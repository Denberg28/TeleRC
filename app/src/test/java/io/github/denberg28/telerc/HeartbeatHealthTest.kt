package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class HeartbeatHealthTest {
    @Test fun toleratesOneMissedHeartbeatThenExpires() {
        assertTrue(HeartbeatHealth.isFresh(1, 1000L, 3000L))
        assertTrue(HeartbeatHealth.isFresh(1, 1000L, 3499L))
        assertFalse(HeartbeatHealth.isFresh(1, 1000L, 3500L))
    }

    @Test fun requiresVerifiedHeartbeatAndNonzeroTarget() {
        assertFalse(HeartbeatHealth.isFresh(0, 1000L, 1001L))
        assertFalse(HeartbeatHealth.isFresh(1, 0L, 1001L))
        assertFalse(HeartbeatHealth.isFresh(1, 2000L, 1999L))
    }
}
