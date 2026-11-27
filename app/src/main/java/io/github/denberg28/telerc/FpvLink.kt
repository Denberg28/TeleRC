package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

// FPV — Recognizes RTL8812BU USB 3 adapters used with Auburn/fpvDROID for 5.8 GHz analog FPV (e.g., Ratel 2 + Eachine VTX).
object Rtl8812buDetector {
    private const val VID_REALTEK = 0x0BDA
    private const val PID_RTL8812BU = 0xB812
    private const val VID_TPLINK = 0x2357
    private const val PID_T4U_V3 = 0x0115
    private const val PID_T3U = 0x012D
    private const val PID_T3U_PLUS = 0x0138

    fun isRtl8812bu(vendorId: Int, productId: Int): Boolean = adapterName(vendorId, productId) != null

    fun adapterName(vendorId: Int, productId: Int): String? = when {
        vendorId == VID_REALTEK && productId == PID_RTL8812BU -> "Realtek RTL8812BU"
        vendorId == VID_TPLINK && productId == PID_T4U_V3 -> "TP-Link Archer T4U v3 (RTL8812BU)"
        vendorId == VID_TPLINK && productId == PID_T3U -> "TP-Link Archer T3U (RTL8812BU)"
        vendorId == VID_TPLINK && productId == PID_T3U_PLUS -> "TP-Link Archer T3U Plus (RTL8812BU)"
        else -> null
    }

    fun findConnectedAdapter(context: Context): String? = try {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        usb.deviceList.values.firstNotNullOfOrNull { d -> adapterName(d.vendorId, d.productId) }
    } catch (_: Exception) {
        null
    }
}

// FPV — Source selection and view preferences persisted across Mission sessions.
enum class FpvSourceMode(val label: String) {
    AUTO("Auto (USB → Wi-Fi)"),
    WIFI_MJPEG("Wi-Fi MJPEG"),
    USB_EXTERNAL("USB External Cam"),
    PHONE_REAR("Phone Cam (Test)");

    companion object {
        fun fromName(value: String?): FpvSourceMode =
            values().firstOrNull { it.name == value } ?: AUTO
    }
}

enum class FpvScaleMode(val label: String) {
    FIT("Fit"),
    FILL("Fill");

    companion object {
        fun fromName(value: String?): FpvScaleMode =
            values().firstOrNull { it.name == value } ?: FILL
    }
}

data class FpvConfig(
    val mode: FpvSourceMode = FpvSourceMode.AUTO,
    val url: String = DEFAULT_WIFI_URL,
    val scale: FpvScaleMode = FpvScaleMode.FILL,
    val crosshair: Boolean = true
) {
    companion object {
        const val DEFAULT_WIFI_URL = "http://192.168.4.2:81/stream"
        const val AP_HOST_WIFI_URL = "http://192.168.4.1:81/stream"
    }
}

data class FpvEndpoint(val host: String, val port: Int, val path: String) {
    val isLoopback: Boolean get() = host == "127.0.0.1"
    fun toUrlString(): String = "http://$host:$port$path"
}

// FPV — Strict private-LAN HTTP URL validator (blocks public internet cleartext and header injection).
object FpvUrlValidator {
    fun parse(raw: String): FpvEndpoint? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 256) return null
        if (trimmed.any { it == '\r' || it == '\n' || it == '\u0000' || it.isWhitespace() || it == '@' }) return null
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://")) return null
        val rest = trimmed.substring(7)
        val slashIdx = rest.indexOf('/')
        val authority = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
        val path = if (slashIdx >= 0) rest.substring(slashIdx) else "/"
        if (authority.isEmpty() || !path.startsWith("/")) return null
        val colonIdx = authority.lastIndexOf(':')
        val host: String
        val port: Int
        if (colonIdx >= 0) {
            host = authority.substring(0, colonIdx)
            port = authority.substring(colonIdx + 1).toIntOrNull() ?: return null
        } else {
            host = authority
            port = 80
        }
        if (port !in 1..65535) return null
        if (!isPrivateLanHost(host)) return null
        return FpvEndpoint(host, port, path)
    }

    internal fun isPrivateLanHost(host: String): Boolean {
        val h = host.lowercase()
        if (h.endsWith(".local") && h.length in 7..64 && h.all { it.isLetterOrDigit() || it == '-' || it == '.' }) {
            return !h.startsWith(".") && !h.contains("..")
        }
        val parts = h.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) ||
            (a == 127 && b == 0 && octets[2] == 0 && octets[3] == 1)
    }
}

