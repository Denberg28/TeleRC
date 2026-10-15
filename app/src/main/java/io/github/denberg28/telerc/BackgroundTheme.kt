package io.github.denberg28.telerc

internal enum class BackgroundTheme(val label: String) {
    PASTEL("Pastel"), LIGHT("Light"), SEPIA("Sepia"), DARK("Dark");

    companion object {
        fun resolve(saved: String?, legacyDark: Boolean): BackgroundTheme =
            entries.firstOrNull { it.name == saved }
                ?: if (legacyDark) DARK else PASTEL

        /** Switch the old dark default once; later explicit Dark choices persist. */
        fun forUpgrade(saved: String?, legacyDark: Boolean, migrated: Boolean): BackgroundTheme {
            val current = resolve(saved, legacyDark)
            return if (!migrated && current == DARK) PASTEL else current
        }
    }
}
