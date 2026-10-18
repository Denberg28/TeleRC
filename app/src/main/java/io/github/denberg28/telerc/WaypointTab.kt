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
    private val send: (WaypointNavigation.Dispatch)->Unit) {
    val navigation=WaypointNavigation()
    private val handler=Handler(Looper.getMainLooper())
    private var visible=false
    private var foreground=false
    private var mapStarted=false
    private var mapResumed=false
    private var map: WaypointMap?=null
    private var dialog: AlertDialog?=null
    private var summary: TextView?=null
    private var readiness: TextView?=null
    private var modeButton: Button?=null
    private var go: Button?=null
    private var check: Button?=null
    private var stopButton: Button?=null
    private var routeRows: LinearLayout?=null
    private var routeFingerprint=""
    private val prefs=activity.getSharedPreferences("waypoint_route",0)
    private var mission=prefs.getBoolean("multiple",false)
    private var points=WaypointPlan.decode(prefs.getString("draft","").orEmpty()).let { if(mission) it else it.takeLast(1) }
    private val ticker=object:Runnable { override fun run() {
        navigation.tick(now(),link().second,foreground && visible)
        flush(); update()
        if (visible || navigation.busy) handler.postDelayed(this,250)
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
    private fun save() { prefs.edit().putString("draft",WaypointPlan.encode(points)).putBoolean("multiple",mission).apply(); update() }
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
        modeButton=button(if(mission) "Mission · multiple" else "Go to · single") { changeMode() }
        routePanel.addView(modeButton,LinearLayout.LayoutParams(-1,dp(48)))
        routeRows=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        routePanel.addView(ScrollView(activity).apply { addView(routeRows) },LinearLayout.LayoutParams(-1,0,1f))
        val undo=button("Undo / clear") {
            if (!navigation.busy && points.isNotEmpty()) { points=points.dropLast(1); save() }
        }.apply { contentDescription="Remove last waypoint; hold to clear route"
            setOnLongClickListener {
                if (!navigation.busy) confirm("Clear route?","Remove all saved waypoints?","Clear") { points=emptyList(); save() }
                true
            }
        }
        routePanel.addView(undo,LinearLayout.LayoutParams(-1,dp(48)))
        go=button("Go / start",true) { confirmStart() }.apply { contentDescription="Confirm and start waypoint route" }
        row.addView(column(dropdown,routePanel,go!!),LinearLayout.LayoutParams(0,-1,1.6f).apply { rightMargin=dp(8) })
        val center=panel()
        summary=label("Planning · no rover GPS",12f).apply { maxLines=2 }
        center.addView(summary,LinearLayout.LayoutParams(-1,dp(40)))
        val mapStatus=label("Planning reference · no rover GPS. Map tiles require internet or cache.",10f)
        map=WaypointMap(activity,::select) { mapStatus.text=it }
        center.addView(map!!.view,LinearLayout.LayoutParams(-1,0,1f))
        center.addView(mapStatus,LinearLayout.LayoutParams(-1,dp(36)))
        val mapActions=LinearLayout(activity)
        mapActions.addView(button("Locate rover / route") { map?.locate() },LinearLayout.LayoutParams(0,dp(48),1f))
        mapActions.addView(button("Coordinates") { coordinates() },LinearLayout.LayoutParams(0,dp(48),1f).apply { leftMargin=dp(4) })
        center.addView(mapActions)
        row.addView(center,LinearLayout.LayoutParams(0,-1,3.8f).apply { rightMargin=dp(8) })
        val safety=panel()
        readiness=label("",12f).apply { gravity=Gravity.TOP or Gravity.START; setPadding(dp(4),dp(4),dp(4),dp(4)) }
        safety.addView(ScrollView(activity).apply { addView(readiness) },LinearLayout.LayoutParams(-1,0,1f))
        safety.addView(button("Safety / compatibility") { safetyInfo() },LinearLayout.LayoutParams(-1,dp(48)))
        check=button("Check link",true) {
            val (target,connected)=link()
            if (!connected || target==0) notice("Navigation unavailable","Connect in Setup first. Mini rover and existing filtering gateways support planning only. A transparent MAVLink Wi-Fi link to ArduRover is required.")
            else { navigation.check(target,now()); flush(); kick() }
        }
        stopButton=button("STOP · HOLD",true) { navigation.stop("Stopped by operator",now()); flush(); kick(); update() }.apply {
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
        if (!point.valid || navigation.busy) return
        if (mission) {
            if (points.size>=WaypointPlan.MAX_POINTS) { notice("Route full","Maximum 20 waypoints."); return }
            points=points+point; save()
        } else {
            points=listOf(point); save()
            confirmStart()
        }
    }
    private fun confirmStart() {
        if (navigation.busy || points.isEmpty()) return
        val problem=navigation.blocker(now()) ?: WaypointPlan.problem(points,navigation.rover) ?:
            if(navigation.state!=WaypointNavigation.State.READY) "Tap Check link before starting" else null
        if(problem!=null) { notice("Route saved · movement locked",problem); return }
        val snapshot=points.toList()
        confirm(if(mission) "Start mission?" else "Proceed to location?",
            "${snapshot.size} waypoint(s) · 0.5 m/s · 100 m operating radius\n"+
            snapshot.mapIndexed { i,p -> "${i+1}. ${p.label()}" }.joinToString("\n")+
            "\n\nKeep TeleRC foreground and connected. STOP, leaving this tab or loss of readiness requests HOLD. Rover remains armed. Check the path for people, traffic and obstacles; no obstacle avoidance.","Proceed") {
                if (!visible || !foreground || points!=snapshot) return@confirm
                navigation.start(snapshot,now())?.let { notice("Cannot start",it) }
                flush(); kick(); update()
            }
    }
    private fun coordinates() {
        if(navigation.busy) return
        val input=EditText(activity).apply { hint="Latitude, longitude"; setSingleLine(); contentDescription="Waypoint latitude and longitude" }
        dialog?.dismiss()
        dialog=AlertDialog.Builder(activity).setTitle("Add waypoint coordinates").setView(input)
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
        fun apply() { mission=!mission; if(!mission && points.size>1) points=points.takeLast(1); save() }
        if(mission && points.size>1) confirm("Use single destination?","Keep only the last waypoint?","Keep last",::apply) else apply()
    }
    private fun confirm(title:String,message:String,positive:String,action:()->Unit) {
        dialog?.dismiss(); dialog=AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton(positive) { _,_ -> action() }.setNegativeButton("Cancel",null).show()
        dialog?.window?.setWindowAnimations(0)
    }
    private fun notice(title:String,message:String) {
        dialog?.dismiss(); dialog=AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK",null).show()
        dialog?.window?.setWindowAnimations(0)
    }
    private fun safetyInfo()=notice("Waypoint safety", "ArduRover ground vehicles only. HGLRC M100 GPS + compass must feed the flight controller with calibrated compass and healthy EKF. GPS alone on the current mini rover sketch cannot navigate.\n\nExisting TeleRC gateways filter navigation packets. A compatible transparent MAVLink link is required; Check link must receive a real FC HOLD acknowledgement and heartbeat.\n\nRead-only checks: SYSID_MYGCS=255; FS_GCS_ENABLE=1; FS_ACTION=2 (Hold); FS_EKF_ACTION=1; FS_GCS_TIMEOUT=2–3 s; FS_TIMEOUT=1–3 s; WP_SPEED=0.1–1 m/s. Configure and bench-test in Mission Planner. TeleRC does not write these settings.\n\nNavigation needs fresh rover GPS/heading, 3D fix, ≥6 satellites, HDOP≤2.5, healthy EKF/compass/GPS and battery≥20%. Maximum 20 points, 100 m radius, 500 m route; 120 s per point, 10 min total.\n\nPhone-supervised Guided route, not an uploaded AUTO mission. No automatic arm, reconnect resume, return-home or obstacle avoidance. STOP requests HOLD; physical stop depends on FC/driver setup. Use an independent transmitter/emergency stop. HOLD without confirmation is not proof of a stop.")
    private fun update() {
        if (!visible) return
        val n=navigation
        summary?.text=if(n.rover==null) "Planning reference · no measured rover GPS" else
            "${n.state} · ${n.rover!!.label()} · ${points.size} point(s)"
        readiness?.text="${n.message}\n\n${n.blocker(now()) ?: "Ready · explicit confirmation required"}\n\n"+
            "${if(n.armed) "ARMED" else "DISARMED / unknown"} · ${when(n.mode) {4->"HOLD";15->"GUIDED";else->"Mode ${n.mode}"}}\n\n0.5 m/s · 3 m arrival radius\nStop on completion · no auto resume"
        go?.text=if(mission) "Start mission" else "Go to location"
        go?.isEnabled=points.isNotEmpty() && !n.busy
        check?.isEnabled=!n.busy
        stopButton?.isEnabled=n.system!=0
        modeButton?.text=if(mission) "Mission · multiple" else "Go to · single"
        modeButton?.isEnabled=!n.busy
        val key=WaypointPlan.encode(points)+n.busy
        if(key!=routeFingerprint) {
            routeFingerprint=key; routeRows?.removeAllViews()
            if(points.isEmpty()) routeRows?.addView(label("Tap map or enter coordinates\n\nRoute stays saved offline",12f))
            points.forEachIndexed { index,point -> routeRows?.addView(button("${index+1}. ${point.label()}") {
                if(!navigation.busy) confirm("Remove waypoint ${index+1}?",point.label(),"Remove") { points=points.filterIndexed { i,_ -> i!=index }; save() }
            }.apply { contentDescription="Waypoint ${index+1}, ${point.label()}; tap to remove" },LinearLayout.LayoutParams(-1,-2)) }
        }
        map?.update(points,n.rover)
        if(n.moving) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun receive(packet:ByteArray,target:Int,receivedAt:Long) {
        if(now()-receivedAt !in 0..500) { if(navigation.moving) stop("Delayed telemetry"); return }
        WaypointProtocol.decode(packet)?.let { navigation.receive(it,receivedAt,target) }; flush()
    }
    fun stop(reason:String) { if(navigation.moving || navigation.busy || navigation.state==WaypointNavigation.State.READY) {
        navigation.stop(reason,now()); flush(); kick(); update()
    } }
    fun disconnected() { navigation.disconnected(); update() }
    fun leave() { stop("Left Waypoint tab"); visible=false; dialog?.dismiss(); dialog=null; releaseMap()
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        summary=null; readiness=null; check=null; stopButton=null; go=null; modeButton=null; routeRows=null
    }
    fun onStart() { if(!mapStarted) { map?.start(); mapStarted=map!=null } }
    fun onResume() { foreground=true; if(!mapResumed) { map?.resume(); mapResumed=map!=null }; if(visible) kick() }
    fun onPause() { foreground=false; dialog?.dismiss(); dialog=null; stop("App left foreground"); if(mapResumed) map?.pause(); mapResumed=false }
    fun onStop() { if(mapStarted) map?.stop(); mapStarted=false }
    fun lowMemory() { map?.lowMemory() }
    private fun releaseMap() { if(mapResumed) map?.pause(); if(mapStarted) map?.stop(); map?.destroy(); map=null; mapStarted=false; mapResumed=false }
    fun close() { leave(); handler.removeCallbacks(ticker) }
}
