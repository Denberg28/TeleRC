package io.github.denberg28.telerc

import android.app.Activity
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
    private var popup:AppDialog?=null
    private var visible=false
    private var foreground=false
    private var state:BalanceSetup.Status?=null
    private var at=0L
    private var pending=0L
    private var pendingAction:BalanceSetup.Action?=null
    private var session=0L
    private var sentAt=0L
    private var request=SystemClock.elapsedRealtime().coerceAtLeast(1) and 0xffffffffL
    private var summary:TextView?=null
    private var detail:TextView?=null
    private var response="Connect to TeleRC-Balancer Wi-Fi, then Connect in Setup."
    private var stopButton:Button?=null
    private var wheels:CheckBox?=null
    private var observed:CheckBox?=null
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
        text=value;textSize=13f;setTextColor(AppColors.ink(activity));includeFontPadding=false
        setLineSpacing(dp(2).toFloat(),1f)
    }
    private fun button(value:String,action:()->Unit)=AppUi.button(activity,value,false,action)
    fun create(root:LinearLayout,dropdown:View,resumed:Boolean,closed:()->Unit) {
        visible=true;foreground=resumed;actions.clear()
        dismissPopup()
        root.addView(dropdown,LinearLayout.LayoutParams(dp(112),rowHeight()))
        val dialog=AppDialog.sheet(activity)
        popup=dialog
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val frame=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(AppColors.surface(activity));cornerRadius=dp(8).toFloat()
            }
            contentDescription="Balance popup"
        }
        val header=LinearLayout(activity).apply {isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL}
        header.addView(View(activity),LinearLayout.LayoutParams(rowHeight(),1))
        header.addView(text("Balance pre-test").apply {
            textSize=16f;gravity=Gravity.CENTER;setTypeface(typeface,android.graphics.Typeface.BOLD)
        },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("×"){dialog.cancel()}.apply {contentDescription="Close Balance"},
            LinearLayout.LayoutParams(rowHeight(),rowHeight()))
        frame.addView(header,LinearLayout.LayoutParams(-1,-2))
        fun divider() {frame.addView(View(activity).apply {
            setBackgroundColor(AppColors.ink(activity));alpha=.12f
        },LinearLayout.LayoutParams(-1,dp(1)))}
        divider()
        val body=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(8),dp(4),dp(8))
        }
        fun section(value:String) {
            body.addView(text(value).apply {setTypeface(typeface,android.graphics.Typeface.BOLD)},
                LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(6)})
        }
        summary=text("Waiting for balancer status…");detail=text("")
        body.addView(summary,LinearLayout.LayoutParams(-1,-2))
        body.addView(detail,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)})
        section("1 · Calibration")
        body.addView(text("Calibration ready means step 1 is complete. Recalibrate only after changing the sensor mounting or when the saved check fails."),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})
        body.addView(button("Calibrate & save") {confirmCalibration()}.also {actions[BalanceSetup.Action.CALIBRATE]=it},LinearLayout.LayoutParams(-1,rowHeight()))
        section("2 · Motor direction test · short forward pulse")
        wheels=CheckBox(activity).apply {text="Regulated motor power ON; chassis secured; wheels raised";textSize=13f;setTextColor(AppColors.ink(activity));setOnCheckedChangeListener{_,_->refresh()}}
        body.addView(wheels,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})
        val motors=LinearLayout(activity)
        listOf("Left" to BalanceSetup.Action.LEFT,"Right" to BalanceSetup.Action.RIGHT,"Both" to BalanceSetup.Action.BOTH).forEachIndexed {i,(name,action)->
            motors.addView(button(name){confirmMotor(action)}.also{actions[action]=it},LinearLayout.LayoutParams(0,rowHeight(),1f).apply{if(i>0)leftMargin=dp(6)})
        }
        body.addView(motors)
        body.addView(button("No movement? Check output") {showMotorHelp()},LinearLayout.LayoutParams(-1,rowHeight()).apply{topMargin=dp(6)})
        section("3 · Confirm: both wheels propel forward; forward tilt makes angle positive.")
        observed=CheckBox(activity).apply {
            text="I observed both wheels move forward and checked the tilt signs";textSize=13f;setTextColor(AppColors.ink(activity))
            setOnCheckedChangeListener{_,_->refresh()}
        }
        body.addView(observed,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})
        body.addView(button("Confirm directions") {confirmDirections()}.also{actions[BalanceSetup.Action.VERIFY]=it},LinearLayout.LayoutParams(-1,rowHeight()))
        body.addView(text("Setup never arms. Use Controls ›, Phone control, centered sticks, then ARM while supported upright. Reported PWM does not prove wheel movement."),
            LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
        val scroll=ScrollView(activity).apply{addView(body);isFillViewport=false;contentDescription="Balance popup content"}
        frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        divider()
        val footer=LinearLayout(activity).apply {isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL}
        footer.addView(button("Refresh") {if(connected())send(BalanceSetup.query,false)},LinearLayout.LayoutParams(0,rowHeight(),1f))
        footer.addView(button("STOP / Disarm") {
            if(state?.mode==1 || state?.mode==2) {
                AppDialog.Builder(activity).setTitle("Stop motor output?").setMessage("Support the chassis first. Disarm removes balancing torque and the robot can fall.")
                    .setPositiveButton("Stop / Disarm"){_,_->stop()}.setNegativeButton("Cancel",null).show()
            } else stop()
        }.also{stopButton=it},LinearLayout.LayoutParams(0,rowHeight(),1f).apply{leftMargin=dp(8)})
        footer.addView(button("Controls ›",controls),LinearLayout.LayoutParams(0,rowHeight(),1f).apply{leftMargin=dp(8)})
        frame.addView(footer,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        dialog.setContentView(frame)
        dialog.setOnCancelListener {if(visible && popup===dialog && !activity.isFinishing && !activity.isDestroyed)closed()}
        // Use the same content measurement and screen cap as the other popups.
        root.post {
            if(visible && foreground && popup===dialog) {
                PopupLayout.configure(activity,dialog,frame,scroll,420)
                dialog.show()
            }
        }
        handler.removeCallbacks(ticker);handler.post(ticker);refresh()
    }
    private fun stop() {
        send(BalanceSetup.stop,true);response="Stop requested; wait for rover confirmation."
        pending=0;pendingAction=null;refresh()
    }
    private fun consent(action:BalanceSetup.Action):BalanceSetup.Consent? =
        state?.let { BalanceSetup.Consent(it.boot,session,action) }
    private fun confirmCalibration() {
        val consent=consent(BalanceSetup.Action.CALIBRATE)?:return
        AppDialog.Builder(activity).setTitle("Calibrate gyro?").setMessage("Disconnect motor power and hold the chassis upright and still. The rover will erase the saved offset, restart, recalibrate and save it. Reconnect remains disarmed.")
            .setPositiveButton("Calibrate"){_,_->dispatchAction(consent)}.setNegativeButton("Cancel",null).show()
    }
    private fun confirmMotor(action:BalanceSetup.Action) {
        if(wheels?.isChecked!=true)return
        val s=state?:return
        val consent=consent(action)?:return
        AppDialog.Builder(activity).setTitle("${action.name.lowercase().replaceFirstChar{it.uppercase()}} motor test?")
            .setMessage("Keep both wheels suspended and hands clear. A ${s.testMs} ms forward pulse uses ${s.testPwm}/255 PWM. Check regulated motor power and common ground. Stop if a wheel turns the wrong way.")
            .setPositiveButton("Test"){_,_->if(wheels?.isChecked==true)dispatchAction(consent)}.setNegativeButton("Cancel",null).show()
    }
    private fun confirmDirections() {
        if(observed?.isChecked!=true)return
        val consent=consent(BalanceSetup.Action.VERIFY)?:return
        AppDialog.Builder(activity).setTitle("Confirm physical directions?")
            .setMessage("Confirm you observed BOTH wheels propel forward and forward/back tilt gives positive/negative angle. Software cannot verify this. This confirmation applies to the current boot and does not arm.")
            .setPositiveButton("Directions correct"){_,_->dispatchAction(consent)}.setNegativeButton("Cancel",null).show()
    }
    private fun dispatchAction(consent:BalanceSetup.Consent) {
        val s=state?:return
        val action=consent.action
        if(!visible||!foreground||!connected()||pending!=0L||!consent.allowed(s,session,at,now())) {response="Status or session changed. Refresh and confirm again.";refresh();return}
        if(action in listOf(BalanceSetup.Action.LEFT,BalanceSetup.Action.RIGHT,BalanceSetup.Action.BOTH) && wheels?.isChecked!=true)return
        if(action==BalanceSetup.Action.VERIFY && observed?.isChecked!=true)return
        if(action!=BalanceSetup.Action.VERIFY)observed?.isChecked=false
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
            session++
            pending=0;pendingAction=null;wheels?.isChecked=false;observed?.isChecked=false
            response="Rover restarted. Check calibration and repeat direction verification."
        }
        state=s;at=receivedAt
        if(!s.testDone)observed?.isChecked=false
        if(s.request==pending && pending!=0L) {
            response=BalanceSetup.responseText(s.result)
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
            else->"${if(s!!.calibrated) "Calibration ready" else "Calibration unavailable"} · ${s.store}\n${if(s.verified) "Directions confirmed" else "Directions need checking"} · ${s.blocker.replace('_',' ')}\nController: ${s.reason}"
        }
        detail?.text=if(fresh)String.format(Locale.US,"Angle %.2f° · rate %.2f°/s · gyro bias %.3f°/s\n%s\n%s\n%s",s!!.angle,s.rate,s.bias,s.calibration,response,outputText(s)) else response
        observed?.isEnabled=fresh&&s?.testDone==true&&pending==0L
        stopButton?.isEnabled=connected()&&s!=null
        actions.forEach{(action,button)->button.isEnabled=visible&&foreground&&fresh&&pending==0L&&BalanceSetup.allowed(s,at,now(),action)&&
            (action !in listOf(BalanceSetup.Action.LEFT,BalanceSetup.Action.RIGHT,BalanceSetup.Action.BOTH)||wheels?.isChecked==true)&&
            (action!=BalanceSetup.Action.VERIFY||observed?.isChecked==true)}
    }
    private fun outputText(s:BalanceSetup.Status):String {
        val out=s.output
        val test="Test ${s.testPwm}/255 · ${s.testMs} ms"
        if(out==null)return "$test · output details need firmware 0.3.1+"
        if(!out.pwmReady)return "$test · PWM unavailable; check firmware fault"
        if(!out.valid)return "$test · no accepted PWM test recorded this boot"
        return "$test · last test command L ${out.left} / R ${out.right}\nGPIO 4/5: ${out.duties[0]}/${out.duties[1]} · GPIO 6/7: ${out.duties[2]}/${out.duties[3]} (last test readback)"
    }
    private fun showMotorHelp() {
        val s=state
        AppDialog.Builder(activity).setTitle("Motor test: no movement")
            .setMessage((s?.let{outputText(it)+"\n\n"}?:"")+"PWM readback confirms only the ESP32 peripheral setting. Check regulated motor voltage at MX1508 +/− during the pulse and common GND. Left inputs: GPIO4/5; right: GPIO6/7. GPIO8/9 remain sensor SDA/SCL. Confirm the motors use the driver's output pairs. Change wiring with power off. If no accepted test is recorded, read the reply/fault before checking power. Leave directions unconfirmed until both wheels move correctly.")
            .setPositiveButton("Close",null).show()
    }
    fun dismissPopup() {
        popup?.setOnCancelListener(null)
        popup?.dismiss()
        popup=null
    }
    fun leave() {
        // Cancel queued UI commands before the caller increments its send epoch.
        if(pendingAction in listOf(BalanceSetup.Action.LEFT,BalanceSetup.Action.RIGHT,BalanceSetup.Action.BOTH)||state?.mode==3)
            send(BalanceSetup.stop,true)
        session++;visible=false;foreground=false;handler.removeCallbacks(ticker);pending=0;pendingAction=null
        dismissPopup()
        wheels?.isChecked=false;observed?.isChecked=false;state=null;at=0
    }
    fun pause(){leave()}
    fun resume(){if(visible){foreground=true;handler.removeCallbacks(ticker);handler.post(ticker)}}
    fun disconnected(){session++;state=null;at=0;pending=0;pendingAction=null;wheels?.isChecked=false;observed?.isChecked=false;response="Link lost. Reconnect, refresh and repeat checks deliberately.";refresh()}
}
