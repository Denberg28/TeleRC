package io.github.denberg28.telerc

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private enum class Page { SETUP, CONTROLS }
    private var page = Page.SETUP
    private var socket: DatagramSocket? = null
    private val connected = AtomicBoolean(false)
    private val controlEnabled = AtomicBoolean(false)
    @Volatile private var heartbeatAt = 0L
    @Volatile private var target = 0
    @Volatile private var steering = 1500
    @Volatile private var drive = 1500
    private var status: TextView? = null
    private var connect: Button? = null
    private var enable: Button? = null
    private var steeringBar: SeekBar? = null
    private var driveBar: SeekBar? = null
    private var host: EditText? = null
    private var port: EditText? = null
    private var endpoint: InetAddress? = null
    private var endpointPort = 0
    private val ink = Color.rgb(35, 38, 53)
    private val muted = Color.rgb(103, 105, 124)
    private val accent = Color.rgb(112, 88, 166)
    private val pale = Color.rgb(247, 245, 251)

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(value: String, filled: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 15f
        setTextColor(if (filled) Color.WHITE else accent)
        background = shape(if (filled) accent else Color.rgb(237, 231, 249), 16)
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape(Color.WHITE); elevation = dp(2).toFloat()
    }
    private fun LinearLayout.addCard(view: View) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = if (savedInstanceState?.getString("page") == "CONTROLS") Page.CONTROLS else Page.SETUP
        requestedOrientation = if (page == Page.SETUP) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name); super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); render()
    }
    private fun switchTo(next: Page) {
        if (page == next) return
        disableControl()
        page = next
        val orientation = if (next == Page.SETUP) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        requestedOrientation = orientation
        render()
    }
    private fun shell(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(pale)
        setPadding(dp(18), dp(14), dp(18), dp(10))
    }
    private fun nav(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL
        val setup = button("⌂  Setup & Config", page == Page.SETUP) { switchTo(Page.SETUP) }
        val controls = button("▣  Controls", page == Page.CONTROLS) { switchTo(Page.CONTROLS) }
        addView(setup, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) })
        addView(controls, LinearLayout.LayoutParams(0, dp(52), 1f))
    }
    private fun render() {
        window.decorView.systemUiVisibility = 0
        if (page == Page.SETUP) renderSetup() else renderControls()
        refreshUi()
    }
    private fun renderSetup() {
        enable = null; steeringBar = null; driveBar = null
        val root = shell()
        root.addView(text("TeleRC", 32f, ink, true))
        root.addView(text("Your craft, in your hands.", 14f, muted))
        val scroll = ScrollView(this).apply { fillViewport = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(20), 0, dp(8)) }
        val craft = card().apply {
            addView(text("CRAFT PROFILE", 12f, accent, true))
            addView(text("Rover", 24f, ink, true))
            addView(text("ArduRover  •  steering CH1  •  drive CH3", 13f, muted))
            addView(text("Multirotor, fixed wing, watercraft and rocket profiles are planned.", 12f, muted))
        }
        body.addCard(craft)
        val connection = card().apply {
            addView(text("CONNECTION", 12f, accent, true))
            addView(text("MAVLink over local Wi-Fi", 19f, ink, true))
            addView(text("Enter your ESP32 or Raspberry Pi bridge address and UDP port.", 13f, muted))
            host = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "Bridge IPv4"; setTextColor(ink)
                inputType = InputType.TYPE_CLASS_TEXT
                setText(if (connected.get()) endpoint?.hostAddress else getSharedPreferences("link", MODE_PRIVATE).getString("host", "192.168.4.1"))
                isEnabled = !connected.get()
            }
            port = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "UDP port"; setTextColor(ink)
                inputType = InputType.TYPE_CLASS_NUMBER
                setText((if (connected.get()) endpointPort else getSharedPreferences("link", MODE_PRIVATE).getInt("port", 14550)).toString())
                isEnabled = !connected.get()
            }
            addView(host); addView(port)
            connect = button("Connect") { if (connected.get()) stop() else start() }
            addView(connect, LinearLayout.LayoutParams(-1, dp(52)))
        }
        body.addCard(connection)
        val info = card().apply {
            addView(text("LINK STATUS", 12f, accent, true))
            status = text("DISCONNECTED", 17f, ink, true); addView(status)
            addView(text("Control requires a valid heartbeat and a separate Enable action.", 13f, muted))
        }
        body.addCard(info)
        body.addView(text("Private bench test  •  Raise wheels before enabling control.", 12f, muted))
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav()); setContentView(root)
    }
    private fun renderControls() {
        host = null; port = null; connect = null
        val root = shell()
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("TeleRC  /  ROVER", 24f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        status = text("DISCONNECTED", 15f, accent, true)
        head.addView(status); root.addView(head)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun control(title: String, hint: String, changed: (Int) -> Unit): SeekBar {
            val panel = card().apply {
                addView(text(title, 20f, ink, true))
                addView(text(hint, 13f, muted))
                val bar = SeekBar(this@MainActivity).apply {
                    max = 1000; progress = 500; isEnabled = false
                    progressTintList = android.content.res.ColorStateList.valueOf(accent)
                    thumbTintList = android.content.res.ColorStateList.valueOf(accent)
                }
                bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                        if (fromUser && controlEnabled.get()) changed(1000 + value)
                    }
                    override fun onStartTrackingTouch(seek: SeekBar?) {}
                    override fun onStopTrackingTouch(seek: SeekBar?) { bar.progress = 500; changed(1500) }
                })
                addView(bar, LinearLayout.LayoutParams(-1, dp(72)))
                addView(text("◀                  NEUTRAL                  ▶", 12f, muted))
            }
            row.addView(panel, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10) })
            return panel.getChildAt(2) as SeekBar
        }
        steeringBar = control("STEERING", "Left  /  right · CH1") { steering = it }
        driveBar = control("DRIVE", "Reverse  /  forward · CH3") { drive = it }
        root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        enable = button("Enable control") {
            if (controlEnabled.get()) disableControl() else if (linkFresh()) {
                controlEnabled.set(true)
                steeringBar?.isEnabled = true; driveBar?.isEnabled = true
                refreshUi()
            }
        }
        root.addView(enable, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(text("Sliders center on release. Stop sends neutral then releases override.", 12f, muted))
        root.addView(nav()); setContentView(root)
    }
    private fun linkFresh() = target != 0 && System.currentTimeMillis() - heartbeatAt < 1500
    private fun refreshUi() {
        status?.text = when {
            !connected.get() -> "DISCONNECTED"
            !linkFresh() -> "WAITING FOR HEARTBEAT"
            controlEnabled.get() -> "SYSTEM $target  •  CONTROL ON"
            else -> "SYSTEM $target  •  LINK ACTIVE"
        }
        connect?.text = if (connected.get()) "Disconnect" else "Connect"
        enable?.isEnabled = linkFresh()
        enable?.text = if (controlEnabled.get()) "STOP / Disable control" else "Enable control"
    }
    private fun disableControl() {
        val wasEnabled = controlEnabled.getAndSet(false)
        steering = 1500; drive = 1500
        if (wasEnabled && target != 0) try {
            val udp = socket; val remote = endpoint
            if (udp != null && remote != null) {
                val neutral = Mavlink.override(0, target, 1, 1500, 1500, 1500, 1500)
                udp.send(DatagramPacket(neutral, neutral.size, remote, endpointPort))
                val release = Mavlink.release(1, target, 1)
                udp.send(DatagramPacket(release, release.size, remote, endpointPort))
            }
        } catch (_: Exception) {}
        steeringBar?.isEnabled = false; driveBar?.isEnabled = false
        steeringBar?.progress = 500; driveBar?.progress = 500
        refreshUi()
    }
    private fun start() {
        val address = host?.text?.toString()?.trim().orEmpty()
        val valid = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) &&
            address.split('.').all { it.toInt() in 0..255 }
        val number = port?.text?.toString()?.toIntOrNull()
        if (!valid || number == null || number !in 1..65535) {
            status?.text = "INVALID ADDRESS OR PORT"; return
        }
        val remote = InetAddress.getByName(address)
        val udp = try { DatagramSocket(number).apply { soTimeout = 50 } }
        catch (e: Exception) { status?.text = "UDP PORT UNAVAILABLE"; return }
        getSharedPreferences("link", MODE_PRIVATE).edit().putString("host", address).putInt("port", number).apply()
        socket = udp; endpoint = remote; endpointPort = number
        target = 0; heartbeatAt = 0; disableControl(); connected.set(true)
        host?.isEnabled = false; port?.isEnabled = false; refreshUi()
        thread(name = "telerc-link") {
            val input = ByteArray(512); var sequence = 0; var lastSend = 0L
            while (connected.get() && socket === udp) {
                try {
                    val packet = DatagramPacket(input, input.size)
                    udp.receive(packet)
                    if (packet.address == remote && packet.port == number) {
                        val system = Mavlink.heartbeatSystem(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
                        if (system != null && (target == 0 || target == system)) {
                            target = system; heartbeatAt = System.currentTimeMillis()
                        }
                    }
                } catch (_: SocketTimeoutException) {} catch (_: Exception) { break }
                val now = System.currentTimeMillis()
                val fresh = target != 0 && now - heartbeatAt < 1500
                if (now - lastSend >= 100 && fresh && controlEnabled.get()) {
                    try {
                        val rc = RoverControls.channels(steering, drive)
                        val bytes = Mavlink.override(sequence++, target, 1, rc.one, rc.two, rc.three, rc.four)
                        udp.send(DatagramPacket(bytes, bytes.size, remote, number)); lastSend = now
                    } catch (_: Exception) { break }
                }
                runOnUiThread {
                    if (socket === udp && connected.get()) {
                        if (!fresh && controlEnabled.get()) disableControl()
                        refreshUi()
                    }
                }
            }
            runOnUiThread { if (socket === udp) stop() }
        }
    }
    private fun stop() {
        disableControl(); connected.set(false)
        val udp = socket; udp?.close(); socket = null; endpoint = null; target = 0; heartbeatAt = 0
        host?.isEnabled = true; port?.isEnabled = true; refreshUi()
    }
    override fun onPause() { stop(); super.onPause() }
    override fun onDestroy() { stop(); super.onDestroy() }
}
