package io.github.denberg28.telerc

/** TeleRC UART framing shared with the base and motor firmware. */
object SerialDatagram {
    const val MAX_PAYLOAD = 280
    private fun crc(bytes: ByteArray, start: Int, end: Int): Int {
        var value = 0xffff
        for (i in start until end) {
            value = value xor ((bytes[i].toInt() and 255) shl 8)
            repeat(8) {
                value = ((value shl 1) xor if (value and 0x8000 != 0) 0x1021 else 0) and 0xffff
            }
        }
        return value
    }
    fun encode(payload: ByteArray): ByteArray {
        require(payload.size in 1..MAX_PAYLOAD)
        val frame = ByteArray(payload.size + 6)
        frame[0] = 0xa5.toByte(); frame[1] = 0x5a
        frame[2] = payload.size.toByte(); frame[3] = (payload.size shr 8).toByte()
        payload.copyInto(frame, 4)
        val checksum = crc(frame, 2, frame.size - 2)
        frame[frame.size - 2] = checksum.toByte(); frame[frame.size - 1] = (checksum shr 8).toByte()
        return frame
    }
    class Parser {
        private val frame = ByteArray(MAX_PAYLOAD + 6)
        private var used = 0
        private var expected = 0
        private var lastAt = 0L
        private fun reset() { used = 0; expected = 0 }
        fun feed(bytes: ByteArray, count: Int, now: Long): List<ByteArray> {
            require(count in 0..bytes.size)
            if (used > 0 && now - lastAt > 20) reset()
            if (count > 0) lastAt = now
            val result = mutableListOf<ByteArray>()
            for (i in 0 until count) {
                val byte = bytes[i]
                if (used == 0) { if (byte == 0xa5.toByte()) frame[used++] = byte; continue }
                if (used == 1 && byte != 0x5a.toByte()) {
                    reset(); if (byte == 0xa5.toByte()) frame[used++] = byte; continue
                }
                frame[used++] = byte
                if (used == 4) {
                    val length = (frame[2].toInt() and 255) or ((frame[3].toInt() and 255) shl 8)
                    if (length !in 1..MAX_PAYLOAD) { reset(); continue }
                    expected = length + 6
                }
                if (expected > 0 && used == expected) {
                    val checksum = (frame[expected - 2].toInt() and 255) or
                        ((frame[expected - 1].toInt() and 255) shl 8)
                    if (checksum == crc(frame, 2, expected - 2)) result.add(frame.copyOfRange(4, expected - 2))
                    reset()
                }
            }
            return result
        }
    }
}
