package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class HeartbeatHealthTest {
    @Test fun toleratesHeartbeatJitterThenExpires() {
        assertTrue(HeartbeatHealth.isFresh(1, 1000L, 4000L))
        assertTrue(HeartbeatHealth.isFresh(1, 1000L, 4999L))
        assertFalse(HeartbeatHealth.isFresh(1, 1000L, 5000L))
    }

    @Test fun displayGraceDoesNotExtendControlAuthority() {
        assertFalse(HeartbeatHealth.isFresh(1, 1000L, 5100L))
        assertTrue(HeartbeatHealth.isRecentlySeen(1, 1000L, 5100L))
        assertFalse(HeartbeatHealth.isRecentlySeen(1, 1000L, 9000L))
        assertFalse(HeartbeatHealth.isRecentlySeen(0, 1000L, 5100L))
    }

    @Test fun requiresVerifiedHeartbeatAndNonzeroTarget() {
        assertFalse(HeartbeatHealth.isFresh(0, 1000L, 1001L))
        assertFalse(HeartbeatHealth.isFresh(1, 0L, 1001L))
        assertFalse(HeartbeatHealth.isFresh(1, 2000L, 1999L))
    }
}
