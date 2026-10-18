package io.github.denberg28.telerc

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Flat practice popups: one header, quiet dividers and 48 dp touch targets. */
internal class MazePracticeDialogs(private val activity:Activity) {
    private val ink=AppColors.ink(activity)
    private val muted=AppColors.muted(activity)
    private val accent=AppColors.accent(activity)
    private val surface=AppColors.surface(activity)
    private val soft=AppColors.softAccent(activity)
    private fun dp(value:Int)=(value*activity.resources.displayMetrics.density+.5f).toInt()
    private fun shape(color:Int,radius:Int=8)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
    }
    private fun label(value:String,size:Float=14f,color:Int=ink,bold:Boolean=false)=TextView(activity).apply {
        text=value; textSize=size; setTextColor(color); includeFontPadding=false
        if(bold) setTypeface(typeface,android.graphics.Typeface.BOLD)
    }
    private val rowHeight get()=PopupLayout.rowHeight(activity)
    private fun action(value:String,clicked:()->Unit)=Button(activity).apply {
        text=value; textSize=13f; isAllCaps=false; includeFontPadding=false
        minimumWidth=dp(64); minimumHeight=rowHeight; setPadding(dp(12),dp(6),dp(12),dp(6))
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(muted,accent)))
        background=RippleDrawable(ColorStateList.valueOf(soft),ColorDrawable(Color.TRANSPARENT),shape(Color.WHITE,4))
        elevation=0f; stateListAnimator=null
        setOnClickListener { clicked() }
    }
    private fun divider(parent:LinearLayout) {
        parent.addView(View(activity).apply { setBackgroundColor(ink); alpha=.12f },LinearLayout.LayoutParams(-1,dp(1)))
    }
    private inner class Sheet(val dialog:Dialog,val root:LinearLayout,val header:LinearLayout,val body:LinearLayout,val scroll:ScrollView,val width:Int) {
        fun show() {
            PopupLayout.configure(activity,dialog,root,scroll,width)
            dialog.show()
        }
    }
    private fun sheet(title:String,width:Int,cancelable:Boolean=true):Sheet {
        val dialog=AppDialog.sheet(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(cancelable); setCanceledOnTouchOutside(cancelable)
        }
        val root=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8))
            background=shape(surface); contentDescription="Standard popup"
        }
        val header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(label(title,16f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header,LinearLayout.LayoutParams(-1,rowHeight))
        divider(root)
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(activity).apply { isFillViewport=false; addView(body); contentDescription="Popup content" }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root)
        return Sheet(dialog,root,header,body,scroll,width)
    }
    private fun close(sheet:Sheet) {
        sheet.header.addView(action("Done") { sheet.dialog.dismiss() },LinearLayout.LayoutParams(-2,rowHeight))
    }
    fun levels(progress:MazeProgress,records:List<MazeClear>,selected:(Int)->Unit):Dialog {
        val sheet=sheet("Levels",420)
        sheet.header.addView(label("${progress.unlocked}/${MazeLevels.COUNT}",12f,muted))
        close(sheet)
        for(level in 1..progress.unlocked) {
            val current=level==progress.current
            val best=records.filter { it.level==level && it.courseVersion==MazeLayout.COURSE_VERSION }.minOfOrNull { it.millis }
            val state=when { current -> "Current"; level<=progress.completed -> "Cleared"; else -> "Play" }
            val row=LinearLayout(activity).apply {
                gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(52)
                setPadding(dp(4),dp(6),dp(4),dp(6))
                background=RippleDrawable(ColorStateList.valueOf(soft),ColorDrawable(Color.TRANSPARENT),null)
                isClickable=true; isFocusable=true
                contentDescription="Level $level, ${MazeEnvironment.forLevel(level).label}, $state, best ${best?.let(MazeRecords::time) ?: "not set"}"
                setOnClickListener { sheet.dialog.dismiss(); selected(level) }
            }
            row.addView(label(if(current) "•" else if(level<=progress.completed) "✓" else "",14f,accent),LinearLayout.LayoutParams(dp(24),-2))
            row.addView(label("Level $level · ${MazeEnvironment.forLevel(level).label}",14f,if(current) accent else ink,current),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(label(best?.let(MazeRecords::time) ?: "—",12f,muted),LinearLayout.LayoutParams(-2,-2))
            sheet.body.addView(row,LinearLayout.LayoutParams(-1,-2))
            if(level<progress.unlocked) divider(sheet.body)
        }
        sheet.show()
        return sheet.dialog
    }
    fun restart(level:Int,millis:Long,bumps:Int,restart:()->Unit):Dialog {
        val sheet=sheet("Restart level $level?",360,false)
        sheet.body.addView(label("$bumps bumps · ${MazeRecords.time(millis)}",13f,muted),
            LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
        sheet.body.addView(label("Coins kept. Fresh start.",13f,muted),
            LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        val footer=LinearLayout(activity).apply { gravity=Gravity.END }
        footer.addView(action("Restart level") { sheet.dialog.dismiss(); restart() },LinearLayout.LayoutParams(-2,rowHeight))
        sheet.root.addView(footer,LinearLayout.LayoutParams(-1,rowHeight))
        sheet.show()
        return sheet.dialog
    }
    fun market(wallet:()->MazeWallet,changed:(MazeWallet)->Unit):Dialog {
        val sheet=sheet("Market",440)
        val balance=label("",12f,muted)
        sheet.header.addView(balance); close(sheet)
        val tabs=LinearLayout(activity)
        sheet.root.addView(tabs,2,LinearLayout.LayoutParams(-1,rowHeight))
        val controls=mutableListOf<Pair<MazeMarketItem,Button>>()
        val tabButtons=mutableListOf<Pair<MazeItemKind,Button>>()
        fun refresh() {
            val current=wallet()
            balance.text="${current.balance} coins"
            for((item,button) in controls) {
                val equipped=current.equipped(item.id)
                val removable=equipped && item.kind==MazeItemKind.ACCESSORY
                val owned=item.id in current.owned
                button.text=when { removable -> "Remove"; equipped -> "Selected"; owned -> "Use"; else -> "Buy · ${item.price}" }
                button.contentDescription=when { removable -> "Remove ${item.name}"; equipped -> "${item.name} · Equipped"; owned -> "Use ${item.name}"; else -> "Buy ${item.name} · ${item.price}" }
                button.isEnabled=removable || !equipped && (owned || current.balance>=item.price)
            }
        }
        fun category(kind:MazeItemKind) {
            for((tab,button) in tabButtons) {
                button.isSelected=tab==kind
                button.setTextColor(if(tab==kind) accent else muted)
                button.setTypeface(null,if(tab==kind) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                // A quiet underline replaces filled tab pills.
                button.background=GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                }.let { base -> android.graphics.drawable.LayerDrawable(arrayOf(base,ColorDrawable(if(tab==kind) accent else Color.TRANSPARENT))).apply {
                    setLayerHeight(1,dp(2)); setLayerGravity(1,Gravity.BOTTOM)
                } }
            }
            controls.clear(); sheet.body.removeAllViews()
            var group:MazeVehicleClass?=null
            for(item in MazeMarket.items.filter { it.kind==kind }) {
                if(kind==MazeItemKind.VEHICLE && group!=item.vehicleClass) {
                    group=item.vehicleClass
                    sheet.body.addView(label(item.vehicleClass.label,12f,muted,true),LinearLayout.LayoutParams(-1,-2).apply {
                        topMargin=dp(10); bottomMargin=dp(4)
                    })
                }
                val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(56) }
                row.addView(MazeMarketPreview(activity,item),LinearLayout.LayoutParams(dp(44),dp(44)).apply { rightMargin=dp(10) })
                row.addView(label(item.name),LinearLayout.LayoutParams(0,-2,1f))
                val buy=action("") {
                    val current=wallet()
                    val next=if(item.kind==MazeItemKind.ACCESSORY && item.id in current.accessories) current.remove(item.id)
                        else if(item.id in current.owned) current.equip(item.id) else current.buy(item.id)?.equip(item.id)
                    if(next!=null) changed(next)
                    refresh()
                }
                row.addView(buy,LinearLayout.LayoutParams(-2,rowHeight))
                controls.add(item to buy)
                sheet.body.addView(row,LinearLayout.LayoutParams(-1,-2))
                if(item!=MazeMarket.items.last { it.kind==kind }) divider(sheet.body)
            }
            sheet.scroll.scrollTo(0,0); refresh()
        }
        for(kind in MazeItemKind.entries) {
            val button=action(if(kind==MazeItemKind.VEHICLE) "Vehicles" else "Accessories") { category(kind) }
            tabButtons.add(kind to button)
            tabs.addView(button,LinearLayout.LayoutParams(0,rowHeight,1f))
        }
        category(MazeItemKind.VEHICLE)
        sheet.show()
        return sheet.dialog
    }
}

