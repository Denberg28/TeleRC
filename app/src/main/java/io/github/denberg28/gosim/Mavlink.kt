package io.github.denberg28.gosim

object Mavlink {
    private const val EXTRA = 50 // RC_CHANNELS_OVERRIDE CRC extra
    fun override(sequence: Int, targetSystem: Int, targetComponent: Int, roll: Int, pitch: Int, throttle: Int, yaw: Int): ByteArray {
        require(targetSystem in 1..255 && targetComponent in 1..255)
        val channels = intArrayOf(roll, pitch, throttle, yaw)
        require(channels.all { it in 1000..2000 })
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
    private fun accumulate(current: Int, value: Int): Int {
        var tmp = (value xor (current and 255)) and 255
        tmp = (tmp xor (tmp shl 4)) and 255
        return ((current ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 65535
    }
    fun heartbeatSystem(packet: ByteArray): Int? {
        if (packet.size < 17) return null
        // MAVLink 1 heartbeat: magic, length 9, message id 0; MAVLink 2: length >=9, id 0.
        val v1 = (packet[0].toInt() and 255) == 0xFE && (packet[1].toInt() and 255) >= 9 && (packet[5].toInt() and 255) == 0
        val v2 = (packet[0].toInt() and 255) == 0xFD && (packet[1].toInt() and 255) >= 9 && packet[7].toInt() == 0 && packet[8].toInt() == 0 && packet[9].toInt() == 0
        if (!v1 && !v2) return null
        val offset = if (v1) 6 else 10
        val length = packet[1].toInt() and 255
        if (packet.size < offset + length + 2) return null
        return packet[if (v1) 3 else 5].toInt() and 255
    }
}
