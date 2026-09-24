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
    @Test fun malformedHeartbeatIgnored() { assertNull(Mavlink.heartbeatSystem(byteArrayOf(0xFE.toByte(), 9))) }
}
