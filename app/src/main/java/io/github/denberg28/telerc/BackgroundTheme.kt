package io.github.denberg28.telerc

internal enum class BackgroundTheme(val label: String) {
    LIGHT("Light"), SEPIA("Sepia"), DARK("Dark");

    companion object {
        fun resolve(saved: String?, legacyDark: Boolean): BackgroundTheme =
            entries.firstOrNull { it.name == saved }
                ?: if (legacyDark) DARK else LIGHT
    }
}
