package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import io.github.denberg28.telerc.FpvHealthPolicy.Action.*

class FpvHealthPolicyTest {
    @Test fun controlFreshnessKeepsTheExistingTwoSecondBoundaryBeforeUsbRecovery() {
        val p = FpvHealthPolicy(); p.started(100)
        assertFalse(p.controlStale(9000)) // Initial opening already disables control.
        p.frame(10000)
        assertFalse(p.controlStale(12000))
        assertTrue(p.controlStale(12001))
        assertEquals(WAIT,p.check(12001)) // Disable control without resetting USB.
        p.frame(12500)
        assertFalse(p.controlStale(12500)) // No automatic vehicle re-enable.
    }
    @Test fun briefFrameGapsKeepTheUsbSession() {
        val p = FpvHealthPolicy(); p.started(100)
        p.frame(500)
        assertEquals(WAIT, p.check(3499))
        p.frame(3500)
        assertEquals(WAIT, p.check(6499))
    }
    @Test fun stalledPreviewRestartsBeforeUsbIsReopened() {
        val p = FpvHealthPolicy(); p.started(100); p.frame(500)
        assertEquals(RESTART_PREVIEW, p.check(3500))
        p.started(3500) // Same camera/session, preview stop/start only.
        assertEquals(WAIT, p.check(11499))
        assertEquals(REOPEN_USB, p.check(11500))
    }
    @Test fun firstFrameGetsAnEightSecondWarmupOnEachPreviewStart() {
        val p = FpvHealthPolicy(); p.started(100)
        assertEquals(WAIT, p.check(8099))
        assertEquals(RESTART_PREVIEW, p.check(8100))
        p.started(8100)
        assertEquals(WAIT, p.check(16099))
        assertEquals(REOPEN_USB, p.check(16100))
    }
    @Test fun oneFrameCannotResetTheRecoveryBudgetButSustainedFramesCan() {
        val p = FpvHealthPolicy(); p.started(100)
        assertEquals(RESTART_PREVIEW, p.check(8100)); p.started(8100)
        p.frame(8200)
        assertFalse(p.stable)
        assertEquals(REOPEN_USB, p.check(11200))
        for (now in 12000L..15000L step 500) p.frame(now)
        assertTrue(p.stable)
        assertEquals(RESTART_PREVIEW, p.check(18000))
        assertFalse(p.stable)
    }
    @Test fun newUsbSessionAndIdlePreviewDoNotInheritTheOldDeadline() {
        val p = FpvHealthPolicy(); p.started(100)
        assertEquals(RESTART_PREVIEW, p.check(8100))
        p.reset(); assertEquals(WAIT, p.check(100000))
        p.started(100000)
        assertEquals(WAIT, p.check(99999))
        assertEquals(RESTART_PREVIEW, p.check(108000))
    }
}
