package io.github.denberg28.telerc

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

/** UI and supervision owned exclusively by Waypoint. No shared map, joystick or Setup preferences. */
class WaypointTab(private val activity: Activity,
    private val link: ()->Pair<Int,Boolean>,
    private val send: (WaypointNavigation.Dispatch)->Unit,
    private val linkHint: ()->String = { "Connect to ArduRover in Setup. Mini rover and filtering gateways support planning only." },
    private val servoButton: (String)->View) {
    val navigation=WaypointNavigation()
    private val handler=Handler(Looper.getMainLooper())
    private var visible=false
    private var foreground=false
    private var mapStarted=false
    private var mapResumed=false
    private var map: WaypointMap?=null
    private var dialog: AppDialog?=null
    private var summary: TextView?=null
    private var readiness: TextView?=null
    private var modeButton: Button?=null
    private var startRequested=false
    private var check: Button?=null
    private var stopButton: Button?=null
    private var steps: TextView?=null
    private var routeRows: LinearLayout?=null
    private var routeFingerprint=""
    private val prefs=activity.getSharedPreferences("waypoint_route",0)
    private var mission=if(prefs.getBoolean("sequence_planner",false)) prefs.getBoolean("multiple",true) else true
    private var placement:String?=null
    private val functionControls=mutableMapOf<String,Button>()
    private val functionPrefs=activity.getSharedPreferences("servo_assignments",0)
    private var points=WaypointPlan.decode(prefs.getString("draft","").orEmpty()).let { if(mission) it else it.takeLast(1) }
    private val loadedActions=WaypointFunctions.decode(prefs.getString("actions","").orEmpty(),points)
    private var invalidActions=loadedActions==null
    private var actions=loadedActions ?: List(points.size) { emptyList() }
    private data class StartSnapshot(val points:List<Waypoint>,val actions:List<List<WaypointAction>>,val functions:List<WaypointFunction>,val mission:Boolean)
    private var startSnapshot:StartSnapshot?=null
    private fun currentPlan()=StartSnapshot(points.toList(),actions.map { it.toList() },bindings(),mission)
    private fun bindings()=WaypointFunctions.NAMES.filter { name -> actions.any { row -> row.any { it.name==name } } }.map { name ->
        WaypointFunction(name,functionPrefs.getInt(name,0),functionPrefs.getInt("${name}_off_pwm",1100),functionPrefs.getInt("${name}_on_pwm",1900))
    }
    private val ticker=object:Runnable { override fun run() {
        navigation.tick(now(),link().second,foreground,visible)
        flush(); update()
        if (visible || navigation.busy || foreground && navigation.gcsSessionActive) handler.postDelayed(this,250)
    } }
    private fun now()=SystemClock.elapsedRealtime()
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun height()=dp((48*maxOf(1f,activity.resources.configuration.fontScale)).toInt())
    private fun label(value:String,size:Float=13f)=TextView(activity).apply {
        text=value; textSize=size; setTextColor(AppColors.ink(activity)); gravity=Gravity.CENTER
    }
    private fun panel()=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8))
        background=GradientDrawable().apply { setColor(AppColors.surface(activity)); cornerRadius=dp(14).toFloat() }
    }
    private fun button(text:String,filled:Boolean=false,action:()->Unit)=Button(activity).apply {
        this.text=text; isAllCaps=false; textSize=12f; minWidth=0; minimumHeight=dp(48)
        setPadding(dp(6),0,dp(6),0); stateListAnimator=null
        setTextColor(if(filled) Color.WHITE else AppColors.accent(activity))
        background=GradientDrawable().apply { setColor(if(filled) AppColors.buttonFill(activity) else AppColors.softAccent(activity)); cornerRadius=dp(10).toFloat() }
        setOnClickListener { action() }
    }
    private fun flush()=navigation.drain().forEach(send)
    fun sendIfAllowed(dispatch:WaypointNavigation.Dispatch,send:(ByteArray)->Unit)=navigation.transmit(dispatch,now(),send)
    private fun save() {
        startRequested=false; startSnapshot=null; invalidActions=false
        prefs.edit().putString("draft",WaypointPlan.encode(points)).putBoolean("multiple",mission).putBoolean("sequence_planner",true)
            .putString("actions",WaypointFunctions.encode(points,actions)).apply(); update()
    }
    fun create(root:LinearLayout,dropdown:View,started:Boolean,resumed:Boolean) {
        releaseMap(); visible=true; foreground=resumed; routeFingerprint=""
        val row=LinearLayout(activity)
        fun column(top:View,middle:View,bottom:View)=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            addView(top,LinearLayout.LayoutParams(-1,height()).apply { bottomMargin=dp(8) })
            addView(middle,LinearLayout.LayoutParams(-1,0,1f))
            addView(bottom,LinearLayout.LayoutParams(-1,height()).apply { topMargin=dp(8) })
        }
        val routePanel=panel()
        modeButton=button(if(mission) "Multiple points ▾" else "Single point ▾") { changeMode() }
        routePanel.addView(modeButton,LinearLayout.LayoutParams(-1,height()))
        routeRows=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        routePanel.addView(ScrollView(activity).apply { addView(routeRows) },LinearLayout.LayoutParams(-1,0,1f))
        val editActions=LinearLayout(activity)
        editActions.addView(button("Undo") {
            if (!navigation.busy && points.isNotEmpty() && !invalidActions) { points=points.dropLast(1); actions=actions.dropLast(1); save() }
        }.apply { contentDescription="Remove last waypoint" },LinearLayout.LayoutParams(0,height(),1f))
        editActions.addView(button("Clear") {
            if (!navigation.busy && (points.isNotEmpty() || invalidActions)) confirm("Clear route?","Remove all points?","Clear") {
                points=emptyList(); actions=emptyList(); placement=null; save()
            }
        },LinearLayout.LayoutParams(0,height(),1f).apply { leftMargin=dp(4) })
        routePanel.addView(editActions)
        val functions=LinearLayout(activity)
        listOf("F1","F2","F3").forEachIndexed { index,name ->
            val control=button(name) {
                if(!navigation.busy) { placement=if(placement==name) null else name; startRequested=false; update() }
            }.apply {
                contentDescription="Plan $name ON or OFF on map; hold for settings"
                setOnLongClickListener { if(!navigation.busy) { placement=null; update(); functionSettings(name) }; true }
            }
            functionControls[name]=control
            functions.addView(control,LinearLayout.LayoutParams(0,-1,1f).apply { if(index>0) leftMargin=dp(4) })
        }
        row.addView(column(dropdown,routePanel,functions),LinearLayout.LayoutParams(0,-1,1.6f).apply { rightMargin=dp(8) })
        val center=panel()
        summary=label("Tap map to set a destination",12f).apply { maxLines=2 }
        center.addView(summary,LinearLayout.LayoutParams(-1,dp(40)))
        val mapStatus=label("Reference map · rover GPS unavailable",10f)
        map=WaypointMap(activity,::select,{ mapStatus.text=it },::editPoint)
        center.addView(map!!.view,LinearLayout.LayoutParams(-1,0,1f))
        center.addView(mapStatus,LinearLayout.LayoutParams(-1,dp(36)))
        val mapActions=LinearLayout(activity)
        mapActions.addView(button("Center map") { map?.locate() },LinearLayout.LayoutParams(0,height(),1f))
        mapActions.addView(button("Add coordinates") { coordinates() },LinearLayout.LayoutParams(0,height(),1f).apply { leftMargin=dp(4) })
        center.addView(mapActions)
        row.addView(center,LinearLayout.LayoutParams(0,-1,3.8f).apply { rightMargin=dp(8) })
        val safety=panel()
        steps=label("1. Select points\n2. Start · 3. Confirm",12f).apply {
            gravity=Gravity.START; setPadding(dp(4),0,dp(4),dp(8))
        }
        val safetyBody=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; addView(steps) }
        readiness=label("",12f).apply { maxLines=7; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(dp(3).toFloat(),1f); contentDescription="Rover check details"; gravity=Gravity.TOP or Gravity.START; setPadding(dp(4),dp(4),dp(4),dp(4)) }
        safetyBody.addView(readiness)
        safety.addView(ScrollView(activity).apply { addView(safetyBody) },LinearLayout.LayoutParams(-1,0,1f))
        safety.addView(button("Help & safety") { safetyInfo() },LinearLayout.LayoutParams(-1,height()))
        check=button("Mission start",true) { requestStart() }.apply {
            contentDescription="Mission start; check rover and confirm route"
        }
        stopButton=button("STOP · HOLD",true) { stop("Stopped by operator") }.apply {
            contentDescription="Stop waypoint navigation and request flight-controller HOLD"
            background=GradientDrawable().apply { setColor(Color.rgb(147,47,54)); cornerRadius=dp(10).toFloat() }
        }
        row.addView(column(check!!,safety,stopButton!!),LinearLayout.LayoutParams(0,-1,1.6f))
        root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
        if(started) { map?.start(); mapStarted=true }; if(resumed) { map?.resume(); mapResumed=true }
        kick(); update()
    }
    private fun kick() { handler.removeCallbacks(ticker); handler.post(ticker) }
    private fun select(point:Waypoint) {
        if(!point.valid || navigation.busy) return
        if(invalidActions) { notice("Rebuild sequence","Saved actions are invalid. Clear the route before adding points."); return }
        placement?.let { name -> chooseAction(name,null,point); return }
        addPoint(point,emptyList())
    }
    private fun addPoint(point:Waypoint,row:List<WaypointAction>) {
        if(mission || row.isNotEmpty()) {
            if(points.size>=WaypointPlan.MAX_POINTS) { notice("Route full","Maximum 20 points."); return }
            mission=true; points=points+point; actions=actions+listOf(row)
        } else { points=listOf(point); actions=listOf(row) }
        placement=null; save()
    }
    private fun chooseAction(name:String,index:Int?,point:Waypoint?=null) {
        if(navigation.busy || invalidActions) return
        dialog?.dismiss()
        val choices=if(index==null) arrayOf("ON","OFF") else arrayOf("ON","OFF","No change")
        dialog=AppDialog.Builder(activity).setTitle("$name at ${if(index==null) "new point" else "point ${index+1}"}")
            .setItems(choices) { _,choice ->
                val action=if(choice<2) WaypointAction(name,choice==0) else null
                if(index==null) { if(action!=null && point!=null) addPoint(point,listOf(action)) }
                else {
                    actions=actions.mapIndexed { i,row -> if(i==index) (if(action==null) row.filter { it.name!=name } else if(row.any { it.name==name }) row.map { if(it.name==name) action else it } else row+action) else row }
                    placement=null; save()
                }
            }.setNegativeButton("Cancel") { _,_ -> placement=null; update() }.setOnCancelListener { placement=null; update() }.show()
    }
    private fun editPoint(index:Int) {
        if(navigation.busy || index !in points.indices) return
        if(invalidActions) { notice("Rebuild sequence","Saved actions are invalid. Clear the route before editing."); return }
        placement?.let { chooseAction(it,null,points[index]); return }
        dialog?.dismiss()
        dialog=AppDialog.Builder(activity).setTitle("Point ${index+1} · sequence")
            .setItems(arrayOf("F1 ON / OFF","F2 ON / OFF","F3 ON / OFF","Remove point")) { _,choice ->
                if(choice<3) chooseAction(WaypointFunctions.NAMES[choice],index)
                else confirm("Remove point ${index+1}?","Remove this location and its function actions?","Remove") {
                    points=points.filterIndexed { i,_ -> i!=index }; actions=actions.filterIndexed { i,_ -> i!=index }; save()
                }
            }.setNegativeButton("Close",null).show()
    }
    private fun functionSettings(name:String) {
        dialog?.dismiss()
        dialog=AppDialog.Builder(activity).setTitle("$name output settings")
            .setItems(arrayOf("Assign servo output","Set ON / OFF PWM")) { _,choice ->
                startRequested=false; startSnapshot=null
                if(choice==0) servoButton(name).performClick() else functionPwm(name)
            }.setNegativeButton("Close",null).show()
    }
    private fun functionPwm(name:String) {
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        fun input(caption:String,value:Int)=EditText(activity).apply {
            hint=caption; contentDescription=caption; inputType=android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine(); setText(value.toString())
            body.addView(label(caption)); body.addView(this)
        }
        val on=input("$name ON PWM",functionPrefs.getInt("${name}_on_pwm",1900))
        val off=input("$name OFF PWM",functionPrefs.getInt("${name}_off_pwm",1100))
        dialog=AppDialog.Builder(activity).setTitle("$name PWM · 1000–2000").setView(body)
            .setPositiveButton("Save",null).setNegativeButton("Cancel",null).create().also { d ->
                d.setOnShowListener { d.getButton(-1).setOnClickListener {
                    val a=on.text.toString().toIntOrNull(); val b=off.text.toString().toIntOrNull()
                    if(a==null || b==null || a !in 1000..2000 || b !in 1000..2000 || a==b) on.error="Use distinct values from 1000 to 2000"
                    else { functionPrefs.edit().putInt("${name}_on_pwm",a).putInt("${name}_off_pwm",b).apply(); startRequested=false; startSnapshot=null; d.dismiss(); update() }
                } }; d.show()
            }
    }
    private fun requestStart() {
        val (target,connected)=link()
        if (!connected || target==0) {
            notice("Planning only",(if(mission) "Multiple points: visits the listed points in order." else "Single point: goes to the selected destination.")+"\n\n"+linkHint()+"\n\nMovement has not started.")
            return
        }
        if (points.isEmpty()) { notice("Select a destination","Tap the map or add coordinates first."); return }
        if (navigation.busy) return
        if(invalidActions) { notice("Rebuild sequence","Saved actions are invalid. Clear the route and rebuild it."); return }
        val current=currentPlan()
        WaypointFunctions.problem(points,actions,current.functions)?.let { notice("Check function sequence",it); return }
        placement=null; startRequested=true; startSnapshot=current
        navigation.check(target,now(),current.functions); flush(); kick(); update()
    }
    private fun confirmStart() {
        if (navigation.busy || points.isEmpty()) return
        val problem=if(!link().second || link().first!=navigation.system) linkHint() else navigation.blocker(now()) ?: WaypointPlan.problem(points,navigation.rover) ?:
            if(navigation.state!=WaypointNavigation.State.READY) "Tap Mission start to check the rover" else null
        if(problem!=null) { notice("Route saved · movement locked",problem); return }
        val planned=startSnapshot ?: return
        if(planned!=currentPlan()) { notice("Plan changed","Tap Mission start again to recheck the sequence."); return }
        val snapshot=planned.points
        val multiple=planned.mission
        val checkedEpoch=navigation.epoch
        confirm(if(mission) "Start route?" else "Go to this point?",
            "${if(multiple) "Multiple points · in listed order" else "Single destination"}\n${snapshot.size} point(s) · 0.5 m/s · within 100 m of start\n"+
            snapshot.mapIndexed { i,p -> "${i+1}. ${p.label()}"+planned.actions[i].takeIf { it.isNotEmpty() }?.joinToString(" · ","\nAfter arrival: ") { it.label() }.orEmpty() }.joinToString("\n")+
            (if(planned.functions.isEmpty()) "" else "\n\nOutputs\n"+planned.functions.joinToString("\n") { "${it.name}: output ${it.output} · OFF ${it.off} / ON ${it.on} PWM" })+
            "\n\n${if(planned.functions.isNotEmpty()) "All planned functions reset OFF before moving. At function points: HOLD, then verify ACK and PWM. End / STOP requests OFF. Lost-link OFF cannot be guaranteed.\n\n" else ""}Keep TeleRC open and connected. Check the path is clear; no obstacle avoidance. STOP or leaving this tab requests HOLD. HOLD keeps the rover armed.","Start") {
                if (!visible || !foreground || planned!=currentPlan() || navigation.epoch!=checkedEpoch) return@confirm
                navigation.start(snapshot,now(),planned.actions,planned.functions)?.let { notice("Cannot start",it) }
                flush(); kick(); update()
            }
    }
    private fun coordinates() {
        if(navigation.busy) return
        val input=EditText(activity).apply { hint="Latitude, longitude"; setSingleLine(); contentDescription="Waypoint latitude and longitude" }
        dialog?.dismiss()
        dialog=AppDialog.Builder(activity).setTitle("Add waypoint coordinates").setView(input)
            .setPositiveButton("Select",null).setNegativeButton("Cancel",null).create().also { d ->
                d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val p=WaypointPlan.decode(input.text.toString().trim())
                    if(p.size!=1 || input.text.toString().contains(';')) input.error="Use decimal latitude, longitude"
                    else { d.dismiss(); select(p.single()) }
                } }; d.show(); d.window?.setWindowAnimations(0)
            }
    }
    private fun changeMode() {
        if(navigation.busy) return
        dialog?.dismiss()
        dialog=AppDialog.Builder(activity).setTitle("Route mode")
            .setSingleChoiceItems(arrayOf("Single destination","Multiple points · up to 20"),if(mission) 1 else 0) { d,choice ->
                d.dismiss()
                val next=choice==1
                if(next!=mission) {
                    fun apply() { mission=next; placement=null; if(!mission) { points=points.takeLast(1); actions=actions.takeLast(1) }; save() }
                    if(!next && points.size>1) confirm("Use single destination?","Keep only the last point?","Keep last",::apply)
                    else apply()
                }
            }.setNegativeButton("Cancel",null).show()
    }
    private fun confirm(title:String,message:String,positive:String,action:()->Unit) {
        dialog?.dismiss(); dialog=AppDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton(positive) { _,_ -> action() }.setNegativeButton("Cancel",null).show()
        dialog?.window?.setWindowAnimations(0)
    }
    private fun notice(title:String,message:String) {
        dialog?.dismiss(); dialog=AppDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK",null).show()
        dialog?.window?.setWindowAnimations(0)
    }
    private fun safetyInfo()=notice("Help & safety",
        "PLAN\nTap map for waypoints. Tap F1–F3, then tap map and choose ON or OFF. Hold F for output settings. Tap an existing point to edit.\n\n"+
        "START\nArm separately. Mission start checks the rover and HOLD. Review every point and action, then confirm. No automatic arming.\n\n"+
        "ACTIONS\nThe rover confirms stationary HOLD before each function action. Each ON needs a later OFF. M5–M8 remain motor-only. End and STOP request function OFF.\n\n"+
        "STOP\nKeep TeleRC open. STOP, leaving this tab or a fault requests HOLD and OFF. Link loss can leave an output ON: use an independent cutoff. No obstacle avoidance or automatic resume.")
    private fun update() {
        if (!visible) return
        val n=navigation
        summary?.text=if(placement!=null) "$placement selected · tap map, choose ON or OFF" else if(points.isEmpty()) "Tap map to ${if(mission) "add points" else "set a destination"}" else
            "${points.size} ${if(points.size==1) "point" else "points"} · ${if(n.rover==null) "Planning only" else "Rover GPS received"}"
        val connected=link().second
        val next=when {
            !connected -> linkHint()
            n.state==WaypointNavigation.State.IDLE -> "Select points, arm separately, then tap Mission start."
            n.busy -> n.message
            else -> n.blocker(now()) ?: if(n.state==WaypointNavigation.State.READY)
                "Ready. Tap Mission start to confirm the route." else "Tap Mission start to check the rover."
        }
        val title=if(!connected) "Movement locked" else when(n.state) {
            WaypointNavigation.State.IDLE -> "Planning"
            WaypointNavigation.State.CHECKING -> "Checking rover"
            WaypointNavigation.State.READY -> "Ready"
            WaypointNavigation.State.STARTING -> "Starting"
            WaypointNavigation.State.RUNNING -> "Navigating"
            WaypointNavigation.State.ACTION_HOLD -> "Stopping at action point"
            WaypointNavigation.State.ACTIONS -> "Running function action"
            WaypointNavigation.State.HOLDING -> "Waiting for HOLD"
            WaypointNavigation.State.STOPPED -> "Stopped"
            WaypointNavigation.State.COMPLETE -> "Route complete"
            WaypointNavigation.State.ERROR -> "Needs attention"
        }
        val detail=if(n.state==WaypointNavigation.State.ERROR) n.message else next
        val concise=when {
            detail.startsWith("Connect to ArduRover Wi-Fi") -> "Connect ArduRover Wi-Fi in Setup."
            detail.startsWith("Mini rover cannot") -> "Mini rover: planning only.\nUse ArduRover Wi-Fi + GPS."
            detail.startsWith("ELRS handset") -> "Handset link: planning only.\nUse FC Wi-Fi for missions."
            detail.startsWith("This LoRa gateway") -> "Gateway: planning only.\nUse a compatible FC link."
            detail.startsWith("Failsafe readback required:") -> "Verify FC failsafes. Tap for details."
            detail.length>150 -> detail.take(147)+"…"
            else -> detail
        }
        val formatted=android.text.SpannableString("$title\n${if(invalidActions) "Saved actions invalid. Clear route to rebuild." else concise}")
        formatted.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,title.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        readiness?.text=formatted
        readiness?.setOnClickListener { notice("Rover checks",detail) }
        steps?.text=if(n.moving) "Keep app open\nSTOP: HOLD + OFF" else if(n.state==WaypointNavigation.State.CHECKING) "Checking rover\nWait for results" else if(n.busy || n.functionsPendingOff) "Mission paused\nVerify HOLD and OFF" else if(placement!=null) "$placement selected\nTap map\nChoose ON or OFF" else "1. Plan map\n2. Arm separately\n3. Mission start\n4. Confirm"
        for((name,control) in functionControls) {
            control.isEnabled=!n.busy; control.isSelected=placement==name
            control.text=if(placement==name) "$name · MAP" else name
        }
        check?.text=if(n.state==WaypointNavigation.State.CHECKING) "Checking…" else "Mission start"
        check?.isEnabled=!n.busy && placement==null
        stopButton?.isEnabled=n.system!=0
        modeButton?.text=if(mission) "Multiple points ▾" else "Single point ▾"
        modeButton?.isEnabled=!n.busy
        val key=WaypointFunctions.encode(points,actions)+n.busy
        if(key!=routeFingerprint) {
            routeFingerprint=key; routeRows?.removeAllViews()
            if(points.isEmpty()) routeRows?.addView(label("No points yet\nTap the map to add",12f))
            points.forEachIndexed { index,point -> routeRows?.addView(button("${index+1}. ${point.label()}"+
                actions[index].takeIf { it.isNotEmpty() }?.joinToString(" · ","\n") { it.label() }.orEmpty()) {
                editPoint(index)
            }.apply { contentDescription="Waypoint ${index+1}, ${point.label()}; tap to edit" },LinearLayout.LayoutParams(-1,-2)) }
        }
        if(startRequested && startSnapshot!=currentPlan()) {
            startRequested=false; startSnapshot=null; stop("Plan or output assignments changed")
        }
        if(startRequested && n.state==WaypointNavigation.State.READY) {
            startRequested=false
            confirmStart()
        } else if(n.state==WaypointNavigation.State.ERROR || !connected) startRequested=false
        map?.update(points,n.rover,actions.map { it.joinToString(" · ") { a -> a.label() } })
        if(n.moving) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun receive(packet:ByteArray,target:Int,receivedAt:Long) {
        if(now()-receivedAt !in 0..500) { if(navigation.moving) stop("Delayed telemetry"); return }
        WaypointProtocol.decode(packet)?.let { navigation.receive(it,receivedAt,target) }; flush()
    }
    fun stop(reason:String) { startRequested=false; if(navigation.moving || navigation.busy || navigation.state==WaypointNavigation.State.READY || navigation.functionsPendingOff) {
        navigation.stop(reason,now()); flush(); kick(); update()
    } }
    fun disconnected() { startRequested=false; navigation.disconnected(); update() }
    fun leave() { startRequested=false; startSnapshot=null; placement=null; stop("Left Waypoint tab"); visible=false; dialog?.dismiss(); dialog=null; releaseMap()
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        summary=null; readiness=null; check=null; stopButton=null; modeButton=null; routeRows=null; steps=null
    }
    fun onStart() { if(!mapStarted) { map?.start(); mapStarted=map!=null } }
    fun onResume() { foreground=true; if(!mapResumed) { map?.resume(); mapResumed=map!=null }; if(visible || navigation.gcsSessionActive) kick() }
    fun onPause() { startRequested=false; startSnapshot=null; placement=null; foreground=false; dialog?.dismiss(); dialog=null; stop("App left foreground"); if(mapResumed) map?.pause(); mapResumed=false }
    fun onStop() { if(mapStarted) map?.stop(); mapStarted=false }
    fun lowMemory() { map?.lowMemory() }
    private fun releaseMap() { if(mapResumed) map?.pause(); if(mapStarted) map?.stop(); map?.destroy(); map=null; mapStarted=false; mapResumed=false }
    fun close() { leave(); handler.removeCallbacks(ticker) }
}
