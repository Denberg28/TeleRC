package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class MissionTransferTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val points = listOf(Waypoint(13.6, 123.2), Waypoint(-13.7, -123.3))
    private fun acc(c: Int, b: Int): Int {
        var t = (b xor (c and 255)) and 255; t = (t xor (t shl 4)) and 255
        return ((c ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
    }
    private fun reply(index: Int = 0, result: Int? = null, integer: Boolean = true,
                      system: Int = 1, target: Int = 255, component: Int = 190,
                      v2: Boolean = false, missionType: Int = 0): ByteArray {
        val id = if (result != null) 47 else if (integer) 51 else 40
        var p = if (result != null) byteArrayOf(target.toByte(), component.toByte(), result.toByte())
            else byteArrayOf(index.toByte(), (index ushr 8).toByte(), target.toByte(), component.toByte())
        if (v2) p += missionType.toByte()
        val bytes = (if (v2) byteArrayOf(253.toByte(), p.size.toByte(), 0, 0, 1, system.toByte(), 1, id.toByte(), 0, 0)
            else byteArrayOf(254.toByte(), p.size.toByte(), 1, system.toByte(), 1, id.toByte())) + p
        var crc = 65535
        for (i in 1 until bytes.size) crc = acc(crc, bytes[i].toInt() and 255)
        crc = acc(crc, if (id == 47) 153 else if (id == 51) 196 else 230)
        return bytes + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }
    private fun transfer() = MissionTransfer().apply { begin(points, 1, 100); poll(100, 0) }
    @Test fun completeUploadRequiresEveryRequestAndFinalAck() {
        val t = transfer()
        t.receive(reply(result = 0)); assertTrue(t.active)
        for (index in 0..2) {
            t.receive(reply(index)); val packet = t.poll(200L + index, index + 1)!!
            assertEquals(73, packet[5].toInt()); assertEquals(index, packet[34].toInt())
        }
        assertTrue(t.active)
        t.receive(reply(result = 0, v2 = true)); assertFalse(t.active)
        assertTrue(t.status.startsWith("Upload acknowledged")); assertNull(t.poll(10000, 5))
    }
    @Test fun requestsAreIsolatedBySourceTargetTypeAndChecksum() {
        val t = transfer()
        listOf(reply(system = 2), reply(target = 0), reply(component = 1),
            reply(v2 = true, missionType = 1), reply().apply { this[lastIndex] = 0 }).forEach {
            assertNull(MissionWire.reply(it, 1)); t.receive(it); assertNull(t.poll(101, 1))
        }
        t.receive(reply(v2 = true)); assertNotNull(t.poll(102, 1))
    }
    @Test fun legacyRequestsUseLegacyFloatItems() {
        val t = transfer(); t.receive(reply(integer = false))
        val packet = t.poll(101, 1)!!
        assertEquals(39, packet[5].toInt()); assertEquals(3, packet[40].toInt())
    }
    @Test fun duplicateRequestResendsSameItemAndOutOfOrderIsIgnored() {
        val t = transfer(); t.receive(reply(2)); assertNull(t.poll(101, 1))
        t.receive(reply(0)); val first = t.poll(102, 1)!!
        t.receive(reply(0)); assertArrayEquals(first, t.poll(103, 1))
        t.receive(reply(2)); assertNull(t.poll(104, 1))
    }
    @Test fun retriesAndOverallLifetimeAreBounded() {
        val t = transfer()
        repeat(3) { assertNotNull(t.poll(1600L + it * 1500, it + 1)) }
        assertNull(t.poll(6100, 5)); assertFalse(t.active)
        val total = transfer(); assertNull(total.poll(120100, 0)); assertFalse(total.active)
    }
    @Test fun cancellationAndPlanEditNeverReplay() {
        val t = transfer(); t.cancel("Disconnected")
        t.receive(reply()); assertNull(t.poll(200, 1)); assertFalse(t.active)
        t.begin(points, 2, 300); t.poll(300, 0); t.receive(reply(system = 1))
        assertNull(t.poll(301, 1)); t.edited(); assertNull(t.poll(302, 2))
    }
    @Test fun errorAckAndClockResetAbort() {
        val t = transfer(); t.receive(reply(result = 14)); assertFalse(t.active)
        assertTrue(t.status.contains("rejected"))
        val clock = transfer(); assertNull(clock.poll(99, 1)); assertFalse(clock.active)
    }
    @Test fun errorInSameReceiveBatchCancelsPendingItem() {
        val t = transfer(); t.receive(reply(0)); t.receive(reply(result = 14))
        assertFalse(t.active); assertNull(t.poll(102, 1))
    }
    @Test fun coordinateAndHomeSlotEncoding() {
        val count = MissionWire.count(0, 1, 3)
        assertEquals(44, count[5].toInt()); assertEquals(3, count[6].toInt())
        val packet = MissionWire.item(1, 1, 2, points[1], true)
        val b = java.nio.ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(-137000000, b.getInt(22)); assertEquals(-1233000000, b.getInt(26))
        assertEquals(16, b.getShort(36).toInt()); assertEquals(0, packet[41].toInt())
        assertEquals(1, packet[42].toInt())
    }
    @Test fun officialPymavlinkGoldenVectorsMatchWireFormatAndTrimmedReplies() {
        // Independently generated with pymavlink's ArduPilot v1/v2 dialects, source 255/190 or 1/1.
        assertArrayEquals(hex("fe0400ffbe2c0300010170ef"), MissionWire.count(0, 1, 3))
        assertArrayEquals(hex("fe2501ffbe4900000000000000000000000000000000c08bd5f7c0e981b6000000000200100001010600018341"),
            MissionWire.item(1, 1, 2, points[1], true))
        assertEquals(0, MissionWire.reply(hex("fd0400000001013300000000ffbe0b45"), 1)!!.index)
        assertEquals(0, MissionWire.reply(hex("fd0200000001012f0000ffbedc54"), 1)!!.result)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyPlanCannotEraseOnboardMission() {
        MissionTransfer().begin(emptyList(), 1, 100)
    }
    @Test fun truncatedPacketsNeverThrow() {
        val packet = reply(v2 = true)
        for (length in 0 until packet.size) assertNull(MissionWire.reply(packet.copyOf(length), 1))
    }
}
