package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class FpvStreamPolicyTest {
    private fun p(type: Int, w: Int = 640, h: Int = 480, fps: Int = 30) = FpvStreamPolicy.Profile(type, w, h, fps)
    @Test fun autoPrefersAdvertisedMjpegAndVgaWithAReasonableRate() {
        val modes = listOf(p(5), p(7, 1280, 720), p(7, fps = 60), p(7, fps = 25))
        assertEquals(p(7, fps = 25), FpvStreamPolicy.profiles(modes, FpvStreamPolicy.Mode.AUTO).first())
    }
    @Test fun compatibilityModeCannotFabricateUnsupportedYuyv() {
        assertTrue(FpvStreamPolicy.profiles(listOf(p(7)), FpvStreamPolicy.Mode.YUYV).isEmpty())
        assertEquals(listOf(p(5, 720, 576, 25)), FpvStreamPolicy.profiles(listOf(p(7), p(5,720,576,25)), FpvStreamPolicy.Mode.YUYV))
    }
    @Test fun malformedDescriptorsAndDuplicatesAreExcluded() {
        assertEquals(listOf(p(7)), FpvStreamPolicy.profiles(listOf(p(99),p(7,0),p(7,fps=0),p(7),p(7)), FpvStreamPolicy.Mode.AUTO))
    }
    @Test fun recoveryBudgetIsFiniteAndDoesNotSilentlyReset() {
        assertEquals(listOf(1000L,2000L,4000L), (0..2).map { FpvStreamPolicy.retryDelay(it) })
        assertNull(FpvStreamPolicy.retryDelay(3)); assertNull(FpvStreamPolicy.retryDelay(100))
    }
}
