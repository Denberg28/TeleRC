package io.github.denberg28.telerc

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var socket: DatagramSocket? = null
    private val connected = AtomicBoolean(false)
    private val controlEnabled = AtomicBoolean(false)
    @Volatile private var heartbeatAt = 0L
    @Volatile private var target = 0
    @Volatile private var steering = 1500
    @Volatile private var drive = 1500
    private lateinit var status: TextView
    private lateinit var connect: Button
    private lateinit var enable: Button
    private lateinit var steeringBar: SeekBar
    private lateinit var driveBar: SeekBar
    private lateinit var host: EditText
    private lateinit var port: EditText
    private var endpoint: InetAddress? = null
    private var endpointPort = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        val prefs = getSharedPreferences("link", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.rgb(16, 20, 28))
        }
        fun label(value: String) = TextView(this).apply {
            text = value; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("TeleRC  •  ROVER"), LinearLayout.LayoutParams(0, 64, 1f))
        host = EditText(this).apply {
            setSingleLine(); setText(prefs.getString("host", "192.168.4.1"))
            hint = "Bridge IPv4"; setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_TEXT
        }
        port = EditText(this).apply {
            setSingleLine(); setText(prefs.getInt("port", 14550).toString())
            hint = "UDP port"; setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_NUMBER
        }
        top.addView(host, LinearLayout.LayoutParams(250, 64))
        top.addView(port, LinearLayout.LayoutParams(125, 64))
        connect = Button(this).apply { text = "CONNECT"; setOnClickListener { if (connected.get()) stop() else start() } }
        top.addView(connect); root.addView(top)
        status = label("DISCONNECTED  •  bench test only"); root.addView(status)
        root.addView(label("ArduRover default: CH1 steering · CH3 bidirectional throttle"))
        root.addView(label("Craft profiles: Rover active · Air / Water / Rocket planned"))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun control(title: String, changed: (Int) -> Unit): SeekBar {
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            column.addView(label(title))
            val bar = SeekBar(this).apply { max = 1000; progress = 500; isEnabled = false }
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && controlEnabled.get()) changed(1000 + value)
                }
                override fun onStartTrackingTouch(seek: SeekBar?) {}
                override fun onStopTrackingTouch(seek: SeekBar?) { bar.progress = 500; changed(1500) }
            })
            column.addView(bar, LinearLayout.LayoutParams(-1, 80))
            controls.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            return bar
        }
        steeringBar = control("STEER  ◀    CENTER    ▶") { steering = it }
        driveBar = control("REVERSE  ◀    STOP    ▶  FORWARD") { drive = it }
        root.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))
        enable = Button(this).apply {
            text = "ENABLE CONTROL"; isEnabled = false
            setOnClickListener {
                if (controlEnabled.get()) disableControl() else if (linkFresh()) {
                    controlEnabled.set(true); isEnabled = true
                    steeringBar.isEnabled = true; driveBar.isEnabled = true
                    text = "STOP / DISABLE CONTROL"
                }
            }
        }
        root.addView(enable)
        root.addView(Button(this).apply { text = "DISCONNECT"; setOnClickListener { stop() } })
        setContentView(root)
    }

    private fun linkFresh() = target != 0 && System.currentTimeMillis() - heartbeatAt < 1500

    private fun disableControl() {
        val wasEnabled = controlEnabled.getAndSet(false)
        steering = 1500; drive = 1500
        if (wasEnabled && target != 0) try {
            val udp = socket
            val remote = endpoint
            if (udp != null && remote != null) {
                val neutral = Mavlink.override(0, target, 1, 1500, 1500, 1500, 1500)
                udp.send(DatagramPacket(neutral, neutral.size, remote, endpointPort))
                val release = Mavlink.release(1, target, 1)
                udp.send(DatagramPacket(release, release.size, remote, endpointPort))
            }
        } catch (_: Exception) {}
        if (::steeringBar.isInitialized) {
            steeringBar.isEnabled = false; driveBar.isEnabled = false
            steeringBar.progress = 500; driveBar.progress = 500
            enable.text = "ENABLE CONTROL"
        }
    }

    private fun start() {
        val address = host.text.toString().trim()
        val valid = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) &&
            address.split('.').all { it.toInt() in 0..255 }
        val number = port.text.toString().toIntOrNull()
        if (!valid || number == null || number !in 1..65535) {
            status.text = "Enter a valid IPv4 address and UDP port"; return
        }
        val remote = InetAddress.getByName(address)
        val udp = try { DatagramSocket(number).apply { soTimeout = 50 } }
        catch (e: Exception) { status.text = "Cannot bind UDP port: ${e.message}"; return }
        getSharedPreferences("link", MODE_PRIVATE).edit().putString("host", address).putInt("port", number).apply()
        socket = udp; endpoint = remote; endpointPort = number
        target = 0; heartbeatAt = 0; disableControl(); connected.set(true)
        connect.text = "DISCONNECT"; status.text = "WAITING FOR HEARTBEAT  •  controls disabled"
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
                        if (!fresh) disableControl()
                        enable.isEnabled = fresh
                        status.text = if (fresh) "LINK ACTIVE  •  system $target  •  ${if (controlEnabled.get()) "CONTROL ON" else "CONTROL OFF"}"
                        else "LINK LOST / WAITING  •  controls disabled"
                    }
                }
            }
            runOnUiThread { if (socket === udp) stop() }
        }
    }

    private fun stop() {
        disableControl()
        connected.set(false)
        val udp = socket
        udp?.close(); socket = null; endpoint = null; target = 0; heartbeatAt = 0
        if (::connect.isInitialized) {
            connect.text = "CONNECT"; enable.isEnabled = false
            status.text = "DISCONNECTED  •  controls disabled"
        }
    }
    override fun onPause() { stop(); super.onPause() }
    override fun onDestroy() { stop(); super.onDestroy() }
}
