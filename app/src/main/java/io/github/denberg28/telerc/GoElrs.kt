package io.github.denberg28.telerc

/** Matched standalone receiver firmware, carried over Wi-Fi UDP. */
internal object GoElrs {
    private fun crc(current: Int, value: Int): Int {
        var t = (value xor (current and 255)) and 255
        t = (t xor (t shl 4)) and 255
        return ((current ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
    }
    fun take(sequence: Int, target: Int): ByteArray {
        require(target in 1..254)
        val p = ByteArray(33)
        p[2] = 0x80.toByte(); p[3] = 0x3f // param1 = 1.0f; no retries/force-arm.
        p[28] = 0x22; p[29] = 0x79 // MAV_CMD_USER_1 = 31010.
        p[30] = target.toByte(); p[31] = 1
        val f = byteArrayOf(0xfe.toByte(), 33, sequence.toByte(), 255.toByte(), 190.toByte(), 76) + p
        var c = 65535; for (b in f.drop(1)) c = crc(c, b.toInt() and 255); c = crc(c, 152)
        return f + byteArrayOf(c.toByte(), (c ushr 8).toByte())
    }
    data class Status(
        val owner: Int,
        val receiver: Boolean,
        val stopped: Boolean,
        val takeover: Boolean = false,
        val capable: Boolean = false,
        val balancing: Boolean = false
    )
    fun status(frame: ByteArray, target: Int): Status? {
        // Matched firmware emits unsigned MAVLink 1 NAMED_VALUE_INT only.
        if (target !in 1..254 || frame.size != 26 || (frame[0].toInt() and 255) != 254 || frame[1].toInt() != 18 ||
            (frame[3].toInt() and 255) != target || frame[4].toInt() != 1 || (frame[5].toInt() and 255) != 252
        ) return null
        if (!frame.copyOfRange(14, 24).contentEquals("TELERC_OWN".toByteArray(Charsets.US_ASCII))) return null
        var c = 65535; for (i in 1..23) c = crc(c, frame[i].toInt() and 255); c = crc(c, 44)
        if ((frame[24].toInt() and 255) != (c and 255) || (frame[25].toInt() and 255) != (c ushr 8)) return null
        val v = (frame[10].toInt() and 255) or ((frame[11].toInt() and 255) shl 8)
        if (frame[12].toInt() != 0 || frame[13].toInt() != 0 || v and 0xe0fc != 0 || (v and 3) > 2) return null
        return Status(v and 3, v and 256 != 0, v and 512 != 0, v and 1024 != 0, v and 2048 != 0, v and 4096 != 0)
    }
}
