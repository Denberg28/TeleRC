package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color

/** Original pre-compact palette, with slightly deeper surfaces and accents. */
internal object AppColors {
    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        if (prefs.getString("background", null) == "PASTEL") {
            prefs.edit().putString("background", BackgroundTheme.LIGHT.name)
                .putBoolean("dark", false).apply()
        }
    }
    fun theme(context: Context): BackgroundTheme {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        return BackgroundTheme.resolve(prefs.getString("background", null), prefs.getBoolean("dark", false))
    }
    fun background(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(234, 238, 242)
        BackgroundTheme.SEPIA -> Color.rgb(231, 222, 204)
        BackgroundTheme.DARK -> Color.rgb(11, 19, 27)
    }
    fun surface(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(247, 248, 250)
        BackgroundTheme.SEPIA -> Color.rgb(242, 234, 220)
        BackgroundTheme.DARK -> Color.rgb(21, 32, 43)
    }
    fun ink(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(27, 38, 49)
        BackgroundTheme.SEPIA -> Color.rgb(58, 46, 31)
        BackgroundTheme.DARK -> Color.rgb(232, 239, 246)
    }
    fun muted(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(84, 102, 119)
        BackgroundTheme.SEPIA -> Color.rgb(102, 83, 58)
        BackgroundTheme.DARK -> Color.rgb(164, 181, 196)
    }
    val names = listOf("Blue", "Teal", "Violet", "Slate")
    private fun accentFor(context: Context, dark: Boolean): Int = when (
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("accent", "Blue")) {
        "Teal" -> if (dark) Color.rgb(45, 192, 177) else Color.rgb(0, 105, 96)
        "Violet" -> if (dark) Color.rgb(170, 142, 245) else Color.rgb(101, 72, 159)
        "Slate" -> if (dark) Color.rgb(153, 177, 200) else Color.rgb(67, 90, 113)
        else -> if (dark) Color.rgb(55, 151, 245) else Color.rgb(0, 93, 180)
    }
    fun accent(context: Context): Int = accentFor(context, theme(context) == BackgroundTheme.DARK)
    private fun mix(base: Int, tint: Int, amount: Float): Int {
        fun channel(a: Int, b: Int) = (a * (1 - amount) + b * amount).toInt()
        return Color.rgb(channel(Color.red(base), Color.red(tint)),
            channel(Color.green(base), Color.green(tint)), channel(Color.blue(base), Color.blue(tint)))
    }
    // Original strong action fill + white label; dark-mode fills also keep readable white text.
    fun buttonFill(context: Context): Int = accentFor(context, false)
    fun softAccent(context: Context): Int = mix(surface(context), accent(context),
        .12f)
    fun outline(context: Context): Int = mix(surface(context), muted(context), .16f)
}
