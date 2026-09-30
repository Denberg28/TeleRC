package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color

internal object AppColors {
    val names = listOf("Blue", "Teal", "Violet", "Slate")
    fun accent(context: Context): Int {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val dark = prefs.getBoolean("dark", false)
        return when (prefs.getString("accent", "Blue")) {
            "Teal" -> if (dark) Color.rgb(45, 192, 177) else Color.rgb(0, 121, 111)
            "Violet" -> if (dark) Color.rgb(170, 142, 245) else Color.rgb(112, 80, 177)
            "Slate" -> if (dark) Color.rgb(153, 177, 200) else Color.rgb(74, 100, 125)
            else -> if (dark) Color.rgb(55, 151, 245) else Color.rgb(0, 112, 218)
        }
    }
    fun softAccent(context: Context): Int {
        val dark = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", false)
        val base = if (dark) Color.rgb(22, 34, 45) else Color.WHITE
        val color = accent(context)
        val mix = if (dark) .22f else .12f
        fun channel(a: Int, b: Int) = (a * (1 - mix) + b * mix).toInt()
        return Color.rgb(channel(Color.red(base), Color.red(color)), channel(Color.green(base), Color.green(color)), channel(Color.blue(base), Color.blue(color)))
    }
}
