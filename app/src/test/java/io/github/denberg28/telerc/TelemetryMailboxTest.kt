package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class TelemetryMailboxTest {
    @Test fun backlogIsBoundedAndKeepsNewestFrames() {
        val box = TelemetryMailbox()
        box.offer((0..30).map { byteArrayOf(it.toByte()) }, 100)
        assertEquals(15, box.poll(101)!![0].toInt())
        repeat(15) { assertNotNull(box.poll(101)) }
        assertNull(box.poll(101))
    }
    @Test fun expiredTelemetryCannotBecomeFreshAfterStall() {
        val box = TelemetryMailbox()
        box.offer(listOf(byteArrayOf(1)), 100)
        assertNull(box.poll(351))
        box.offer(listOf(byteArrayOf(2)), 352)
        assertEquals(2, box.poll(352)!![0].toInt())
    }
    @Test fun backwardsClockAndBoundary() {
        val box = TelemetryMailbox()
        box.offer(listOf(byteArrayOf(1)), 100)
        assertNotNull(box.poll(350))
        box.offer(listOf(byteArrayOf(2)), 100)
        assertNull(box.poll(99))
    }
}
