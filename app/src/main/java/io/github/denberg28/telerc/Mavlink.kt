package io.github.denberg28.telerc

object Mavlink {
    data class GlobalPosition(val system: Int, val latitude: Double, val longitude: Double)
    fun frames(datagram: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var cursor = 0
        while (cursor < datagram.size) {
            val magic = datagram[cursor].toInt() and 255
            val header = if (magic == 0xFE) 6 else if (magic == 0xFD) 10 else 0
            if (header == 0 || cursor + header > datagram.size) { cursor++; continue }
            val signed = magic == 0xFD && (datagram[cursor + 2].toInt() and 1) != 0
            val size = header + (datagram[cursor + 1].toInt() and 255) + 2 + if (signed) 13 else 0
            if (cursor + size > datagram.size) { cursor++; continue }
            result.add(datagram.copyOfRange(cursor, cursor + size))
            cursor += size
        }
        return result
    }
    private const val EXTRA = 50 // RC_CHANNELS_OVERRIDE CRC extra
    fun override(sequence: Int, targetSystem: Int, targetComponent: Int, roll: Int, pitch: Int, throttle: Int, yaw: Int): ByteArray {
        require(targetSystem in 1..255 && targetComponent in 1..255)
        val channels = intArrayOf(roll, pitch, throttle, yaw)
        require(channels.all { it in 1000..2000 })
        return overrideFrame(sequence, targetSystem, targetComponent, channels)
    }
    private fun accumulate(current: Int, value: Int): Int {
        var tmp = (value xor (current and 255)) and 255
        tmp = (tmp xor (tmp shl 4)) and 255
        return ((current ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 65535
    }
    fun release(sequence: Int, targetSystem: Int, targetComponent: Int): ByteArray =
        overrideFrame(sequence, targetSystem, targetComponent, intArrayOf(0, 0, 0, 0))

    private fun overrideFrame(sequence: Int, targetSystem: Int, targetComponent: Int, channels: IntArray): ByteArray {
        val payload = ByteArray(18)
        for (i in 0..7) {
            val value = if (i < 4) channels[i] else 65535
            payload[i * 2] = value.toByte(); payload[i * 2 + 1] = (value ushr 8).toByte()
        }
        payload[16] = targetSystem.toByte(); payload[17] = targetComponent.toByte()
        val frame = byteArrayOf(0xFE.toByte(), 18, sequence.toByte(), 255.toByte(), 190.toByte(), 70) + payload
        var crc = 0xffff
        for (b in frame.drop(1)) crc = accumulate(crc, b.toInt() and 255)
        crc = accumulate(crc, EXTRA)
        return frame + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }
    fun heartbeatSystem(packet: ByteArray): Int? {
        if (packet.size < 17) return null
        val v1 = (packet[0].toInt() and 255) == 0xFE
        val v2 = (packet[0].toInt() and 255) == 0xFD
        if (!v1 && !v2) return null
        val offset = if (v1) 6 else 10
        val length = packet[1].toInt() and 255
        if (length != 9 || packet.size < offset + length + 2) return null
        if (v1 && (packet[5].toInt() and 255) != 0) return null
        if (v2 && (packet[7].toInt() != 0 || packet[8].toInt() != 0 || packet[9].toInt() != 0)) return null
        var crc = 0xffff
        for (i in 1 until offset + length) crc = accumulate(crc, packet[i].toInt() and 255)
        crc = accumulate(crc, 50) // HEARTBEAT CRC extra
        if ((packet[offset + length].toInt() and 255) != (crc and 255) ||
            (packet[offset + length + 1].toInt() and 255) != (crc ushr 8)) return null
        val system = packet[if (v1) 3 else 5].toInt() and 255
        return system.takeIf { it in 1..254 }
    }

    /** Accept only complete, checksum-valid GLOBAL_POSITION_INT frames from the rover. */
    fun globalPosition(packet: ByteArray): GlobalPosition? {
        if (packet.size < 20) return null
        val v1 = (packet[0].toInt() and 255) == 0xFE
        val v2 = (packet[0].toInt() and 255) == 0xFD
        if (!v1 && !v2) return null
        val offset = if (v1) 6 else 10
        val length = packet[1].toInt() and 255
        if (length < 12 || packet.size < offset + length + 2) return null
        if (v2 && (packet[2].toInt() and 1) != 0) return null // signed frames need authentication
        val id = if (v1) packet[5].toInt() and 255 else
            (packet[7].toInt() and 255) or ((packet[8].toInt() and 255) shl 8) or ((packet[9].toInt() and 255) shl 16)
        if (id != 33) return null
        var crc = 0xffff
        for (i in 1 until offset + length) crc = accumulate(crc, packet[i].toInt() and 255)
        crc = accumulate(crc, 104) // GLOBAL_POSITION_INT CRC extra
        if ((packet[offset + length].toInt() and 255) != (crc and 255) ||
            (packet[offset + length + 1].toInt() and 255) != (crc ushr 8)) return null
        fun int32(start: Int) = (packet[start].toInt() and 255) or
            ((packet[start + 1].toInt() and 255) shl 8) or
            ((packet[start + 2].toInt() and 255) shl 16) or
            (packet[start + 3].toInt() shl 24)
        val lat = int32(offset + 4) / 1e7
        val lon = int32(offset + 8) / 1e7
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || lat == 0.0 && lon == 0.0) return null
        val system = packet[if (v1) 3 else 5].toInt() and 255
        return GlobalPosition(system, lat, lon).takeIf { system in 1..254 }
    }
}
