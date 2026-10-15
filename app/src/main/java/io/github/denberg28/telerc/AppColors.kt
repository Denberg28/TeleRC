package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color

internal object AppColors {
    fun theme(context: Context): BackgroundTheme {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        return BackgroundTheme.resolve(prefs.getString("background", null), prefs.getBoolean("dark", false))
    }
    fun background(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(245, 248, 251)
        BackgroundTheme.SEPIA -> Color.rgb(239, 229, 210)
        BackgroundTheme.DARK -> Color.rgb(12, 20, 28)
    }
    fun surface(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.WHITE
        BackgroundTheme.SEPIA -> Color.rgb(250, 242, 226)
        BackgroundTheme.DARK -> Color.rgb(22, 34, 45)
    }
    fun ink(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(27, 38, 49)
        BackgroundTheme.SEPIA -> Color.rgb(58, 46, 31)
        BackgroundTheme.DARK -> Color.rgb(232, 239, 246)
    }
    fun muted(context: Context): Int = when (theme(context)) {
        BackgroundTheme.LIGHT -> Color.rgb(96, 114, 131)
        BackgroundTheme.SEPIA -> Color.rgb(109, 89, 62)
        BackgroundTheme.DARK -> Color.rgb(164, 181, 196)
    }
    val names = listOf("Blue", "Teal", "Violet", "Slate")
    fun accent(context: Context): Int {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val dark = theme(context) == BackgroundTheme.DARK
        return when (prefs.getString("accent", "Blue")) {
            "Teal" -> if (dark) Color.rgb(45, 192, 177) else Color.rgb(0, 121, 111)
            "Violet" -> if (dark) Color.rgb(170, 142, 245) else Color.rgb(112, 80, 177)
            "Slate" -> if (dark) Color.rgb(153, 177, 200) else Color.rgb(74, 100, 125)
            else -> if (dark) Color.rgb(55, 151, 245) else Color.rgb(0, 112, 218)
        }
    }
    fun softAccent(context: Context): Int {
        val dark = theme(context) == BackgroundTheme.DARK
        val base = surface(context)
        val color = accent(context)
        val mix = if (dark) .22f else .12f
        fun channel(a: Int, b: Int) = (a * (1 - mix) + b * mix).toInt()
        return Color.rgb(channel(Color.red(base), Color.red(color)), channel(Color.green(base), Color.green(color)), channel(Color.blue(base), Color.blue(color)))
    }
}
