package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class ControlResponseTest {
    @Test fun legacyUpgradeAndPartialMigrationPreserveEachAxis() {
        assertEquals(ControlResponse(), ControlResponse.fromStored(emptyMap<String, Any>()))
        val old = mapOf("sensitivity" to 45)
        assertEquals(ControlResponse(45, 45, 0), ControlResponse.fromStored(old))
        val split = old + mapOf(ControlResponse.STEERING_KEY to 30, ControlResponse.DRIVE_KEY to 95, ControlResponse.EXPO_KEY to 60)
        assertEquals(ControlResponse(30, 95, 60), ControlResponse.fromStored(split))
        assertEquals(ControlResponse(30, 45, 0), ControlResponse.fromStored(old + (ControlResponse.STEERING_KEY to 30)))
        // Loading the new keys again must not reapply the shared legacy value.
        assertEquals(ControlResponse(30, 95, 60), ControlResponse.fromStored(split + ("sensitivity" to 100)))
    }

    @Test fun invalidPreferencesAreBoundedWithoutCrossCoupling() {
        assertEquals(ControlResponse(25, 100, 100), ControlResponse.fromStored(mapOf(
            ControlResponse.STEERING_KEY to -1, ControlResponse.DRIVE_KEY to 400, ControlResponse.EXPO_KEY to 101)))
        assertEquals(ControlResponse(25, 25, 0), ControlResponse.fromStored(mapOf("sensitivity" to -5, ControlResponse.EXPO_KEY to -5)))
        assertEquals(ControlResponse(), ControlResponse.fromStored(mapOf("sensitivity" to "bad", ControlResponse.EXPO_KEY to "bad")))
    }

    @Test fun driveAndSteerUseIndependentGainInBothDirections() {
        val settings = ControlResponse(25, 100, 0)
        assertEquals(1600, settings.channel(StickPosition(1f, 0f), false))
        assertEquals(1400, settings.channel(StickPosition(-1f, 0f), false))
        assertEquals(1900, settings.channel(StickPosition(0f, -1f), true))
        assertEquals(1100, settings.channel(StickPosition(0f, 1f), true))
        assertEquals(1900, settings.copy(steeringPercent = 100).channel(StickPosition(1f, 0f), false))
        assertEquals(1900, settings.copy(steeringPercent = 100).channel(StickPosition(0f, -1f), true))
        assertEquals(1600, settings.copy(drivePercent = 25).channel(StickPosition(1f, 0f), false))
        assertEquals(1600, settings.copy(drivePercent = 25).channel(StickPosition(0f, -1f), true))
    }

    @Test fun expoHasKnownCurveAndKeepsNeutralAndEndpoints() {
        assertEquals(1700, stickChannel(StickPosition(.5f, 0f), false, 1f, 0f))
        assertEquals(1625, stickChannel(StickPosition(.5f, 0f), false, 1f, .5f))
        assertEquals(1550, stickChannel(StickPosition(.5f, 0f), false, 1f, 1f))
        assertEquals(1450, stickChannel(StickPosition(-.5f, 0f), false, 1f, 1f))
        for (expo in listOf(0f, .5f, 1f)) {
            assertEquals(1900, stickChannel(StickPosition(1f, 0f), false, 1f, expo))
            assertEquals(1100, stickChannel(StickPosition(-1f, 0f), false, 1f, expo))
            assertEquals(1600, stickChannel(StickPosition(1f, 0f), false, .25f, expo))
            assertEquals(1500, stickChannel(StickPosition(0f, 0f), false, 1f, expo))
            assertEquals(1500, stickChannel(StickPosition(.079f, 1f), false, 1f, expo))
            assertEquals(1500, stickChannel(StickPosition(1f, 0f), true, 1f, expo))
        }
    }

    @Test fun everyCurveIsMonotonicBoundedAndSignPreserving() {
        for (gain in listOf(.25f, .5f, 1f)) for (expo in listOf(0f, .3f, .7f, 1f)) {
            var previous = 1100
            for (step in -1000..1000) {
                val x = step / 1000f
                val steer = stickChannel(StickPosition(x, 0f), false, gain, expo)
                val drive = stickChannel(StickPosition(0f, -x), true, gain, expo)
                assertEquals(steer, drive)
                assertTrue(steer in 1100..1900)
                assertTrue(steer >= previous)
                assertTrue(if (x < 0) steer <= 1500 else steer >= 1500)
                previous = steer
            }
        }
    }

    @Test fun invalidNumericInputNeverBecomesMotion() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(StickPosition(0f, 0f), limitStick(bad, 0f))
            assertEquals(1500, stickChannel(StickPosition(bad, 0f), false))
            assertEquals(1500, stickChannel(StickPosition(1f, 0f), false, bad))
            assertEquals(1500, stickChannel(StickPosition(1f, 0f), false, 1f, bad))
        }
    }
}
