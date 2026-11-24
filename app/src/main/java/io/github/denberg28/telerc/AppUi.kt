package io.github.denberg28.telerc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

// UI — Shared action buttons. Consistent insets, ripple feedback and readable disabled state.
internal object AppUi {
    fun button(context: Context, value: String, filled: Boolean = false, action: () -> Unit) = Button(context).apply {
        fun dp(n: Int) = (n * context.resources.displayMetrics.density + .5f).toInt()
        fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
        val accent = AppColors.accent(context)
        val soft = AppColors.softAccent(context)
        text = value; isAllCaps = false; textSize = 14f; includeFontPadding = false; maxLines = 2
        minWidth = 0; minimumWidth = 0; minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0); stateListAnimator = null; elevation = 0f
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(AppColors.muted(context), if (filled) Color.WHITE else accent)
            )
        )
        val fill = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(soft))
            addState(intArrayOf(), shape(if (filled) AppColors.buttonFill(context) else soft))
        }
        background = RippleDrawable(ColorStateList.valueOf((accent and 0x00ffffff) or 0x24000000), fill, shape(Color.WHITE))
        setOnClickListener { action() }
    }
}
