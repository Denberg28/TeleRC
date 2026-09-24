package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class JoystickMathTest {
    @Test fun offAxisMovementRemainsNeutral() {
        assertEquals(1500, stickChannel(limitStick(0f, -1f), vertical = false))
        assertEquals(1500, stickChannel(limitStick(1f, 0f), vertical = true))
    }

    @Test fun diagonalMovementShowsBothAxesButControlsOnlyAssignedAxis() {
        val position = limitStick(1f, -1f)
        assertTrue(position.x > 0f && position.y < 0f)
        assertEquals(position.x, -position.y, .0001f)
        assertTrue(stickChannel(position, vertical = false) in 1800..1900)
        assertTrue(stickChannel(position, vertical = true) in 1800..1900)
    }

    @Test fun displacementClampsToCircleAndReleaseCenters() {
        val position = limitStick(5f, 12f)
        assertEquals(1f, kotlin.math.sqrt(position.x * position.x + position.y * position.y), .0001f)
        assertEquals(1000, stickChannel(limitStick(0f, 1f), vertical = true))
        assertEquals(1500, stickChannel(StickPosition(0f, 0f), vertical = true))
    }
}
