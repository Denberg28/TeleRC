package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class FpvPreviewPolicyTest {
    @Test fun standardFpvVideoFitsWidePanelWithoutStretching() {
        assertEquals(400 to 300, FpvPreviewPolicy.fit(600, 300, 640, 480))
    }
    @Test fun widescreenVideoFitsNarrowPanelWithoutCropping() {
        assertEquals(320 to 180, FpvPreviewPolicy.fit(320, 400, 1280, 720))
    }
    @Test fun unavailableOrInvalidDimensionsDoNotProduceALayout() {
        assertNull(FpvPreviewPolicy.fit(0, 300, 640, 480))
        assertNull(FpvPreviewPolicy.fit(300, 300, -1, 480))
    }
    @Test fun staleOrMissingUsbFramesMustNotAppearLive() {
        assertFalse(FpvPreviewPolicy.frameFresh(0, 1000))
        assertTrue(FpvPreviewPolicy.frameFresh(1000, 3000))
        assertFalse(FpvPreviewPolicy.frameFresh(1000, 3001))
    }
    @Test fun backwardsClockDoesNotMakeOldVideoFresh() {
        assertFalse(FpvPreviewPolicy.frameFresh(1000, 999))
    }
}
