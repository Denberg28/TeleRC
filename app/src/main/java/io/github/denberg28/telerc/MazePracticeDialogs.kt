package io.github.denberg28.telerc

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Sized within the visible window: scrolling content, fixed header/actions, shared spacing. */
internal class MazePracticeDialogs(private val activity:Activity) {
    private val ink=AppColors.ink(activity)
    private val muted=AppColors.muted(activity)
    private val accent=AppColors.accent(activity)
    private val surface=AppColors.surface(activity)
    private val soft=AppColors.softAccent(activity)
    private fun dp(value:Int)=(value*activity.resources.displayMetrics.density+.5f).toInt()
    private fun shape(color:Int,radius:Int=12,stroke:Boolean=false)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
        if(stroke) setStroke(dp(1),accent)
    }
    private fun label(value:String,size:Float,color:Int=ink,bold:Boolean=false)=TextView(activity).apply {
        text=value; textSize=size; setTextColor(color)
        if(bold) setTypeface(typeface,android.graphics.Typeface.BOLD)
    }
    private fun action(value:String,primary:Boolean,clicked:()->Unit)=Button(activity).apply {
        text=value; textSize=14f; isAllCaps=false
        minimumWidth=0; minimumHeight=dp(48); setPadding(dp(16),dp(10),dp(16),dp(10))
        setTextColor(if(primary) Color.WHITE else ink)
        background=shape(if(primary) AppColors.buttonFill(activity) else soft)
        elevation=0f; stateListAnimator=null
        setOnClickListener { clicked() }
    }
    private class Sheet(val dialog:Dialog,val body:LinearLayout,val footer:LinearLayout)
    private fun sheet(title:String,subtitle:String,badge:String,width:Int,height:Int,cancelable:Boolean):Sheet {
        val dialog=Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(cancelable); setCanceledOnTouchOutside(cancelable)
        }
        val root=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(20),dp(20),dp(20))
            background=shape(surface,20)
        }
        val heading=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        heading.addView(label(title,20f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(label(badge,12f,accent,true).apply {
            gravity=Gravity.CENTER; setPadding(dp(10),dp(6),dp(10),dp(6)); background=shape(soft,10)
        },LinearLayout.LayoutParams(-2,-2).apply { leftMargin=dp(12) })
        root.addView(heading,LinearLayout.LayoutParams(-1,-2))
        root.addView(label(subtitle,12f,muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(activity).apply { isFillViewport=false; addView(body) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(16) })
        val footer=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
        root.addView(footer,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(16) })
        dialog.setContentView(root)
        // Add actions before first measure, so the scrolling region yields space to them.
        dialog.setOnShowListener {
            val visible=Rect()
            activity.window.decorView.getWindowVisibleDisplayFrame(visible)
            val metrics=activity.resources.displayMetrics
            val availableWidth=if(visible.width()>0) visible.width() else metrics.widthPixels
            val availableHeight=if(visible.height()>0) visible.height() else metrics.heightPixels
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(.62f)
                setLayout(minOf(dp(width),(availableWidth-dp(40)).coerceAtLeast(1)),
                    minOf(dp(height),(availableHeight-dp(32)).coerceAtLeast(1)))
            }
        }
        return Sheet(dialog,body,footer)
    }
    fun levels(progress:MazeProgress,records:List<MazeClear>,selected:(Int)->Unit):Dialog {
        val sheet=sheet("Levels","${progress.unlocked} levels unlocked · Select a level",
            "${progress.unlocked} / ${MazeLevels.COUNT}",520,340,true)
        for(level in 1..progress.unlocked) {
            val current=level==progress.current
            val best=records.filter { it.level==level && it.courseVersion==MazeLayout.COURSE_VERSION }.minOfOrNull { it.millis }
            val state=when { current -> "Current"; level<=progress.completed -> "Cleared"; else -> "Play" }
            val row=LinearLayout(activity).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
                minimumHeight=dp(68); setPadding(dp(12),dp(12),dp(12),dp(12))
                background=shape(if(current) soft else surface,12,true)
                isClickable=true; isFocusable=true
                contentDescription="Level $level, $state, best ${best?.let(MazeRecords::time) ?: "not set"}"
                setOnClickListener { sheet.dialog.dismiss(); selected(level) }
            }
            row.addView(label(java.lang.String.format(java.util.Locale.US,"%02d",level),16f,Color.WHITE,true).apply {
                gravity=Gravity.CENTER; background=shape(AppColors.buttonFill(activity),10)
            },LinearLayout.LayoutParams(dp(44),dp(44)))
            val detail=LinearLayout(activity).apply {
                orientation=LinearLayout.VERTICAL
                addView(label("Level $level",15f,ink,true))
                addView(label("Best ${best?.let(MazeRecords::time) ?: "—"}",12f,muted),
                    LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4) })
            }
            row.addView(detail,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(12); rightMargin=dp(12) })
            row.addView(label(state,12f,if(current) accent else muted,current),LinearLayout.LayoutParams(-2,-2))
            sheet.body.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        sheet.footer.addView(action("Done",false) { sheet.dialog.dismiss() },LinearLayout.LayoutParams(-1,-2))
        sheet.dialog.show()
        return sheet.dialog
    }
    fun restart(level:Int,millis:Long,bumps:Int,restart:()->Unit):Dialog {
        val sheet=sheet("Restart level $level?","Wall-bump limit reached","$bumps / ${MazePractice.BUMP_LIMIT}",460,310,false)
        val stats=LinearLayout(activity).apply {
            setPadding(dp(16),dp(12),dp(16),dp(12)); background=shape(soft)
        }
        fun stat(title:String,value:String):LinearLayout = LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            addView(label(title,11f,muted,true))
            addView(label(value,20f,ink,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4) })
        }
        stats.addView(stat("TIME",MazeRecords.time(millis)),LinearLayout.LayoutParams(0,-2,1f))
        stats.addView(stat("BUMPS","$bumps / ${MazePractice.BUMP_LIMIT}"),LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(16) })
        sheet.body.addView(stats,LinearLayout.LayoutParams(-1,-2))
        sheet.body.addView(label("Return to START with a fresh timer and no wall marks.",14f),
            LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
        sheet.body.addView(label("Collected coins stay in your wallet.",12f,muted),
            LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        sheet.footer.addView(action("Restart level",true) { sheet.dialog.dismiss(); restart() },LinearLayout.LayoutParams(-1,-2))
        sheet.dialog.show()
        return sheet.dialog
    }
}
