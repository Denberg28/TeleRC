package io.github.denberg28.telerc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

internal object TestMavlink {
    fun frame(id: Int, extra: Int, payload: ByteArray, system: Int = 1, component: Int = 1): ByteArray {
        val f = byteArrayOf(0xfe.toByte(), payload.size.toByte(), 1, system.toByte(), component.toByte(), id.toByte()) + payload
        var crc = 65535
        for (v in f.drop(1).map { it.toInt() and 255 } + extra) {
            var t = v xor (crc and 255); t = (t xor (t shl 4)) and 255
            crc = ((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
        }
        return f + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }
}

class BalancerTelemetryTest {
    private fun heartbeat(boot: Int = 42, armed: Boolean = true, health: Int = 4) =
        TestMavlink.frame(0, 50, ByteArray(9).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0, boot)
            it[4] = 10; it[6] = if (armed) 0xc0.toByte() else 64; it[7] = health.toByte(); it[8] = 3
        })
    private fun attitude(stamp: Int = 100, pitch: Float = .1f, system: Int = 1) =
        TestMavlink.frame(30, 39, ByteArray(28).also {
            val b = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(0, stamp); b.putFloat(8, pitch); b.putFloat(20, .2f)
        }, system)
    private fun notice(s: String, system: Int = 1) = TestMavlink.frame(253, 83, ByteArray(51).also {
        it[0] = 6; s.toByteArray().copyInto(it, 1)
    }, system)

    @Test fun currentFirmwareReportsAreReadWithoutGrantingAuthority() {
        val t = BalancerTelemetry()
        assertFalse(t.receive(heartbeat(), 0, 1000))
        t.receive(attitude(), 1, 1001); t.receive(notice("PAUSED_FALL_HOLD_LEVEL"), 1, 1002)
        assertTrue(t.summary(1100).startsWith("ARMED"))
        assertTrue(t.detail(1100).contains("5.73°"))
        assertTrue(t.detail(1100).contains("PAUSED FALL HOLD LEVEL"))
        assertTrue(t.detail(1600).contains("stale"))
        assertTrue(t.summary(2300).contains("stale"))
    }
    @Test fun malformedWrongPeerReplayAndNonfiniteCannotReplaceTelemetry() {
        val t = BalancerTelemetry(); t.receive(heartbeat(), 1, 1000)
        t.receive(attitude(), 1, 1001)
        t.receive(attitude(100, .4f), 1, 1002)
        t.receive(attitude(99, .4f), 1, 1003)
        t.receive(attitude(101, Float.NaN), 1, 1004)
        t.receive(attitude(102, .4f, 2), 1, 1005)
        t.receive(attitude(103, .4f).also { it[15] = (it[15].toInt() xor 1).toByte() }, 1, 1006)
        t.receive(notice("WRONG_PEER", 2), 1, 1007)
        assertTrue(t.detail(1100).contains("5.73°"))
        assertFalse(t.detail(1100).contains("WRONG"))
    }
    @Test fun newBootRevokesOldStateAndFaultIsSeparateFromArm() {
        val t = BalancerTelemetry(); t.receive(heartbeat(), 1, 1000)
        t.receive(attitude(), 1, 1001); t.receive(notice("PAUSED"), 1, 1002)
        assertTrue(t.receive(heartbeat(43, true, 5), 1, 1100))
        assertTrue(t.summary(1101).contains("ARMED")); assertTrue(t.summary(1101).contains("fault"))
        assertFalse(t.detail(1101).contains("PAUSED")); assertTrue(t.detail(1101).contains("unavailable"))
        t.reset(); assertTrue(t.summary(1102).contains("unknown"))
    }
    @Test fun rcStreamRequestIsNotAMissionOrModeCommand() {
        val f = Mavlink.requestMessage(3, 7, 65, 200000)
        val p = ByteBuffer.wrap(f, 6, 33).slice().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(511, p.getShort(28).toInt()); assertEquals(7, f[36].toInt())
        assertEquals(65f, p.getFloat(0), 0f); assertEquals(200000f, p.getFloat(4), 0f)
    }
}
