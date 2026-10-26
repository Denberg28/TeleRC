package io.github.denberg28.telerc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** Read-only telemetry for v0.4 balancers. Local PID/paused recovery stay on the MCU. */
internal class BalancerTelemetry {
    companion object {
        const val GUIDE = "Select Balancer · Wi-Fi in Setup. Join TeleRC-2WD-Test (default password telerc123), keep the connection without internet, then Connect. Open Controls, enable Phone control, center sticks until ARM · READY, and ARM while supported upright.\n\nFirmware v0.4.0rc2 balances locally. Link loss cancels travel, while an already engaged robot can continue balancing. PAUSED retains ARM and can resume output after upright recovery. DISARM before handling. The APK cannot tune PID or certify motor movement.\n\nCurrent firmware supplies heartbeat, attitude and event reports. Calibration and direction checks use firmware serial diagnostics. Legacy v0.3 pre-test requires its matching TELERC_BALANCE_V1 firmware; those controls stay locked without a valid reply."
    }
    private var boot: Long? = null
    private var armed = false
    private var health = 0
    private var heartbeatAt: Long? = null
    private var attitudeAt: Long? = null
    private var sampleStamp: Long? = null
    private var pitch = 0f
    private var rate = 0f
    private var notice: String? = null
    private var noticeAt = 0L

    @Synchronized fun reset() {
        boot = null; armed = false; health = 0; heartbeatAt = null
        attitudeAt = null; sampleStamp = null; notice = null; noticeAt = 0
    }
    /** True only when a validated heartbeat reports a different firmware boot. */
    @Synchronized fun receive(frame: ByteArray, target: Int, now: Long): Boolean {
        Mavlink.heartbeat(frame)?.let { hb ->
            if (target != 0 && hb.system != target) return false
            val h = if (frame[0] == 0xfe.toByte()) 6 else 10
            val token = ByteBuffer.wrap(frame, h, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            val reboot = boot != null && boot != token
            if (reboot) reset()
            boot = token; armed = hb.armed; health = frame[h + 7].toInt() and 255; heartbeatAt = now
            return reboot
        }
        if (target !in 1..254 || boot == null) return false
        payload(frame, target, 30, 39, 28, 28)?.let { p ->
            val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
            val stamp = b.getInt(0).toLong() and 0xffffffffL
            if (sampleStamp?.let { ((stamp - it) and 0xffffffffL) !in 1..0x7fffffffL } == true) return false
            val angle = b.getFloat(8); val speed = b.getFloat(20)
            if (!angle.isFinite() || !speed.isFinite() || kotlin.math.abs(angle) > 3.2f || kotlin.math.abs(speed) > 100f) return false
            pitch = Math.toDegrees(angle.toDouble()).toFloat(); rate = Math.toDegrees(speed.toDouble()).toFloat()
            sampleStamp = stamp; attitudeAt = now
        }
        payload(frame, target, 253, 83, 51, 54)?.let { p ->
            // No chunk assembly: ignore chunked messages rather than display partial diagnoses.
            if ((p[0].toInt() and 255) > 7 || p.drop(51).any { it != 0.toByte() }) return false
            val raw = p.copyOfRange(1, 51).takeWhile { it != 0.toByte() }
            if (raw.isEmpty() || raw.any { (it.toInt() and 255) !in 32..126 }) return false
            notice = String(raw.toByteArray(), Charsets.US_ASCII); noticeAt = now
        }
        return false
    }
    @Synchronized fun summary(now: Long): String = when {
        heartbeatAt?.let { now - it in 0..1250 } != true -> "Balancer telemetry stale · output state unknown"
        health >= 5 -> "${if (armed) "ARMED" else "DISARMED"} · controller reports a fault"
        armed -> "ARMED · local controller owns balancing"
        else -> "DISARMED · enable and arm from Controls"
    }
    @Synchronized fun detail(now: Long): String {
        val attitude = if (attitudeAt?.let { now - it in 0..500 } == true)
            String.format(Locale.US, "Pitch %.2f° · rate %.2f°/s", pitch, rate)
        else "Attitude unavailable or stale"
        val report = notice?.let { "Last report (${((now - noticeAt).coerceAtLeast(0)) / 1000}s ago): ${it.replace('_', ' ')}" }
            ?: "No event report received this session; current pause/output state is not reported by heartbeat."
        return "$attitude\n$report"
    }
    private fun payload(frame: ByteArray, target: Int, id: Int, extra: Int, base: Int, max: Int): ByteArray? {
        if (frame.size < 8) return null
        val v1 = frame[0] == 0xfe.toByte(); val v2 = frame[0] == 0xfd.toByte()
        val h = if (v1) 6 else if (v2) 10 else return null
        val n = frame[1].toInt() and 255
        if (frame.size != h + n + 2 || (v1 && n != base) || (v2 && (n !in 1..max || frame[2] != 0.toByte()))) return null
        val message = if (v1) frame[5].toInt() and 255 else (frame[7].toInt() and 255) or ((frame[8].toInt() and 255) shl 8) or ((frame[9].toInt() and 255) shl 16)
        if (message != id || (frame[if (v1) 3 else 5].toInt() and 255) != target || frame[if (v1) 4 else 6] != 1.toByte()) return null
        var crc = 65535
        for (value in frame.copyOfRange(1, h + n).map { it.toInt() and 255 } + extra) {
            var t = value xor (crc and 255); t = (t xor (t shl 4)) and 255
            crc = ((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
        }
        if ((frame[h + n].toInt() and 255) != (crc and 255) || (frame[h + n + 1].toInt() and 255) != (crc ushr 8)) return null
        return ByteArray(max).also { frame.copyInto(it, 0, h, h + n) }
    }
}
