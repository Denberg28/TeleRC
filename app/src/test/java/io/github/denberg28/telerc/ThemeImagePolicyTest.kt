package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class ThemeImagePolicyTest {
    @Test fun largeLandscapePortraitAndPanoramasRemainBoundedWithoutUpscaling() {
        assertEquals(1920 to 1080, ThemeImagePolicy.target(7680,4320))
        assertEquals(1080 to 1920, ThemeImagePolicy.target(4320,7680))
        assertEquals(1920 to 1, ThemeImagePolicy.target(100000,1))
        assertEquals(600 to 400, ThemeImagePolicy.target(600,400))
        assertEquals(4,ThemeImagePolicy.sample(7680,4320))
        assertEquals(4,ThemeImagePolicy.sample(3841,2160))
    }
    @Test fun invalidBoundsAreRejectedBeforeAllocating() {
        assertNull(ThemeImagePolicy.target(-1,480)); assertNull(ThemeImagePolicy.target(0,0))
        assertNull(ThemeImagePolicy.target(100000,100000)); assertNull(ThemeImagePolicy.target(Int.MAX_VALUE,200)); assertEquals(0,ThemeImagePolicy.sample(-1,300))
    }
}
