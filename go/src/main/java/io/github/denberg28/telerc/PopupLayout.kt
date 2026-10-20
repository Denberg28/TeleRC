package io.github.denberg28.telerc

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

/** Measure once before show: fixed chrome, a bounded scrolling body, no opening jump. */
internal object PopupLayout {
    fun rowHeight(activity:Activity)=dp(activity,(48*maxOf(1f,activity.resources.configuration.fontScale)).toInt())
    private fun dp(activity:Activity,value:Int)=(value*activity.resources.displayMetrics.density+.5f).toInt()

    fun configure(activity:Activity,dialog:Dialog,root:LinearLayout,scroll:ScrollView,preferredWidth:Int) {
        val visible=Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
        val metrics=activity.resources.displayMetrics
        val availableWidth=if(visible.width()>0) visible.width() else metrics.widthPixels
        val availableHeight=if(visible.height()>0) visible.height() else metrics.heightPixels
        val width=minOf(dp(activity,preferredWidth),(availableWidth-dp(activity,32)).coerceAtLeast(1))
        val cap=minOf(dp(activity,340),(availableHeight-dp(activity,24)).coerceAtLeast(1))
        // Temporarily measure the body at its real wrapping width. The live layout
        // restores weight so only the body scrolls when content exceeds the cap.
        val params=scroll.layoutParams as LinearLayout.LayoutParams
        val oldHeight=params.height; val oldWeight=params.weight
        params.height=LinearLayout.LayoutParams.WRAP_CONTENT; params.weight=0f
        try {
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        } finally { params.height=oldHeight; params.weight=oldWeight }
        val height=root.measuredHeight.coerceIn(1,cap)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setDimAmount(.48f)
            setGravity(Gravity.CENTER)
            attributes=attributes.apply { x=0; y=0; windowAnimations=0 }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setLayout(width,height)
        }
    }
}
