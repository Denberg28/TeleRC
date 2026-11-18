package io.github.denberg28.telerc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Restricted MAVLink mission upload codec: navigation waypoints only, no arm/mode commands. */
object MissionWire {
    private fun acc(c: Int, b: Int): Int {
        var t = (b xor (c and 255)) and 255
        t = (t xor (t shl 4)) and 255
        return ((c ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
    }
    private fun frame(sequence: Int, id: Int, extra: Int, payload: ByteArray): ByteArray {
        val bytes = byteArrayOf(254.toByte(), payload.size.toByte(), sequence.toByte(), 255.toByte(), 190.toByte(), id.toByte()) + payload
        var crc = 65535
        for (i in 1 until bytes.size) crc = acc(crc, bytes[i].toInt() and 255)
        crc = acc(crc, extra)
        return bytes + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }
    fun count(sequence: Int, system: Int, count: Int): ByteArray {
        require(system in 1..254 && count in 2..101)
        return frame(sequence, 44, 221, byteArrayOf(count.toByte(), 0, system.toByte(), 1))
    }
    fun item(sequence: Int, system: Int, index: Int, point: Waypoint, integer: Boolean): ByteArray {
        require(system in 1..254 && index in 0..100)
        val p = ByteArray(37)
        val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        if (integer) {
            b.putInt(16, (point.latitude * 1e7).roundToInt()); b.putInt(20, (point.longitude * 1e7).roundToInt())
        } else {
            b.putFloat(16, point.latitude.toFloat()); b.putFloat(20, point.longitude.toFloat())
        }
        b.putShort(28, index.toShort()); b.putShort(30, 16) // MAV_CMD_NAV_WAYPOINT
        p[32] = system.toByte(); p[33] = 1
        p[34] = if (integer) 6 else 3 // GLOBAL_RELATIVE_ALT(_INT); Rover ignores altitude.
        p[36] = 1 // autocontinue; never mark an item current or start navigation.
        return frame(sequence, if (integer) 73 else 39, if (integer) 38 else 254, p)
    }
    data class Reply(val index: Int? = null, val integer: Boolean = true, val result: Int? = null)
    fun reply(packet: ByteArray, system: Int): Reply? {
        if (packet.size < 8) return null
        val v2 = packet[0] == 253.toByte()
        if (!v2 && packet[0] != 254.toByte()) return null
        val h = if (v2) 10 else 6
        val length = packet[1].toInt() and 255
        if (packet.size != h + length + 2 || v2 && (packet[2] != 0.toByte() || packet[8] != 0.toByte() || packet[9] != 0.toByte())) return null
        if ((packet[if (v2) 5 else 3].toInt() and 255) != system || packet[if (v2) 6 else 4] != 1.toByte()) return null
        val id = packet[if (v2) 7 else 5].toInt() and 255
        val minimum = if (id == 47) 3 else 4
        val extra = when (id) { 40 -> 230; 51 -> 196; 47 -> 153; else -> return null }
        if (length !in (if (v2) 1 else minimum)..(if (v2) { if (id == 47) 8 else 5 } else minimum)) return null
        var crc = 65535
        for (i in 1 until h + length) crc = acc(crc, packet[i].toInt() and 255)
        crc = acc(crc, extra)
        if ((packet[h + length].toInt() and 255) != (crc and 255) ||
            (packet[h + length + 1].toInt() and 255) != (crc ushr 8)) return null
        val p = packet.copyOfRange(h, h + length).copyOf(8)
        // MAVLink2 may trim zero extension bytes. Only the primary mission type is accepted.
        if (p[minimum] != 0.toByte()) return null
        val targetAt = if (id == 47) 0 else 2
        if (p[targetAt] != 255.toByte() || p[targetAt + 1] != 190.toByte()) return null
        return if (id == 47) Reply(result = p[2].toInt() and 255)
            else Reply(index = (p[0].toInt() and 255) or ((p[1].toInt() and 255) shl 8), integer = id == 51)
    }
}

/** MainActivity owns this state under commandLock; the link worker owns all transfer sends. */
class MissionTransfer {
    // Mission — Retry policy. No queued ARM/AUTO; upload has per-step and overall deadlines.
    companion object { const val RETRY_MS = 1500L; const val MAX_RETRIES = 3; const val TOTAL_MS = 120_000L }
    var active = false; private set
    var status = "Plan saved on phone"; private set
    private var points = emptyList<Waypoint>()
    private var system = 0
    private var next = 0
    private var last = -1
    private var integer = true
    private var due = false
    private var attempts = 0
    private var began = 0L
    private var sentAt = 0L

    fun begin(plan: List<Waypoint>, target: Int, now: Long) {
        require(!active && plan.size in 1..MissionPlan.MAX_WAYPOINTS && target in 1..254)
        points = plan.toList(); system = target; next = 0; last = -1; integer = true
        attempts = 0; began = now; sentAt = now; due = true; active = true
        status = "Uploading · waiting for rover request"
    }
    fun cancel(reason: String) {
        if (active) { active = false; due = false; points = emptyList(); status = "$reason · onboard plan unverified" }
    }
    fun edited() {
        if (active) cancel("Plan changed; upload cancelled") else status = "Plan changed · upload required"
    }
    fun disconnected() {
        cancel("Disconnected; upload cancelled")
        status = "Disconnected · onboard plan unverified"
    }
    fun receive(packet: ByteArray) {
        if (!active) return
        val reply = MissionWire.reply(packet, system) ?: return
        if (reply.result != null) {
            if (reply.result != 0) cancel("Rover rejected upload (${reply.result})")
            else if (!due && next == points.size + 1) {
                active = false; points = emptyList(); status = "Upload acknowledged · rover remains disarmed"
            }
            return
        }
        if (due) return
        val index = reply.index ?: return
        if (index != next && index != last || index !in 0..points.size) return
        if (index == next) { next++; attempts = 0 }
        else if (++attempts > MAX_RETRIES) { cancel("Too many repeated requests"); return }
        last = index; integer = reply.integer; due = true
    }
    fun poll(now: Long, sequence: Int): ByteArray? {
        if (!active) return null
        if (now < began || now - began >= TOTAL_MS) { cancel("Upload timed out"); return null }
        if (!due && now - sentAt >= RETRY_MS) {
            if (++attempts > MAX_RETRIES) { cancel("No rover reply; upload stopped"); return null }
            due = true
        }
        if (!due) return null
        due = false; sentAt = now
        if (last < 0) return MissionWire.count(sequence, system, points.size + 1)
        status = "Uploading ${last.coerceAtMost(points.size)} / ${points.size} · awaiting acknowledgement"
        // ArduPilot reserves sequence 0 for Home; actual route starts at sequence 1.
        // ArduPilot reads this reserved slot from its own Home; no SET_HOME is emitted.
        return MissionWire.item(sequence, system, last, points[if (last == 0) 0 else last - 1], integer)
    }
}
