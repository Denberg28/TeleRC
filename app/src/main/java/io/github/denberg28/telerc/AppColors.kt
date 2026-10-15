package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color

/** Pastel fills carry the color; readable slate foregrounds carry the content. */
internal object AppColors {
    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        if (prefs.getBoolean("pastel_theme_v1", false)) return
        val selected = BackgroundTheme.forUpgrade(
            prefs.getString("background", null), prefs.getBoolean("dark", false), false)
        prefs.edit().putString("background", selected.name)
            .putBoolean("dark", selected == BackgroundTheme.DARK)
            .putBoolean("pastel_theme_v1", true).apply()
    }
    fun theme(context: Context): BackgroundTheme {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        return BackgroundTheme.resolve(prefs.getString("background", null), prefs.getBoolean("dark", false))
    }
    private fun tint(context: Context): Int = when (
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("accent", "Blue")) {
        "Teal" -> Color.rgb(214, 235, 226)
        "Violet" -> Color.rgb(229, 223, 243)
        "Slate" -> Color.rgb(223, 229, 237)
        else -> Color.rgb(215, 229, 246)
    }
    fun background(context: Context): Int = when (theme(context)) {
        BackgroundTheme.PASTEL -> mix(Color.rgb(245, 247, 251), tint(context), .42f)
        BackgroundTheme.LIGHT -> Color.rgb(244, 246, 248)
        BackgroundTheme.SEPIA -> Color.rgb(246, 239, 223)
        BackgroundTheme.DARK -> Color.rgb(27, 32, 35)
    }
    fun surface(context: Context): Int = when (theme(context)) {
        BackgroundTheme.PASTEL -> mix(Color.rgb(253, 254, 255), tint(context), .24f)
        BackgroundTheme.LIGHT -> Color.rgb(252, 253, 255)
        BackgroundTheme.SEPIA -> Color.rgb(255, 249, 238)
        BackgroundTheme.DARK -> Color.rgb(36, 42, 45)
    }
    fun ink(context: Context): Int = when (theme(context)) {
        BackgroundTheme.PASTEL, BackgroundTheme.LIGHT -> Color.rgb(52, 66, 86)
        BackgroundTheme.SEPIA -> Color.rgb(71, 60, 45)
        BackgroundTheme.DARK -> Color.rgb(216, 222, 222)
    }
    fun muted(context: Context): Int = when (theme(context)) {
        BackgroundTheme.PASTEL, BackgroundTheme.LIGHT -> Color.rgb(88, 100, 117)
        BackgroundTheme.SEPIA -> Color.rgb(109, 95, 75)
        BackgroundTheme.DARK -> Color.rgb(171, 179, 181)
    }
    val names = listOf("Blue", "Teal", "Violet", "Slate")
    fun accent(context: Context): Int {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val dark = theme(context) == BackgroundTheme.DARK
        return when (prefs.getString("accent", "Blue")) {
            "Teal" -> if (dark) Color.rgb(171, 192, 186) else Color.rgb(69, 110, 101)
            "Violet" -> if (dark) Color.rgb(190, 178, 201) else Color.rgb(106, 93, 129)
            "Slate" -> if (dark) Color.rgb(184, 190, 195) else Color.rgb(83, 100, 120)
            else -> if (dark) Color.rgb(168, 184, 193) else Color.rgb(75, 102, 134)
        }
    }
    private fun mix(base: Int, tint: Int, amount: Float): Int {
        fun channel(a: Int, b: Int) = (a * (1 - amount) + b * amount).toInt()
        return Color.rgb(channel(Color.red(base), Color.red(tint)),
            channel(Color.green(base), Color.green(tint)), channel(Color.blue(base), Color.blue(tint)))
    }
    fun buttonFill(context: Context): Int = when (theme(context)) {
        BackgroundTheme.PASTEL -> tint(context)
        BackgroundTheme.LIGHT, BackgroundTheme.SEPIA -> mix(surface(context), tint(context), .85f)
        BackgroundTheme.DARK -> mix(surface(context), accent(context), .22f)
    }
    fun softAccent(context: Context): Int = when (theme(context)) {
        BackgroundTheme.PASTEL, BackgroundTheme.LIGHT, BackgroundTheme.SEPIA -> mix(surface(context), tint(context), .48f)
        BackgroundTheme.DARK -> mix(surface(context), accent(context), .08f)
    }
    fun outline(context: Context): Int = mix(surface(context), muted(context), .20f)
}
