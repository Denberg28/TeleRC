package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MiniRoverWifiTest {
    @Test fun neutralDwellTracksSentCommandsAndExpires() {
        val gate = MiniRoverArmGate()
        assertFalse(gate.ready(1000)) // Heartbeats/discovery alone do not qualify.
        gate.sent(1500, 1500, 1000)
        gate.sent(1500, 1500, 1100)
        gate.sent(1500, 1500, 1200)
        assertFalse(gate.ready(1249)) // The old 0/40/80 ms ARM burst was too early.
        gate.sent(1500, 1500, 1300)
        assertTrue(gate.ready(1300))
        assertFalse(gate.ready(1550))
        gate.sent(1500, 1500, 1800) // 500 ms interruption starts a new dwell.
        assertFalse(gate.ready(1800))
    }

    @Test fun motionStopAndClockResetRevokeReadiness() {
        val gate = MiniRoverArmGate()
        fun qualify(start: Long) { for (i in 0..3) gate.sent(1500, 1500, start + i * 100) }
        qualify(0)
        assertTrue(gate.ready(300))
        gate.sent(1526, 1500, 310)
        assertFalse(gate.ready(310))
        qualify(400)
        gate.sent(1500, 1474, 710)
        assertFalse(gate.ready(710))
        qualify(800)
        gate.reset() // STOP, pause, disconnect, expiry, or a new connection.
        assertFalse(gate.ready(1100))
        qualify(1200)
        gate.sent(1500, 1500, 10)
        assertFalse(gate.ready(10))
    }

    @Test fun miniRoverGoldenHeartbeatAndAndroidCommandsInteroperate() {
        // Emitted by the exact MiniMavlink.h shipped in MiniRover i-BUS 1.1.0.
        val heartbeat = File("../tests/mini-rover/heartbeat.hex").readText().trim()
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(Mavlink.Heartbeat(1, false), Mavlink.heartbeat(heartbeat))
        val damaged = heartbeat.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertNull(Mavlink.heartbeat(damaged))
        val dir = File("build/mini-rover-interop").apply { mkdirs() }
        File(dir, "neutral.bin").writeBytes(Mavlink.override(1, 1, 1, 1500, 1500, 1500, 1500))
        File(dir, "drive.bin").writeBytes(Mavlink.override(2, 1, 1, 1700, 1200, 1500, 1500))
        val response = ControlResponse(25, 100, 50)
        File(dir, "response.bin").writeBytes(Mavlink.override(6, 1, 1,
            response.channel(StickPosition(.5f, 0f), false),
            response.channel(StickPosition(0f, .5f), true), 1500, 1500))
        File(dir, "release.bin").writeBytes(Mavlink.release(3, 1, 1))
        File(dir, "arm.bin").writeBytes(Mavlink.armDisarm(4, 1, 1, true))
        File(dir, "disarm.bin").writeBytes(Mavlink.armDisarm(5, 1, 1, false))
    }
}
