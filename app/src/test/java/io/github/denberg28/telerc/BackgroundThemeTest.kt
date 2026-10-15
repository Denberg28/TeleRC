package io.github.denberg28.telerc

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundThemeTest {
    @Test fun existingAppearanceSurvivesUpgrade() {
        assertEquals(BackgroundTheme.LIGHT, BackgroundTheme.resolve(null, false))
        assertEquals(BackgroundTheme.DARK, BackgroundTheme.resolve(null, true))
        assertEquals(BackgroundTheme.DARK, BackgroundTheme.resolve("unknown", true))
    }

    @Test fun explicitBackgroundOverridesLegacyToggle() {
        assertEquals(BackgroundTheme.SEPIA, BackgroundTheme.resolve("SEPIA", true))
        assertEquals(BackgroundTheme.LIGHT, BackgroundTheme.resolve("LIGHT", true))
        assertEquals(BackgroundTheme.DARK, BackgroundTheme.resolve("DARK", false))
    }
}
