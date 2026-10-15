package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color

/** Muted surfaces and low-saturation accents; content keeps a readable foreground. */
internal object AppColors {
    fun theme(context: Context): BackgroundTheme {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        return BackgroundTheme.resolve(prefs.getString("background", null), prefs.getBoolean("dark", false))
    }
    fun background(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(232, 235, 234)
        BackgroundTheme.SEPIA -> Color.rgb(229, 222, 209)
        BackgroundTheme.DARK -> Color.rgb(27, 32, 35)
    }
    fun surface(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(240, 242, 241)
        BackgroundTheme.SEPIA -> Color.rgb(238, 232, 221)
        BackgroundTheme.DARK -> Color.rgb(36, 42, 45)
    }
    fun ink(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(48, 57, 61)
        BackgroundTheme.SEPIA -> Color.rgb(62, 55, 45)
        BackgroundTheme.DARK -> Color.rgb(216, 222, 222)
    }
    fun muted(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(91, 102, 107)
        BackgroundTheme.SEPIA -> Color.rgb(105, 94, 76)
        BackgroundTheme.DARK -> Color.rgb(171, 179, 181)
    }
    val names = listOf("Blue", "Teal", "Violet", "Slate")
    fun accent(context: Context): Int {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val dark = theme(context) == BackgroundTheme.DARK
        return when (prefs.getString("accent", "Blue")) {
            "Teal" -> if (dark) Color.rgb(171, 192, 186) else Color.rgb(69, 96, 90)
            "Violet" -> if (dark) Color.rgb(190, 178, 201) else Color.rgb(94, 84, 108)
            "Slate" -> if (dark) Color.rgb(184, 190, 195) else Color.rgb(79, 91, 99)
            else -> if (dark) Color.rgb(168, 184, 193) else Color.rgb(72, 93, 106)
        }
    }
    private fun mix(base: Int, tint: Int, amount: Float): Int {
        fun channel(a: Int, b: Int) = (a * (1 - amount) + b * amount).toInt()
        return Color.rgb(channel(Color.red(base), Color.red(tint)),
            channel(Color.green(base), Color.green(tint)), channel(Color.blue(base), Color.blue(tint)))
    }
    fun buttonFill(context: Context): Int = mix(surface(context), accent(context), .22f)
    fun softAccent(context: Context): Int = mix(surface(context), accent(context), .08f)
    fun outline(context: Context): Int = mix(surface(context), muted(context), .16f)
}
