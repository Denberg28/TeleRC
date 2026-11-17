package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class BackpackRecoveryTest {
    @Test fun longTelemetryOutageDoesNotPreventRegistrationOrFloodTheLink() {
        val recovery = BackpackRecovery()
        var checks = 0
        val present = { checks++; true }
        assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(0, true, true, present))
        for (time in 1L..999L) assertEquals(BackpackRecovery.Action.WAIT, recovery.poll(time, true, true, present))
        assertEquals(1, checks)
        for (time in listOf(1000L, 5000L, 60000L, 3600000L))
            assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(time, true, true, present))
        assertEquals(5, checks)
    }

    @Test fun backgroundAndDisconnectDoNotRegisterOrInspectNetworks() {
        val recovery = BackpackRecovery()
        val forbidden = { error("Inactive session inspected a network") }
        assertEquals(BackpackRecovery.Action.WAIT, recovery.poll(0, false, true, forbidden))
        assertEquals(BackpackRecovery.Action.WAIT, recovery.poll(5000, true, false, forbidden))
        assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(6000, true, true) { true })
    }

    @Test fun removedBoundNetworkRequestsRebindAndNewSessionCanRegister() {
        val recovery = BackpackRecovery()
        assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(100, true, true) { true })
        assertEquals(BackpackRecovery.Action.REBIND, recovery.poll(1100, true, true) { false })
        // A replacement socket has a new recovery policy; no stale retry timestamp is inherited.
        assertEquals(BackpackRecovery.Action.REGISTER, BackpackRecovery().poll(1101, true, true) { true })
    }

    @Test fun ordinaryWifiRebindNeverRestoresDrivingAuthority() {
        val recovery=BackpackRecovery()
        val control=LiveControlGate()
        assertTrue(control.enable(1,100,100))
        assertEquals(BackpackRecovery.Action.REBIND,recovery.poll(1100,true,true) { false })
        control.disable() // Existing session teardown revokes authority.
        assertEquals(BackpackRecovery.Action.REGISTER,BackpackRecovery().poll(1200,true,true) { true })
        assertFalse(control.get())
    }

}
