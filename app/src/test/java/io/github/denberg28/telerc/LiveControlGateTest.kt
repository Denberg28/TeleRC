package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class LiveControlGateTest {
    @Test fun movingOrInvalidAxesCannotAcquireAuthority() {
        val gate = LiveControlGate()
        for ((steering, drive) in listOf(1800 to 1500, 1500 to 1200, 0 to 1500, 1500 to 65535)) {
            assertFalse(gate.enable(1, 1000, 1001, steering, drive))
            assertFalse(gate.get())
        }
        assertTrue(gate.enable(1, 1000, 1001, 1500, 1500))
        assertFalse(gate.enable(1, 1000, 1001, 1500, 1800))
        assertFalse(gate.get())
    }
    @Test fun telemetryRecoveryRequiresExplicitEnable() {
        val gate = LiveControlGate()
        assertTrue(gate.enable(1, 1000, 1001))
        assertFalse(gate.expire(1, 1000, 4999))
        assertTrue(gate.get())
        assertTrue(gate.expire(1, 1000, 5000))
        assertFalse(gate.get())
        assertFalse(gate.expire(1, 5100, 5101))
        assertFalse(gate.get())
        assertTrue(gate.enable(1, 5100, 5101))
    }

    @Test fun disconnectedOrExpiredLinkCannotEnable() {
        val gate = LiveControlGate()
        assertFalse(gate.enable(0, 1000, 1001))
        assertFalse(gate.enable(1, 0, 1001))
        assertFalse(gate.enable(1, 1000, 5000))
        assertFalse(gate.get())
    }

    @Test fun lifecycleStopStaysDisabledAcrossFreshTelemetry() {
        val gate = LiveControlGate()
        assertTrue(gate.enable(1, 1000, 1001))
        gate.disable()
        assertFalse(gate.expire(1, 1100, 1101))
        assertFalse(gate.get())
    }

    @Test fun clockRegressionRevokesAuthorityOnce() {
        val gate = LiveControlGate()
        assertTrue(gate.enable(1, 2000, 2001))
        assertTrue(gate.expire(1, 2000, 1999))
        assertFalse(gate.expire(1, 2000, 1998))
    }
}
