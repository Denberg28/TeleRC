package io.github.denberg28.gosim

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.text.InputType
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var socket: DatagramSocket? = null
    private val active = AtomicBoolean(false)
    @Volatile private var lastHeartbeat = 0L
    @Volatile private var target = 0
    @Volatile private var roll = 1500
    @Volatile private var pitch = 1500
    @Volatile private var throttle = 1000
    @Volatile private var yaw = 1500
    private lateinit var status: TextView
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var connect: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        val prefs = getSharedPreferences("link", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 12, 22, 12); setBackgroundColor(Color.rgb(16, 20, 28)) }
        fun label(s: String) = TextView(this).apply { text = s; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL }
        val top = LinearLayout(this)
        top.addView(label("GoSim  •  MAVLink UDP"), LinearLayout.LayoutParams(0, 64, 1f))
        host = EditText(this).apply { setSingleLine(); setText(prefs.getString("host", "192.168.4.1")); hint = "IPv4 address"; setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_TEXT }
        port = EditText(this).apply { setSingleLine(); setText(prefs.getInt("port", 14550).toString()); hint = "Port"; setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_NUMBER }
        top.addView(host, LinearLayout.LayoutParams(250, 64)); top.addView(port, LinearLayout.LayoutParams(125, 64))
        connect = Button(this).apply { text = "CONNECT" }
        top.addView(connect); root.addView(top)
        status = label("DISCONNECTED  •  props off for setup"); root.addView(status)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun axis(name: String, onChange: (Int) -> Unit): LinearLayout {
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            column.addView(label(name))
            val seek = SeekBar(this).apply { max = 1000; progress = if (name == "THROTTLE") 0 else 500 }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange(1000 + progress) }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) { if (name != "THROTTLE") { seek.progress = 500; onChange(1500) } }
            })
            column.addView(seek, LinearLayout.LayoutParams(350, 72))
            return column
        }
        controls.addView(axis("ROLL", { roll = it })); controls.addView(axis("PITCH", { pitch = it })); controls.addView(axis("THROTTLE", { throttle = it })); controls.addView(axis("YAW", { yaw = it }))
        root.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))
        val release = Button(this).apply { text = "RELEASE CONTROL / DISCONNECT"; setOnClickListener { stop() } }
        root.addView(release); setContentView(root)
        connect.setOnClickListener { if (active.get()) stop() else start(prefs) }
    }
    private fun start(prefs: android.content.SharedPreferences) {
        val address = host.text.toString().trim()
        val match = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) && address.split('.').all { it.toInt() in 0..255 }
        val number = port.text.toString().toIntOrNull()
        if (!match || number == null || number !in 1..65535) { status.text = "Enter a valid IPv4 address and UDP port"; return }
        prefs.edit().putString("host", address).putInt("port", number).apply()
        val remote = InetAddress.getByName(address)
        try { socket = DatagramSocket(number).apply { soTimeout = 50 } } catch (e: Exception) { status.text = "Cannot bind UDP port: ${e.message}"; return }
        active.set(true); target = 0; lastHeartbeat = 0
        connect.text = "DISCONNECT"; status.text = "WAITING FOR HEARTBEAT  •  controls disabled"
        val udp = socket!!
        thread(name = "gosim-link") {
            val input = ByteArray(512); var sequence = 0; var lastSend = 0L
            while (active.get()) {
                try {
                    val packet = DatagramPacket(input, input.size)
                    udp.receive(packet)
                    val sender = packet.address.hostAddress
                    if (sender == address) {
                        val system = Mavlink.heartbeatSystem(input.copyOf(packet.length))
                        if (system != null && system in 1..254) { target = system; lastHeartbeat = System.currentTimeMillis() }
                    }
                } catch (_: java.net.SocketTimeoutException) {} catch (_: Exception) { break }
                val now = System.currentTimeMillis()
                if (now - lastSend >= 100 && target != 0 && now - lastHeartbeat < 1500) {
                    try {
                        val bytes = Mavlink.override(sequence++, target, 1, roll, pitch, throttle, yaw)
                        udp.send(DatagramPacket(bytes, bytes.size, remote, number)); lastSend = now
                    } catch (_: Exception) { break }
                }
                runOnUiThread { if (active.get()) status.text = if (target != 0 && now - lastHeartbeat < 1500) "LINK ACTIVE  •  system $target" else "LINK LOST / WAITING  •  controls disabled" }
            }
            runOnUiThread { stop() }
        }
    }
    private fun stop() {
        if (active.getAndSet(false)) {
            val udp = socket
            if (udp != null && target != 0) try {
                val bytes = Mavlink.override(0, target, 1, 1500, 1500, 1000, 1500)
                udp.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host.text.toString()), port.text.toString().toInt()))
            } catch (_: Exception) {}
            udp?.close()
        }
        socket = null; target = 0; roll = 1500; pitch = 1500; throttle = 1000; yaw = 1500
        if (::connect.isInitialized) { connect.text = "CONNECT"; status.text = "DISCONNECTED  •  controls disabled" }
    }
    override fun onPause() { stop(); super.onPause() }
    override fun onDestroy() { stop(); super.onDestroy() }
}