// FPV — Incremental bounded JPEG frame scanner for multipart or raw MJPEG streams.
internal class MjpegFrameExtractor(
    private val maxFrameBytes: Int = MAX_FRAME_BYTES
) {
    companion object {
        const val MIN_FRAME_BYTES = 64
        const val MAX_FRAME_BYTES = 512 * 1024
    }

    private val buffer = ByteArrayOutputStream(32 * 1024)
    private var inFrame = false
    private var prevByte = -1

    fun reset() {
        buffer.reset()
        inFrame = false
        prevByte = -1
    }

    /** Push a chunk of stream bytes; invokes [onFrame] for each complete bounded JPEG frame. */
    fun push(chunk: ByteArray, length: Int, onFrame: (ByteArray) -> Unit) {
        val count = length.coerceIn(0, chunk.size)
        for (i in 0 until count) {
            val cur = chunk[i].toInt() and 0xFF
            if (!inFrame) {
                if (prevByte == 0xFF && cur == 0xD8) {
                    buffer.reset()
                    buffer.write(0xFF)
                    buffer.write(0xD8)
                    inFrame = true
                    prevByte = -1
                    continue
                }
                prevByte = cur
            } else {
                if (buffer.size() >= maxFrameBytes) {
                    // Drop oversized corrupted frame cleanly and resync on next SOI.
                    reset()
                    prevByte = cur
                    continue
                }
                buffer.write(cur)
                if (prevByte == 0xFF && cur == 0xD9) {
                    if (buffer.size() >= MIN_FRAME_BYTES) {
                        onFrame(buffer.toByteArray())
                    }
                    reset()
                    continue
                }
                prevByte = cur
            }
        }
    }
}

// FPV — Self-healing link supervisor inspired by Auburn/fpvDROID UsbLink.
// Detects silent frame stalls, holds the last frame dimmed briefly during reopen,
// escalates backoff on repeated failures, and resets backoff after sustained healthy streaming.
internal class FpvLinkSupervisor {
    enum class State {
        IDLE,
        CONNECTING,
        STREAMING,
        STALE_HOLD,
        RECONNECTING,
        PERMISSION_NEEDED,
        NO_SOURCE
    }

    enum class Action { NONE, REOPEN, CLEAR_HOLD }

    companion object {
        const val STALL_MS = 4000L
        const val HOLD_FRAME_MS = 4000L
        const val HEALTHY_MS = 10_000L
        const val FIRST_RETRY_MS = 1500L
        const val RETRY_MS = 3000L
        const val SLOW_RETRY_MS = 6000L
        const val PERSISTENT_FAILURES = 4
    }

    var state = State.IDLE; private set
    var message = "FPV idle"; private set
    var generation = 0; private set
    var holdFrameVisible = false; private set
    var consecutiveFailures = 0; private set

    private var activeSourceLabel = ""
    private var streamingSinceMs = 0L
    private var lastFrameMs = 0L
    private var holdUntilMs = 0L
    private var retryAtMs = 0L

    fun startConnecting(sourceLabel: String, nowMs: Long): Int {
        generation++
        activeSourceLabel = sourceLabel
        state = State.CONNECTING
        message = "Connecting $sourceLabel…"
        lastFrameMs = nowMs
        streamingSinceMs = 0L
        retryAtMs = 0L
        return generation
    }

    fun markNoSource(reason: String) {
        generation++
        state = State.NO_SOURCE
        message = reason
        holdFrameVisible = false
        retryAtMs = 0L
    }

    fun markPermissionNeeded(reason: String) {
        generation++
        state = State.PERMISSION_NEEDED
        message = reason
        holdFrameVisible = false
        retryAtMs = 0L
    }

    fun stop() {
        generation++
        state = State.IDLE
        message = "FPV paused"
        holdFrameVisible = false
        retryAtMs = 0L
        streamingSinceMs = 0L
    }

    fun onFrame(gen: Int, nowMs: Long): Boolean {
        if (gen != generation) return false
        if (state != State.STREAMING) {
            state = State.STREAMING
            streamingSinceMs = nowMs
            message = "$activeSourceLabel · LIVE"
        } else if (nowMs - streamingSinceMs >= HEALTHY_MS) {
            consecutiveFailures = 0
        }
        lastFrameMs = nowMs
        holdFrameVisible = true
        holdUntilMs = 0L
        retryAtMs = 0L
        return true
    }

    fun onError(gen: Int, reason: String, nowMs: Long): Boolean {
        if (gen != generation) return false
        consecutiveFailures++
        val delay = retryDelayMs(consecutiveFailures)
        retryAtMs = nowMs + delay
        val hadFrame = holdFrameVisible
        if (hadFrame) {
            state = State.STALE_HOLD
            holdUntilMs = nowMs + HOLD_FRAME_MS
        } else {
            state = State.RECONNECTING
            holdUntilMs = 0L
        }
        message = "$reason · retrying in ${(delay + 500L) / 1000L}s"
        return true
    }

