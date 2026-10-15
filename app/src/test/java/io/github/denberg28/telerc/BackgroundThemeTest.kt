package io.github.denberg28.telerc

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundThemeTest {
    @Test fun freshInstallDefaultsToPastel() {
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.resolve(null, false))
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.resolve("unknown", false))
    }

    @Test fun explicitBackgroundOverridesLegacyToggle() {
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.resolve("PASTEL", true))
        assertEquals(BackgroundTheme.SEPIA, BackgroundTheme.resolve("SEPIA", true))
        assertEquals(BackgroundTheme.LIGHT, BackgroundTheme.resolve("LIGHT", true))
        assertEquals(BackgroundTheme.DARK, BackgroundTheme.resolve("DARK", false))
    }

    @Test fun oldDarkAppearanceMovesToPastelOnce() {
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.forUpgrade("DARK", true, false))
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.forUpgrade(null, true, false))
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.forUpgrade("unknown", true, false))
    }

    @Test fun lightSepiaAndLaterDarkChoicesPersist() {
        assertEquals(BackgroundTheme.LIGHT, BackgroundTheme.forUpgrade("LIGHT", true, false))
        assertEquals(BackgroundTheme.SEPIA, BackgroundTheme.forUpgrade("SEPIA", false, false))
        assertEquals(BackgroundTheme.DARK, BackgroundTheme.forUpgrade("DARK", true, true))
        assertEquals(BackgroundTheme.PASTEL, BackgroundTheme.forUpgrade("PASTEL", false, true))
    }
}
