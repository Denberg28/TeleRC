package io.github.denberg28.telerc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Separate navigation codec: the existing manual-control encoder/parser is unchanged. */
object WaypointProtocol {
    private val extras = mapOf(0 to 50, 1 to 124, 20 to 214, 22 to 220, 24 to 24,
        33 to 104, 75 to 158, 76 to 152, 77 to 143, 87 to 150, 193 to 71, 242 to 104)
    data class Frame(val id: Int, val system: Int, val component: Int, val data: ByteArray) {
        private val buffer get() = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        fun u8(i: Int) = data[i].toInt() and 255
        fun u16(i: Int) = buffer.getShort(i).toInt() and 65535
        fun i32(i: Int) = buffer.getInt(i)
        fun i64(i: Int) = buffer.getLong(i)
        fun f32(i: Int) = buffer.getFloat(i)
        fun text(i: Int, count: Int) = data.copyOfRange(i, i + count).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.US_ASCII)
    }
    private fun crcByte(crc: Int, value: Int): Int {
        var t = (value xor (crc and 255)) and 255
        t = (t xor (t shl 4)) and 255
        return ((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
    }
    fun decode(packet: ByteArray): Frame? {
        if (packet.size < 8) return null
        val magic = packet[0].toInt() and 255
        val offset = when (magic) { 254 -> 6; 253 -> 10; else -> return null }
        if (packet.size < offset + 2) return null
        // Unknown incompatibility flags and signatures cannot be authenticated here.
        if (magic == 253 && packet[2] != 0.toByte()) return null
        val n = packet[1].toInt() and 255
        if (packet.size != offset + n + 2) return null
        val id = if (magic == 254) packet[5].toInt() and 255 else
            (packet[7].toInt() and 255) or ((packet[8].toInt() and 255) shl 8) or ((packet[9].toInt() and 255) shl 16)
        val extra = extras[id] ?: return null
        var crc = 65535
        for (i in 1 until offset + n) crc = crcByte(crc, packet[i].toInt() and 255)
        crc = crcByte(crc, extra)
        if ((packet[offset+n].toInt() and 255) != (crc and 255) ||
            (packet[offset+n+1].toInt() and 255) != (crc ushr 8)) return null
        val system = packet[if (magic == 254) 3 else 5].toInt() and 255
        val component = packet[if (magic == 254) 4 else 6].toInt() and 255
        if (system !in 1..254 || component != 1) return null
        // MAVLink2 truncates trailing zero bytes. Restore only each known minimum payload.
        val min = mapOf(0 to 9, 1 to 31, 22 to 25, 24 to 30, 33 to 28,
            77 to 3, 87 to 51, 193 to 22, 242 to 52)[id] ?: return null
        if (magic == 254 && n < min || n == 0) return null
        return Frame(id, system, component, packet.copyOfRange(offset, offset+n).copyOf(maxOf(n, min)))
    }
    internal fun frame(id: Int, sequence: Int, payload: ByteArray): ByteArray {
        val header = byteArrayOf(254.toByte(), payload.size.toByte(), sequence.toByte(), 255.toByte(), 190.toByte(), id.toByte())
        val bytes = header + payload
        var crc = 65535
        for (i in 1 until bytes.size) crc = crcByte(crc, bytes[i].toInt() and 255)
        crc = crcByte(crc, extras.getValue(id))
        return bytes + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }
    private fun buffer(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    fun heartbeat(sequence: Int) = frame(0, sequence, buffer(9).apply {
        putInt(0); put(6); put(8); put(0); put(4); put(3)
    }.array())
    fun parameter(sequence: Int, system: Int, name: String): ByteArray {
        require(name.length in 1..16 && system in 1..254)
        return frame(20, sequence, buffer(20).apply {
            putShort((-1).toShort()); put(system.toByte()); put(1)
            put(name.toByteArray(Charsets.US_ASCII))
        }.array())
    }
    fun command(sequence: Int, system: Int, command: Int, vararg params: Float): ByteArray {
        require(system in 1..254 && params.size <= 7 && params.all { it.isFinite() })
        return frame(76, sequence, buffer(33).apply {
            for (i in 0..6) putFloat(params.getOrElse(i) { 0f })
            putShort(command.toShort()); put(system.toByte()); put(1); put(0)
        }.array())
    }
    fun hold(sequence: Int, system: Int) = command(sequence, system, 176, 1f, 4f)
    /** Atomic Guided + destination + 0.5 m/s; no arm, force flag or old-target resume. */
    fun destination(sequence: Int, system: Int, point: Waypoint): ByteArray {
        require(point.valid && system in 1..254)
        return frame(75, sequence, buffer(35).apply {
            putFloat(.5f); putFloat(1f); putFloat(0f); putFloat(0f)
            putInt((point.latitude*1e7).roundToInt()); putInt((point.longitude*1e7).roundToInt()); putFloat(0f)
            putShort(192.toShort()); put(system.toByte()); put(1); put(6); put(0); put(0)
        }.array())
    }
}
