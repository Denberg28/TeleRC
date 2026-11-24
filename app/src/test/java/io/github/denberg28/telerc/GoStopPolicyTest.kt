package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class GoStopPolicyTest {
    @Test fun balancingResumeRequiresIntentFreshTelemetryAndNewNeutralDwell() {
        val gate = GoControl()
        gate.heartbeat(true, 1000)
        assertFalse(gate.enabled)
        assertFalse(gate.mayDrive(1001))
        assertTrue(gate.resumeBalancing(1001))
        assertFalse(gate.mayDrive(1001))
        for (now in 1001L..1351L step 50) gate.sent(1500, 1500, now)
        assertTrue(gate.mayDrive(1351))
        gate.stop()
        assertFalse(gate.mayDrive(1352))
        assertFalse(gate.resumeBalancing(3000))
        gate.heartbeat(true, 3000)
        assertFalse(gate.enabled)
    }

    @Test fun balancingLossCancelsTravelWithoutRemovingBalanceTorque() {
        assertFalse(GoStopPolicy.disarm(true, false))
        assertTrue(GoStopPolicy.disarm(true, true))
        assertTrue(GoStopPolicy.disarm(false, false))
    }

    @Test fun inProgressIsNotAnArmRejection() {
        assertTrue(GoStopPolicy.armReplyPending(0))
        assertTrue(GoStopPolicy.armReplyPending(5))
        for (result in listOf(1, 2, 3, 4, 6, 255)) assertFalse(GoStopPolicy.armReplyPending(result))
    }

    @Test fun autopilotLifecycleLossDoesNotDisarmOrChangeItsMissionPolicy() {
        assertFalse(GoStopPolicy.disarm(false, false, true))
        assertTrue(GoStopPolicy.disarm(false, true, true))
    }
}
