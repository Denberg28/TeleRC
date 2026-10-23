package io.github.denberg28.telerc

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** The same flat, centered, bounded sheet contract as the Test tab. */
internal class AppDialog private constructor(private val activity:Activity):Dialog(activity) {
    private val actions=mutableMapOf<Int,Button>()
    fun getButton(which:Int):Button=requireNotNull(actions[which])
    override fun show() {
        if(activity.isFinishing || activity.isDestroyed) return
        (activity as? MainActivity)?.prepareForDialog()
        open.getOrPut(activity) { mutableListOf() }.add(WeakReference(this))
        super.show()
    }
    override fun dismiss() {
        open[activity]?.removeAll { it.get()==null || it.get()===this }
        super.dismiss()
    }
    companion object {
        fun sheet(activity:Activity)=AppDialog(activity)
        private val open=WeakHashMap<Activity,MutableList<WeakReference<AppDialog>>>()
        fun dismissAll(activity:Activity) {
            open.remove(activity)?.mapNotNull { it.get() }?.forEach { it.cancel() }
        }
    }
    class Builder(private val activity:Activity) {
        private var title=""
        private var message:String?=null
        private var custom:View?=null
        private var items:Array<out String>?=null
        private var selected=-1
        private var single=false
        private var choice:((DialogInterface,Int)->Unit)?=null
        private val buttons=linkedMapOf<Int,Pair<String,((DialogInterface,Int)->Unit)?>>()
        private var cancelable=true
        private var cancelled:((DialogInterface)->Unit)?=null
        fun setTitle(value:String)=apply { title=value }
        fun setMessage(value:String)=apply { message=value }
        fun setView(value:View)=apply { custom=value }
        fun setCancelable(value:Boolean)=apply { cancelable=value }
        fun setOnCancelListener(action:((DialogInterface)->Unit)?)=apply { cancelled=action }
        fun setItems(values:Array<out String>,action:(DialogInterface,Int)->Unit)=apply { items=values; choice=action; single=false }
        fun setSingleChoiceItems(values:Array<out String>,checked:Int,action:(DialogInterface,Int)->Unit)=apply {
            items=values; selected=checked; choice=action; single=true
        }
        fun setPositiveButton(value:String,action:((DialogInterface,Int)->Unit)?)=apply { buttons[-1]=value to action }
        fun setNegativeButton(value:String,action:((DialogInterface,Int)->Unit)?)=apply { buttons[-2]=value to action }
        fun setNeutralButton(value:String,action:((DialogInterface,Int)->Unit)?)=apply { buttons[-3]=value to action }
        fun show()=create().also { it.show() }
        fun create():AppDialog {
            val dialog=AppDialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(cancelable); dialog.setCanceledOnTouchOutside(cancelable)
            dialog.setOnCancelListener { cancelled?.invoke(it) }
            val density=activity.resources.displayMetrics.density
            fun dp(n:Int)=(n*density+.5f).toInt()
            val rowHeight=PopupLayout.rowHeight(activity)
            val ink=AppColors.ink(activity); val accent=AppColors.accent(activity)
            val soft=AppColors.softAccent(activity)
            fun shape(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
            fun label(value:String,size:Float=14f)=TextView(activity).apply {
                text=value; textSize=size; setTextColor(ink); includeFontPadding=false
            }
            fun divider(parent:LinearLayout) { parent.addView(View(activity).apply {
                setBackgroundColor(ink); alpha=.12f
            },LinearLayout.LayoutParams(-1,dp(1))) }
            val root=LinearLayout(activity).apply {
                orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8))
                background=shape(AppColors.surface(activity),8); contentDescription="Standard popup"
            }
            val heading=label(title,16f).apply {
                setTypeface(typeface,android.graphics.Typeface.BOLD); gravity=Gravity.CENTER
                minHeight=rowHeight; maxLines=2
            }
            root.addView(heading,LinearLayout.LayoutParams(-1,-2))
            divider(root)
            val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(8),0,dp(8)) }
            message?.let { body.addView(label(it),LinearLayout.LayoutParams(-1,-2)) }
            items?.forEachIndexed { index,value ->
                val row=LinearLayout(activity).apply {
                    gravity=Gravity.CENTER_VERTICAL; minimumHeight=rowHeight; setPadding(dp(4),dp(8),dp(4),dp(8))
                    background=RippleDrawable(ColorStateList.valueOf(soft),ColorDrawable(Color.TRANSPARENT),null)
                    isClickable=true; isFocusable=true
                    setOnClickListener {
                        if(!single) dialog.dismiss()
                        choice?.invoke(dialog,index)
                    }
                }
                if(single) row.addView(label(if(index==selected) "✓" else "",14f).apply { setTextColor(accent) },LinearLayout.LayoutParams(dp(28),-2))
                row.addView(label(value),LinearLayout.LayoutParams(0,-2,1f))
                body.addView(row,LinearLayout.LayoutParams(-1,-2)); if(index<items!!.lastIndex) divider(body)
            }
            custom?.let { supplied ->
                // One scroll owner; legacy callers may already wrap their form.
                val content=if(supplied is ScrollView && supplied.childCount==1) supplied.getChildAt(0).also { supplied.removeView(it) } else supplied
                fun themeForm(view:View) {
                    if(view is android.widget.EditText) {
                        view.setTextColor(ink); view.setHintTextColor(AppColors.muted(activity)); view.textSize=14f
                        view.minimumHeight=rowHeight; view.backgroundTintList=ColorStateList.valueOf(accent)
                    }
                    if(view is android.widget.SeekBar) {
                        view.progressTintList=ColorStateList.valueOf(accent); view.thumbTintList=ColorStateList.valueOf(accent)
                    }
                    if(view is ViewGroup) for(index in 0 until view.childCount) themeForm(view.getChildAt(index))
                }
                themeForm(content)
                (content.parent as? ViewGroup)?.removeView(content)
                body.addView(content,LinearLayout.LayoutParams(-1,-2))
            }
            val scroll=ScrollView(activity).apply { addView(body); isFillViewport=false; contentDescription="Popup content" }
            root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
            if(buttons.isNotEmpty()) {
                divider(root)
                val footer=LinearLayout(activity).apply { gravity=Gravity.END or Gravity.CENTER_VERTICAL }
                for(which in listOf(-3,-2,-1)) buttons[which]?.let { (caption,clicked) ->
                    val button=Button(activity).apply {
                        text=caption; textSize=13f; isAllCaps=false; includeFontPadding=false; maxLines=2
                        minimumWidth=0; minimumHeight=rowHeight; setPadding(dp(8),dp(6),dp(8),dp(6))
                        setTextColor(accent); elevation=0f; stateListAnimator=null
                        background=RippleDrawable(ColorStateList.valueOf(soft),ColorDrawable(Color.TRANSPARENT),shape(Color.WHITE,4))
                        setOnClickListener { dialog.dismiss(); clicked?.invoke(dialog,which) }
                    }
                    dialog.actions[which]=button
                    footer.addView(button,LinearLayout.LayoutParams(0,rowHeight,1f))
                }
                root.addView(footer,LinearLayout.LayoutParams(-1,-2))
            }
            dialog.setContentView(root)
            val width=when {
                custom!=null -> 420
                items!=null -> 420
                message.orEmpty().length>240 -> 420
                else -> 360
            }
            PopupLayout.configure(activity,dialog,root,scroll,width)
            return dialog
        }
    }
}