    fun tick(nowMs: Long): Action {
        if (state == State.STREAMING && nowMs - lastFrameMs >= STALL_MS) {
            // Silent wedge/stall detected (fpvDROID pattern): reopen immediately while holding last frame briefly.
            consecutiveFailures++
            state = State.STALE_HOLD
            holdUntilMs = nowMs + HOLD_FRAME_MS
            retryAtMs = nowMs
            message = "Stream stalled · reconnecting…"
            return Action.REOPEN
        }
        var cleared = false
        if (holdFrameVisible && holdUntilMs > 0L && nowMs >= holdUntilMs) {
            holdFrameVisible = false
            holdUntilMs = 0L
            if (state == State.STALE_HOLD) state = State.RECONNECTING
            cleared = true
        }
        if ((state == State.STALE_HOLD || state == State.RECONNECTING) && retryAtMs > 0L && nowMs >= retryAtMs) {
            retryAtMs = 0L
            return Action.REOPEN
        }
        return if (cleared) Action.CLEAR_HOLD else Action.NONE
    }

    private fun retryDelayMs(failures: Int): Long = when {
        failures <= 1 -> FIRST_RETRY_MS
        failures < PERSISTENT_FAILURES -> RETRY_MS
        else -> SLOW_RETRY_MS
    }
}

// FPV — Low-priority Wi-Fi LAN MJPEG worker bound to Android's Wi-Fi network so cellular data never hijacks LAN video.
internal class FpvMjpegClient(
    private val context: Context,
    private val endpoint: FpvEndpoint,
    private val generation: Int,
    private val onBitmap: (Int, Bitmap) -> Unit,
    private val onError: (Int, String) -> Unit
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val READ_TIMEOUT_MS = 3000
    }

    private val closed = AtomicBoolean(false)
    private val activeSocket = AtomicReference<Socket?>(null)
    private val decodeBusy = AtomicBoolean(false)

    fun start() {
        thread(name = "telerc-fpv-mjpeg", isDaemon = true, priority = Thread.MIN_PRIORITY + 1) {
            runLoop()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            activeSocket.getAndSet(null)?.close()
        } catch (_: Exception) {}
    }

    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun runLoop() {
        var socket: Socket? = null
        try {
            val net = if (endpoint.isLoopback) null else wifiNetwork()
            if (!endpoint.isLoopback && net == null) {
                if (!closed.get()) onError(generation, "Join rover Wi-Fi for FPV")
                return
            }
            socket = (net?.socketFactory?.createSocket() ?: Socket()).apply {
                tcpNoDelay = true
                soTimeout = READ_TIMEOUT_MS
            }
            activeSocket.set(socket)
            if (closed.get()) return
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)

            val request = "GET ${endpoint.path} HTTP/1.1\r\n" +
                "Host: ${endpoint.host}:${endpoint.port}\r\n" +
                "User-Agent: TeleRC-FPV\r\n" +
                "Accept: multipart/x-mixed-replace, image/jpeg\r\n" +
                "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }

            val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val statusLine = readAsciiLine(input) ?: throw IOException("No HTTP response")
            if (!statusLine.startsWith("HTTP/1.") || !statusLine.contains(" 200")) {
                throw IOException("Camera HTTP error")
            }
            // Skip response headers (bounded to 64 lines).
            var lines = 0
            while (lines < 64) {
                val header = readAsciiLine(input) ?: throw IOException("Stream closed in headers")
                if (header.isEmpty()) break
                lines++
            }

            val extractor = MjpegFrameExtractor()
            val chunk = ByteArray(8192)
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            while (!closed.get()) {
                val n = input.read(chunk)
                if (n < 0) break
                extractor.push(chunk, n) { jpegBytes ->
                    if (closed.get()) return@push
                    // Drop frames if UI/decoder is still busy so latency never accumulates.
                    if (!decodeBusy.compareAndSet(false, true)) return@push
                    try {
                        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
                        if (bmp != null && !closed.get()) {
                            onBitmap(generation, bmp)
                        } else {
                            bmp?.recycle()
                        }
                    } finally {
                        decodeBusy.set(false)
                    }
                }
            }
            if (!closed.get()) onError(generation, "FPV stream ended")
        } catch (_: Exception) {
            if (!closed.get()) onError(generation, "Camera unreachable (${endpoint.host})")
        } finally {
            try { activeSocket.getAndSet(null)?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = StringBuilder(64)
        var count = 0
        while (count < 512) {
            val b = input.read()
            if (b < 0) return if (out.isEmpty()) null else out.toString()
            if (b == '\n'.code) {
                if (out.isNotEmpty() && out[out.length - 1] == '\r') out.setLength(out.length - 1)
                return out.toString()
            }
            out.append(b.toChar())
            count++
        }
        return out.toString()
    }
}
