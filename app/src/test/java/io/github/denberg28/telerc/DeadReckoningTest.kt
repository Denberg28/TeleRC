package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class DeadReckoningTest {
    @Test fun integratesOnlyPreviouslyTransmittedFramesAndResets() {
        val estimate = DeadReckoning()
        estimate.accept(1_000L, 1500, 2000)
        assertEquals(.76f, estimate.pose.y, .0001f)
        val moving = estimate.accept(1_100L, 1500, 2000)
        assertTrue(moving.y < .76f)
        estimate.accept(1_200L, 2000, 1500)
        val turning = estimate.accept(1_300L, 2000, 1500)
        assertTrue(turning.heading > 0f)
        estimate.reset()
        assertEquals(RoverPose(), estimate.pose)
        estimate.accept(5_000L, 1500, 1500)
        assertEquals(RoverPose(), estimate.pose)
    }

    @Test fun delayedFrameDoesNotIntegrateUnboundedTime() {
        val estimate = DeadReckoning()
        estimate.accept(1_000L, 1500, 2000)
        val delayed = estimate.accept(61_000L, 1500, 2000)
        assertTrue(.76f - delayed.y < .18f)
    }
    @Test fun outageHoldsLastEstimateUntilNewFramesArrive() {
        val estimate = DeadReckoning()
        estimate.accept(1000L, 1500, 2000)
        val beforeLoss = estimate.accept(1100L, 1500, 2000)
        estimate.hold()
        assertEquals(beforeLoss, estimate.accept(70_000L, 1500, 2000))
        assertTrue(estimate.accept(70_100L, 1500, 2000).y < beforeLoss.y)
    }
}
