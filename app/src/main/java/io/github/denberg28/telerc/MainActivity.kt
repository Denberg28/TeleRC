package io.github.denberg28.telerc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** One Wi-Fi session, one bounded IO loop. No queued ARM retries or automatic re-arm. */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val gate = MiniRoverArmGate()
    private var socket: DatagramSocket? = null
    private var generation = 0L
    private var sequence = 0
    private var lastHeartbeat = 0L
    private var connected = false
    private var armed = false
    private var disarmPending = false
    private var pendingArm = false
    private var armRequestedAt = 0L
    private var wantArm = false
    private var wantDisarm = false
    private var steer = 1500
    private var drive = 1500
    private var message = "Join TeleRC-MiniRover, then Connect"
    private var lastRenderedMessage: String? = null
    private lateinit var status: TextView
    private lateinit var connectButton: Button
    private lateinit var armButton: Button
    private lateinit var left: JoystickView
    private lateinit var right: JoystickView
    private val sliders = mutableListOf<SeekBar>()
    private fun now() = SystemClock.elapsedRealtime()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float = 16f) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(24, 41, 58)); gravity = Gravity.CENTER
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; minHeight = dp(44); setOnClickListener { action() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(Color.rgb(242, 247, 251))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("TeleRC · Mini Rover   ${BuildConfig.VERSION_NAME}", 18f), LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(button("Wi-Fi") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        header.addView(button("Setup") { AlertDialog.Builder(this).setTitle("Wi-Fi mini rover").setMessage(MiniRoverWifi.SETUP).setPositiveButton("OK", null).show() })
        root.addView(header)
        val body = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun stickPanel(title: String, vertical: Boolean, changed: (Int) -> Unit): Pair<LinearLayout, JoystickView> {
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            panel.addView(label(title, 15f))
            val stick = JoystickView(this, vertical, changed)
            panel.addView(stick, LinearLayout.LayoutParams(-1, 0, 1f))
            return panel to stick
        }
        val (leftPanel, l) = stickPanel("STEERING  ←  →", false) { synchronized(lock) { steer = it } }
        val (rightPanel, r) = stickPanel("DRIVE  ↑  ↓", true) { synchronized(lock) { drive = it } }
        left = l; right = r
        body.addView(leftPanel, LinearLayout.LayoutParams(0, -1, 1f))
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) }
        status = label(message, 15f).apply { minHeight = dp(48); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        center.addView(status, LinearLayout.LayoutParams(-1, -2))
        connectButton = button("Connect") {
            val active = synchronized(lock) { socket != null }
            if (active) disconnect("Disconnected · DISARMED") else connect()
        }
        armButton = button("ARM") { toggleArm() }
        center.addView(connectButton, LinearLayout.LayoutParams(-1, dp(48)))
        center.addView(armButton, LinearLayout.LayoutParams(-1, dp(48)))
        center.addView(button("STOP / DISARM") { disarm("Stopped · DISARMED") }, LinearLayout.LayoutParams(-1, dp(48)))
        sensitivity(center, "Steering", "steerSensitivity")
        sensitivity(center, "Drive", "driveSensitivity")
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(center) }
        body.addView(scroll, LinearLayout.LayoutParams(dp(230), -1))
        body.addView(rightPanel, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun sensitivity(parent: LinearLayout, title: String, key: String) {
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val value = prefs.getInt(key, 100).coerceIn(25, 100)
        val text = label("$title $value%", 13f); parent.addView(text)
        val slider = SeekBar(this).apply { max = 75; progress = value - 25; contentDescription = "$title sensitivity" }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                text.text = "$title ${progress + 25}%"
                if (fromUser) prefs.edit().putInt(key, progress + 25).apply()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        sliders.add(slider); parent.addView(slider, LinearLayout.LayoutParams(-1, dp(36)))
    }
    private fun send(s: DatagramSocket, bytes: ByteArray) {
        s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(MiniRoverWifi.HOST), MiniRoverWifi.PORT))
    }
    private fun sendDisarm(s: DatagramSocket) {
        send(s, Mavlink.armDisarm(sequence++, 1, 1, false))
        send(s, Mavlink.release(sequence++, 1, 1))
    }
    private fun resetAuthority() {
        armed = false; pendingArm = false; wantArm = false; steer = 1500; drive = 1500; gate.reset()
    }
    private fun disarm(reason: String) {
        synchronized(lock) {
            resetAuthority(); wantDisarm = socket != null; disarmPending = socket != null
            message = if (disarmPending) "Disarming… wait for rover" else reason
            // Stop takes priority over any pending ARM. The worker never retries ARM.
        }
        left.reset(); right.reset(); render()
    }
    private fun disconnect(reason: String) {
        synchronized(lock) {
            generation++
            val old = socket
            socket = null; connected = false; lastHeartbeat = 0; resetAuthority(); wantDisarm = false
            disarmPending = false; message = reason
            if (old != null) {
                try { sendDisarm(old); send(old, "TELERC_DISCONNECT_V1".toByteArray(Charsets.US_ASCII)) } catch (_: Exception) { /* Independent rover watchdog still expires. */ }
                old.close()
            }
        }
        left.reset(); right.reset(); render()
    }
    private fun toggleArm() {
        synchronized(lock) {
            if (armed || pendingArm) { resetAuthority(); wantDisarm = true; disarmPending = true; message = "Disarming… wait for rover" }
            else if (connected && !disarmPending && gate.ready(now()) && steer in 1475..1525 && drive in 1475..1525) {
                wantArm = true; pendingArm = true; armRequestedAt = now(); message = "Arming… wait for rover"
            }
        }
        render()
    }
    @Suppress("DEPRECATION")
    private fun connect() {
        synchronized(lock) {
            if (socket != null) return
            var candidate: DatagramSocket? = null
            try {
                val cm = getSystemService(ConnectivityManager::class.java)
                val wifi = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                    ?: throw IllegalStateException("Join TeleRC-MiniRover in Wi-Fi settings")
                candidate = DatagramSocket(null)
                wifi.bindSocket(candidate)
                candidate.bind(InetSocketAddress(MiniRoverWifi.PORT))
                candidate.soTimeout = 25
                socket = candidate; generation++; connected = false; lastHeartbeat = 0; resetAuthority()
                wantDisarm = true; disarmPending = true; message = "Connecting · waiting for rover"
                val token = generation
                val activeSocket = requireNotNull(candidate)
                Thread({ runSession(activeSocket, token) }, "TeleRC-WiFi").start()
            } catch (e: Exception) {
                candidate?.close(); socket = null; message = e.message ?: "Wi-Fi connection failed"
            }
        }
        render()
    }
    private fun runSession(s: DatagramSocket, token: Long) {
        val started = now()
        var lastDiscovery = 0L
        var lastControl = 0L
        val buffer = ByteArray(256)
        try {
            while (!s.isClosed) {
                synchronized(lock) {
                    if (generation != token || socket !== s) return
                    val time = now()
                    // Revoke before processing recovery telemetry. Reconnect is always explicit.
                    if ((connected && time - lastHeartbeat >= 1200) || (!connected && time - started >= 5000))
                        throw IllegalStateException("Link lost · controls off · reconnect")
                    if (wantDisarm) { wantDisarm = false; sendDisarm(s) }
                    if (time - lastDiscovery >= 750) {
                        send(s, "TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII)); lastDiscovery = time
                    }
                    if (connected && time - lastControl >= 100) {
                        val a = if (armed) steer else 1500
                        val b = if (armed) drive else 1500
                        send(s, Mavlink.override(sequence++, 1, 1, a, b, 1500, 1500))
                        gate.sent(a, b, time); lastControl = time
                    }
                    if (wantArm) {
                        wantArm = false
                        if (connected && pendingArm && time - armRequestedAt < 500 && gate.ready(time) && steer in 1475..1525 && drive in 1475..1525) {
                            send(s, Mavlink.armDisarm(sequence++, 1, 1, true))
                        } else { resetAuthority(); message = "ARM cancelled · center joysticks" }
                    }
                    if (pendingArm && time - armRequestedAt >= 1500) {
                        resetAuthority(); wantDisarm = true; disarmPending = true; message = "ARM unconfirmed · requesting DISARM"
                    }
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try { s.receive(packet) } catch (_: SocketTimeoutException) { continue }
                if (packet.address.hostAddress != MiniRoverWifi.HOST || packet.port != MiniRoverWifi.PORT) continue
                synchronized(lock) {
                    if (generation != token || socket !== s) return
                    val receivedAt = now()
                    if (connected && receivedAt - lastHeartbeat >= 1200) throw IllegalStateException("Link lost · controls off · reconnect")
                    for (frame in Mavlink.frames(packet.data.copyOfRange(0, packet.length))) {
                        Mavlink.heartbeat(frame)?.takeIf { it.system == 1 }?.let { hb ->
                            val first = !connected
                            connected = true; lastHeartbeat = receivedAt
                            if (first) { resetAuthority(); wantDisarm = true; disarmPending = true; message = "Connected · confirming DISARM" }
                            else if (disarmPending && !hb.armed) { disarmPending = false; gate.reset(); message = "Connected · DISARMED" }
                            else if (hb.armed && pendingArm) { armed = true; pendingArm = false; message = "ARMED" }
                            else if (hb.armed && !armed) { wantDisarm = true; disarmPending = true; message = "Confirming DISARM…" } // Unexpected/late ARM cannot restore local authority.
                            else if (!hb.armed && armed) { resetAuthority(); message = "Rover DISARMED · center to re-arm" }
                        }
                        Mavlink.commandAck(frame)?.takeIf { it.system == 1 && it.command == 400 && pendingArm && it.result != 0 }?.let {
                            resetAuthority(); wantDisarm = true; disarmPending = true; message = "ARM ${it.resultText} · requesting DISARM"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            synchronized(lock) {
                if (generation == token && socket === s) {
                    try { sendDisarm(s) } catch (_: Exception) {}
                    socket = null; connected = false; disarmPending = false; resetAuthority(); message = e.message ?: "Disconnected · DISARMED"
                }
            }
        } finally { s.close() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 100) }
    }
    private fun render() {
        if (!::status.isInitialized) return
        synchronized(lock) {
            val active = socket != null
            val fresh = connected && now() - lastHeartbeat < 1200
            val enabled = armed && fresh && !pendingArm
            if (left.isEnabled != enabled) left.isEnabled = enabled
            if (right.isEnabled != enabled) right.isEnabled = enabled
            sliders.forEach { it.isEnabled = !armed && !pendingArm }
            connectButton.text = if (active) "Disconnect" else "Connect"
            armButton.text = if (armed) "DISARM" else if (pendingArm) "Cancel ARM" else "ARM"
            armButton.isEnabled = fresh && !disarmPending && (armed || pendingArm || gate.ready(now()))
            val text = if (fresh && !armed && !pendingArm && gate.ready(now()) && message == "Connected · DISARMED") "Connected · ARM ready" else message
            if (text != lastRenderedMessage) { status.text = text; lastRenderedMessage = text }
        }
    }
    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() {
        ui.removeCallbacks(refresh)
        disconnect("App paused · reconnect to confirm DISARM")
        super.onPause()
    }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); disconnect("Disconnected · DISARMED"); super.onDestroy() }
}
