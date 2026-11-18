package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color

internal object AppColors {
    // UI — Accent palette. Use subdued, high-contrast tones across light/dark themes.
    val names = listOf("Blue", "Teal", "Violet", "Slate")
    fun accent(context: Context): Int {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val dark = prefs.getBoolean("dark", false)
        return when (prefs.getString("accent", "Blue")) {
            "Teal" -> if (dark) Color.rgb(70, 201, 181) else Color.rgb(0, 115, 105)
            "Violet" -> if (dark) Color.rgb(177, 155, 239) else Color.rgb(103, 76, 165)
            "Slate" -> if (dark) Color.rgb(164, 185, 210) else Color.rgb(69, 97, 125)
            else -> if (dark) Color.rgb(92, 171, 245) else Color.rgb(31, 96, 170)
        }
    }
    fun softAccent(context: Context): Int {
        val dark = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", false)
        val base = if (dark) Color.rgb(26, 39, 53) else Color.WHITE
        val color = accent(context)
        val mix = if (dark) .24f else .10f
        fun channel(a: Int, b: Int) = (a * (1 - mix) + b * mix).toInt()
        return Color.rgb(channel(Color.red(base), Color.red(color)), channel(Color.green(base), Color.green(color)), channel(Color.blue(base), Color.blue(color)))
    }
}
