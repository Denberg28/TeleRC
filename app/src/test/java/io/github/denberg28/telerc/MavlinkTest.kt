package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class MavlinkTest {
    @Test fun roverNeutralAndMapping() {
        assertEquals(RcChannels(1500, 1500, 1500, 1500), RoverControls.neutral)
        assertEquals(RcChannels(1700, 1500, 1200, 1500), RoverControls.channels(1700, 1200))
        assertFalse(CraftProfile.ROCKET.available)
    }
    @Test fun overrideBoundsAndFrame() {
        val frame = Mavlink.override(7, 1, 1, 1500, 1500, 1000, 1500)
        assertEquals(26, frame.size)
        assertEquals(0xFE, frame[0].toInt() and 255)
        assertEquals(70, frame[5].toInt())
        assertEquals(0xDC, frame[6].toInt() and 255)
        assertEquals(0x05, frame[7].toInt() and 255)
        assertThrows(IllegalArgumentException::class.java) { Mavlink.override(0, 1, 1, 999, 1500, 1000, 1500) }
    }
    @Test fun releaseClearsOverrides() {
        val frame = Mavlink.release(1, 1, 1)
        assertTrue((6..13).all { frame[it].toInt() == 0 })
    }
    @Test fun forgedHeartbeatIgnored() {
        val packet = byteArrayOf(0xFE.toByte(), 9, 0, 1, 1, 0) + ByteArray(9) + byteArrayOf(0, 0)
        assertNull(Mavlink.heartbeatSystem(packet))
    }
    @Test fun truncatedV2HeartbeatNeverCrashes() {
        val truncated = byteArrayOf(0xFD.toByte(), 9, 0, 0, 0, 1, 1, 0, 0, 0) + ByteArray(9)
        for (length in 0 until truncated.size) {
            assertNull(Mavlink.heartbeatSystem(truncated.copyOf(length)))
        }
        assertNull(Mavlink.heartbeatSystem(truncated))
    }
    @Test fun malformedHeartbeatIgnored() { assertNull(Mavlink.heartbeatSystem(byteArrayOf(0xFE.toByte(), 9))) }
    @Test fun globalPositionRequiresValidFrame() {
        val payload = ByteArray(28)
        fun put32(offset: Int, value: Int) { for (i in 0..3) payload[offset + i] = (value ushr (8 * i)).toByte() }
        put32(4, 145_995_000); put32(8, 1_209_842_000)
        val header = byteArrayOf(0xFE.toByte(), 28, 1, 1, 1, 33)
        var crc = 0xffff
        fun accumulate(value: Int) {
            var tmp = (value xor (crc and 255)) and 255
            tmp = (tmp xor (tmp shl 4)) and 255
            crc = ((crc ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 65535
        }
        (header.drop(1) + payload.toList()).forEach { accumulate(it.toInt() and 255) }
        accumulate(104)
        val packet = header + payload + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
        val position = Mavlink.globalPosition(packet)
        assertEquals(1, position?.system)
        assertEquals(14.5995, position!!.latitude, 0.000001)
        assertEquals(120.9842, position.longitude, 0.000001)
        assertNull(Mavlink.globalPosition(packet.copyOf(packet.size - 1)))
        packet[10] = (packet[10].toInt() xor 1).toByte()
        assertNull(Mavlink.globalPosition(packet))
    }
}
