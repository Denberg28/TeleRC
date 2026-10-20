package io.github.denberg28.telerc

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

/** Setup UI only; balancing and arming authority remain on the robot. */
class BalancePretestTab(private val activity:Activity,
    private val connected:()->Boolean,
    private val send:(ByteArray,Boolean)->Unit,
    private val controls:()->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var visible=false
    private var foreground=false
    private var state:BalanceSetup.Status?=null
    private var at=0L
    private var pending=0L
    private var pendingAction:BalanceSetup.Action?=null
    private var sentAt=0L
    private var request=SystemClock.elapsedRealtime().coerceAtLeast(1) and 0xffffffffL
    private var summary:TextView?=null
    private var detail:TextView?=null
    private var response="Connect to TeleRC-Balancer Wi-Fi, then Connect in Setup."
    private var stopButton:Button?=null
    private var wheels:CheckBox?=null
    private val actions=mutableMapOf<BalanceSetup.Action,Button>()
    private val ticker=object:Runnable { override fun run() {
        if(!visible || !foreground)return
        if(connected())send(BalanceSetup.query,false)
        if(pending!=0L && now()-sentAt>4000) {
            response="No completion reply. Check status before retrying; requests are never replayed."
            pending=0;pendingAction=null
        }
        refresh();handler.postDelayed(this,750)
    } }
    private fun now()=SystemClock.elapsedRealtime()
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun rowHeight()=dp((48*maxOf(1f,activity.resources.configuration.fontScale)).toInt())
    private fun text(value:String)=TextView(activity).apply {
        text=value;textSize=13f;setTextColor(AppColors.ink(activity));setPadding(dp(4),dp(4),dp(4),dp(4))
    }
    private fun button(value:String,action:()->Unit)=Button(activity).apply {
        text=value;isAllCaps=false;textSize=12f;minimumHeight=rowHeight();minimumWidth=0
        setTextColor(AppColors.accent(activity));stateListAnimator=null
        background=GradientDrawable().apply {setColor(AppColors.softAccent(activity));cornerRadius=dp(10).toFloat()}
        setOnClickListener{action()}
    }
    fun create(root:LinearLayout,dropdown:View,resumed:Boolean) {
        visible=true;foreground=resumed;actions.clear()
        val header=LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(dropdown,LinearLayout.LayoutParams(dp(150),rowHeight()))
        header.addView(text("Balance pre-test").apply{textSize=16f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,rowHeight(),1f))
        header.addView(button("Controls ›",controls),LinearLayout.LayoutParams(dp(112),rowHeight()))
        root.addView(header)
        val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8))
            background=GradientDrawable().apply {setColor(AppColors.surface(activity));cornerRadius=dp(12).toFloat()}}
        summary=text("Waiting for balancer status…");detail=text("")
        body.addView(summary);body.addView(detail)
        body.addView(text("1 · Calibration"))
        body.addView(button("Calibrate & save") {confirmCalibration()}.also {actions[BalanceSetup.Action.CALIBRATE]=it},LinearLayout.LayoutParams(-1,rowHeight()))
        body.addView(text("2 · Motor direction test · short forward pulse"))
        wheels=CheckBox(activity).apply {text="Chassis secured; both wheels off the floor";textSize=13f;setTextColor(AppColors.ink(activity));setOnCheckedChangeListener{_,_->refresh()}}
        body.addView(wheels)
        val motors=LinearLayout(activity)
        listOf("Left" to BalanceSetup.Action.LEFT,"Right" to BalanceSetup.Action.RIGHT,"Both" to BalanceSetup.Action.BOTH).forEachIndexed {i,(name,action)->
            motors.addView(button(name){confirmMotor(action)}.also{actions[action]=it},LinearLayout.LayoutParams(0,rowHeight(),1f).apply{if(i>0)leftMargin=dp(6)})
        }
        body.addView(motors)
        body.addView(text("3 · Confirm: both wheels propel forward; forward tilt makes angle positive."))
        body.addView(button("Confirm directions") {confirmDirections()}.also{actions[BalanceSetup.Action.VERIFY]=it},LinearLayout.LayoutParams(-1,rowHeight()))
        body.addView(text("Setup never arms. Use Controls ›, Phone control, centered sticks, then ARM while supported upright. Reported PWM does not prove wheel movement."))
        root.addView(ScrollView(activity).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=dp(8);bottomMargin=dp(8)})
        val footer=LinearLayout(activity)
        footer.addView(button("Refresh") {if(connected())send(BalanceSetup.query,false)},LinearLayout.LayoutParams(0,rowHeight(),1f))
        footer.addView(button("STOP / Disarm") {
            if(state?.mode==1 || state?.mode==2) {
                AppDialog.Builder(activity).setTitle("Stop motor output?").setMessage("Support the chassis first. Disarm removes balancing torque and the robot can fall.")
                    .setPositiveButton("Stop / Disarm"){_,_->stop()}.setNegativeButton("Cancel",null).show()
            } else stop()
        }.also{stopButton=it},LinearLayout.LayoutParams(0,rowHeight(),1f).apply{leftMargin=dp(8)})
        root.addView(footer);handler.removeCallbacks(ticker);handler.post(ticker);refresh()
    }
    private fun stop() {
        send(BalanceSetup.stop,true);response="Stop requested; wait for rover confirmation."
        pending=0;pendingAction=null;refresh()
    }
    private fun confirmCalibration() {
        AppDialog.Builder(activity).setTitle("Calibrate gyro?").setMessage("Disconnect motor power and hold the chassis upright and still. The rover will erase the saved offset, restart, recalibrate and save it. Reconnect remains disarmed.")
            .setPositiveButton("Calibrate"){_,_->dispatchAction(BalanceSetup.Action.CALIBRATE)}.setNegativeButton("Cancel",null).show()
    }
    private fun confirmMotor(action:BalanceSetup.Action) {
        if(wheels?.isChecked!=true)return
        val s=state?:return
        AppDialog.Builder(activity).setTitle("${action.name.lowercase().replaceFirstChar{it.uppercase()}} motor test?")
            .setMessage("Keep both wheels suspended and hands clear. A ${s.testMs} ms forward pulse uses ${s.testPwm}/255 PWM. Check regulated motor power and common ground. Stop if a wheel turns the wrong way.")
            .setPositiveButton("Test"){_,_->if(wheels?.isChecked==true)dispatchAction(action)}.setNegativeButton("Cancel",null).show()
    }
    private fun confirmDirections() {
        AppDialog.Builder(activity).setTitle("Confirm physical directions?")
            .setMessage("Confirm you observed BOTH wheels propel forward and forward/back tilt gives positive/negative angle. Software cannot verify this. This confirmation applies to the current boot and does not arm.")
            .setPositiveButton("Directions correct"){_,_->dispatchAction(BalanceSetup.Action.VERIFY)}.setNegativeButton("Cancel",null).show()
    }
    private fun dispatchAction(action:BalanceSetup.Action) {
        val s=state?:return
        if(!visible||!foreground||!connected()||pending!=0L||!BalanceSetup.allowed(s,at,now(),action)) {response="Fresh ready status required. Refresh and check the blocker.";refresh();return}
        request=(request+1) and 0xffffffffL;if(request==0L)request=1
        pending=request;pendingAction=action;sentAt=now();response="Waiting for rover confirmation…"
        send(BalanceSetup.command(s,request,action),false);refresh()
    }
    fun receive(bytes:ByteArray,receivedAt:Long) {
        val s=BalanceSetup.parse(bytes)?:return
        if(!visible||now()-receivedAt !in 0..250)return
        val old=state
        if(!BalanceSetup.newer(old,s))return
        if(old!=null && old.boot!=s.boot) {
            pending=0;pendingAction=null;wheels?.isChecked=false
            response="Rover restarted. Check calibration and repeat direction verification."
        }
        state=s;at=receivedAt
        if(s.request==pending && pending!=0L) {
            response=s.result.replace('_',' ')
            if(s.result!="QUEUED" && s.result!="TEST_RUNNING") {pending=0;pendingAction=null}
        }
        refresh()
    }
    private fun refresh() {
        val s=state
        val fresh=connected() && s!=null && now()-at in 0..1500
        summary?.text=when {
            !connected()->"Connect via Wi-Fi in Setup · no setup commands sent"
            !fresh->"Waiting for balancer firmware v0.3.0+ · controls locked"
            else->"${if(s!!.calibrated) "Calibration ready" else "Calibration unavailable"} · ${s.store}\n${if(s.verified) "Directions confirmed" else "Directions need checking"} · ${s.blocker.replace('_',' ')}"
        }
        detail?.text=if(fresh)String.format(Locale.US,"Angle %.2f° · rate %.2f°/s · gyro bias %.3f°/s\n%s\n%s",s!!.angle,s.rate,s.bias,s.calibration,response) else response
        stopButton?.isEnabled=connected()&&s!=null
        actions.forEach{(action,button)->button.isEnabled=visible&&foreground&&fresh&&pending==0L&&BalanceSetup.allowed(s,at,now(),action)&&
            (action !in listOf(BalanceSetup.Action.LEFT,BalanceSetup.Action.RIGHT,BalanceSetup.Action.BOTH)||wheels?.isChecked==true)}
    }
    fun leave() {
        // Cancel queued UI commands before the caller increments its send epoch.
        if(pendingAction in listOf(BalanceSetup.Action.LEFT,BalanceSetup.Action.RIGHT,BalanceSetup.Action.BOTH)||state?.mode==3)
            send(BalanceSetup.stop,true)
        visible=false;foreground=false;handler.removeCallbacks(ticker);pending=0;pendingAction=null
        wheels?.isChecked=false;state=null;at=0
    }
    fun pause(){leave()}
    fun resume(){if(visible){foreground=true;handler.removeCallbacks(ticker);handler.post(ticker)}}
    fun disconnected(){state=null;at=0;pending=0;pendingAction=null;wheels?.isChecked=false;response="Link lost. Reconnect, refresh and repeat checks deliberately.";refresh()}
}
