package io.github.denberg28.telerc

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

/** Shared tab actions: consistent text, insets, feedback and readable disabled state. */
internal object AppUi {
    fun button(activity:Activity,value:String,filled:Boolean=false,action:()->Unit)=Button(activity).apply {
        fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
        fun shape(color:Int)=GradientDrawable().apply {setColor(color);cornerRadius=dp(10).toFloat()}
        val accent=AppColors.accent(activity)
        val soft=AppColors.softAccent(activity)
        text=value;isAllCaps=false;textSize=14f;includeFontPadding=false;maxLines=2
        minWidth=0;minimumWidth=0;minimumHeight=0
        setPadding(dp(8),0,dp(8),0);stateListAnimator=null;elevation=0f
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),
            intArrayOf(AppColors.muted(activity),if(filled) Color.WHITE else accent)))
        val fill=StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),shape(soft))
            addState(intArrayOf(),shape(if(filled) AppColors.buttonFill(activity) else soft))
        }
        background=RippleDrawable(ColorStateList.valueOf((accent and 0x00ffffff) or 0x24000000),fill,shape(Color.WHITE))
        setOnClickListener {action()}
    }
}
