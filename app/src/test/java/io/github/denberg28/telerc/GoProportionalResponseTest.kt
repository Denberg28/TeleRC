package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GoProportionalResponseTest {
    private val deadband = ControlResponse.GO_RECEIVER_DEADBAND_US

    private fun command(gain: Int, axis: Float, vertical: Boolean, expo: Int = 100): Int =
        ControlResponse(gain, gain, expo).proportionalChannel(
            if (vertical) StickPosition(0f, -axis) else StickPosition(axis, 0f), vertical, deadband
        )

    @Test fun lineFractionsProduceSymmetricProportionalDemandBeyondDeadband() {
        for (gain in listOf(25, 50, 75, 100)) {
            for (fraction in listOf(.09f, .12f, .25f, .5f, .75f, 1f)) {
                for (vertical in listOf(false, true)) {
                    val pos = command(gain, fraction, vertical)
                    val neg = command(gain, -fraction, vertical)
                    assertEquals(3000 - pos, neg)
                    val normalized = (pos - 1500 - deadband) / (400f - deadband)
                    assertEquals(fraction * gain / 100f, normalized, 1f / 365f)
                    assertTrue(abs(pos - 1500) > deadband)
                }
            }
        }
    }

    @Test fun fixedNeutralGuardAndIndependentSensitivityRemain() {
        val s = ControlResponse(25, 75, 100)
        for (axis in listOf(-.08f, -.04f, 0f, .04f, .08f)) {
            assertEquals(1500, command(100, axis, false))
            assertEquals(1500, command(100, axis, true))
        }
        assertEquals(1626, s.proportionalChannel(StickPosition(1f, 0f), false, deadband))
        assertEquals(1809, s.proportionalChannel(StickPosition(0f, -1f), true, deadband))
        for (gain in 25..100) {
            var previous = 1500
            for (i in 0..1000) {
                val axis = i / 1000f
                val value = command(gain, axis, false)
                assertTrue(value >= previous && value in 1500..1900)
                assertEquals(3000 - value, command(gain, -axis, false))
                assertEquals(value, command(gain, axis, false, 0))
                previous = value
            }
        }
    }

    @Test fun invalidInputStaysNeutral() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1500, command(100, bad, false))
            assertEquals(1500, proportionalStickChannel(StickPosition(1f, 0f), false, bad, deadband))
        }
        assertEquals(1500, proportionalStickChannel(StickPosition(1f, 0f), false, 1f, -1))
        assertEquals(1500, proportionalStickChannel(StickPosition(1f, 0f), false, 1f, 100))
    }
}
