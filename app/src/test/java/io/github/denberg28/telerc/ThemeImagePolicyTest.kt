package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class ThemeImagePolicyTest {
    @Test fun targetBoundsOversizedImagesAndRejectsInvalidDimensions() {
        assertEquals(1280 to 720, ThemeImagePolicy.target(1280, 720))
        assertEquals(1920 to 1080, ThemeImagePolicy.target(3840, 2160))
        assertEquals(1080 to 1920, ThemeImagePolicy.target(2160, 3840))
        assertNull(ThemeImagePolicy.target(0, 100))
        assertNull(ThemeImagePolicy.target(100, -1))
        assertNull(ThemeImagePolicy.target(20000, 20000))
    }

    @Test fun sampleComputesPowerOfTwoDownscale() {
        assertEquals(1, ThemeImagePolicy.sample(1920, 1080))
        assertEquals(2, ThemeImagePolicy.sample(3840, 2160))
        assertEquals(4, ThemeImagePolicy.sample(7680, 4320))
        assertEquals(0, ThemeImagePolicy.sample(0, 0))
    }
}
