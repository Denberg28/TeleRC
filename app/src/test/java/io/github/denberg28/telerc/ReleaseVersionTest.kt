package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun numericReleaseOrdering() {
        assertTrue(compareVersion("0.4.10", "0.4.9") > 0)
        assertEquals(0, compareVersion("0.4.1", "0.4.1"))
        assertTrue(compareVersion("0.3.9", "0.4.1") < 0)
        assertTrue(compareVersion("malicious-tag", "0.4.1") < 0)
    }
}
