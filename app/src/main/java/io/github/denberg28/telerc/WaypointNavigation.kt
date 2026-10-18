package io.github.denberg28.telerc

import kotlin.math.*

data class Waypoint(val latitude: Double, val longitude: Double) {
    val valid get() = latitude.isFinite() && longitude.isFinite() && latitude in -85.0..85.0 &&
        longitude in -180.0..180.0 && (latitude != 0.0 || longitude != 0.0)
    fun distance(other: Waypoint): Double {
        val a = Math.toRadians(latitude); val b = Math.toRadians(other.latitude)
        val d = sin((b-a)/2).pow(2) + cos(a)*cos(b)*sin(Math.toRadians(other.longitude-longitude)/2).pow(2)
        return 12_742_000.0 * asin(sqrt(d.coerceIn(0.0, 1.0)))
    }
    fun label() = java.lang.String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude)
}

/** Bounded local draft only. Never persists running state, authority or confirmations. */
object WaypointPlan {
    const val MAX_POINTS = 20
    const val RADIUS_METERS = 100.0
    fun encode(points: List<Waypoint>) = points.take(MAX_POINTS).filter { it.valid }
        .joinToString(";") { "${it.latitude},${it.longitude}" }
    fun decode(raw: String): List<Waypoint> {
        if (raw.length > 1600) return emptyList()
        return raw.split(';').take(MAX_POINTS).mapNotNull {
            val pair = it.split(','); if (pair.size != 2) return@mapNotNull null
            val a=pair[0].toDoubleOrNull() ?: return@mapNotNull null
            val b=pair[1].toDoubleOrNull() ?: return@mapNotNull null
            Waypoint(a,b).takeIf { p -> p.valid }
        }
    }
    fun problem(points: List<Waypoint>, origin: Waypoint?): String? {
        if (points.isEmpty()) return "Tap the map to choose a destination"
        if (points.size > MAX_POINTS || points.any { !it.valid }) return "Invalid route; maximum 20 points"
        if (origin == null || !origin.valid) return "Waiting for measured rover GPS"
        if (points.any { it.distance(origin) > RADIUS_METERS }) return "Every waypoint must be within 100 m of the start"
        var last: Waypoint = origin; var length = 0.0
        for (point in points) { length += last.distance(point); last=point }
        if (length > 500) return "Route exceeds 500 m"
        return null
    }
}

/** Foreground, phone-supervised Guided route; never changes or uploads the FC's AUTO mission. */
class WaypointNavigation {
    enum class State { IDLE, CHECKING, READY, STARTING, RUNNING, ACTION_HOLD, ACTIONS, HOLDING, STOPPED, COMPLETE, ERROR }
    data class Dispatch(val bytes: ByteArray, val epoch: Long, val motion: Boolean, val function:WaypointFunction?=null, val pwm:Int=0, val cleanup:Boolean=false, val token:Long=0, val createdAt:Long=0) {
        val gcsHeartbeat get() = bytes.size==17 && bytes[5]==0.toByte()
    }
    var state = State.IDLE; private set
    var message = "Plan now · navigation requires ArduRover GPS + compass and a compatible link"; private set
    var system = 0; private set
    var rover: Waypoint? = null; private set
    var heading: Double? = null; private set
    var home: Waypoint? = null; private set
    var armed = false; private set
    var index = 0; private set
    var epoch = 0L; private set
    var mode = -1; private set
    val busy get() = state in setOf(State.STARTING, State.RUNNING, State.ACTION_HOLD, State.ACTIONS, State.HOLDING, State.CHECKING)
    val moving get() = state in setOf(State.RUNNING,State.STARTING,State.ACTION_HOLD,State.ACTIONS)
    var gcsSessionActive = false; private set
    private var supervisionForeground = false
    private var monitoring = false
    private var sequence = 0
    private var heartbeatAt = -10000L
    private var positionClock = -1L
    private var gpsClock = -1L
    private var positionAt = -10000L
    private var homeAt = -10000L
    private var gpsAt = -10000L
    private var ekfAt = -10000L
    private var healthAt = -10000L
    private var gpsGood = false
    private var ekfGood = false
    private var healthGood = false
    private var battery = -1
    private var autopilotGood = false
    private var modeAck = false
    private var holdConfirmedAt = -10000L
    private var holdSentAt = 0L
    private var destinationAt = 0L
    private var destinationAck = false
    private var targetEchoAt = -10000L
    private var lastBeat = -10000L
    private var lastRequest = -10000L
    private var startedAt = 0L
    private var lastArrivalSample = -10000L
    private var arrivals = 0
    private var holdAttempts = 0
    private var stopState = State.STOPPED
    private var origin: Waypoint? = null
    private var run = emptyList<Waypoint>()
    private var functions=emptyList<WaypointFunction>()
    private var actions=emptyList<List<WaypointAction>>()
    private var initializing=false
    private var speedCm=Int.MAX_VALUE
    private var stationarySamples=0
    private var stationarySince=-1L
    private var outputClock=-1L
    private var holdObserved=false
    private var auxTouched=false
    val functionsPendingOff get()=auxTouched
    private val auxiliary=WaypointAuxiliary { f,pwm,attempt,cleanup,token ->
        if(pending.size<40) pending.addLast(Dispatch(WaypointProtocol.servo(sequence++,system,f.output,pwm,attempt),epoch,!cleanup,f,pwm,cleanup,token,auxNow))
    }
    private var auxNow=0L
    private val pending = ArrayDeque<Dispatch>()
    private val parameters = mutableMapOf<String, Pair<Float,Long>>()
    private val required = linkedMapOf<String, (Float)->Boolean>(
        "SYSID_MYGCS" to { it == 255f }, "FS_GCS_ENABLE" to { it == 1f },
        "FS_ACTION" to { it == 2f }, "FS_EKF_ACTION" to { it == 1f },
        "FS_GCS_TIMEOUT" to { it in 2f..3f }, "FS_TIMEOUT" to { it in 1f..3f },
        "WP_SPEED" to { it in .1f..1f })

    @Synchronized fun permits(dispatch: Dispatch, now: Long) = dispatch.epoch == epoch &&
        (!dispatch.motion || moving && monitoring && supervisionForeground && blocker(now)==null && (dispatch.function!=null || state in setOf(State.STARTING,State.RUNNING))) &&
        (!dispatch.gcsHeartbeat || beaconAllowed(now)) &&
        (dispatch.function==null || dispatch.token==auxiliary.generation && auxiliary.busy &&
            fresh(dispatch.createdAt,now,500) && functionReady(dispatch.function,now) &&
            if(dispatch.cleanup) state==State.HOLDING && dispatch.pwm==dispatch.function.off
            else state==State.ACTIONS && mode==4)
    /** Stop and the final send share one lock, so a cancelled generation cannot slip past validation. */
    @Synchronized fun transmit(dispatch: Dispatch, now: Long, send: (ByteArray)->Unit): Boolean {
        if (!permits(dispatch,now)) return false
        send(dispatch.bytes)
        if(dispatch.function!=null) auxiliary.transmitted(dispatch.token,now)
        return true
    }
    private fun queue(bytes: ByteArray, motion: Boolean = false) {
        if (pending.size < 40) pending.addLast(Dispatch(bytes,epoch,motion))
    }
    @Synchronized fun drain(): List<Dispatch> = pending.toList().also { pending.clear() }
    private fun fresh(at: Long, now: Long, limit: Long = 2000) = now-at in 0..limit
    @Synchronized fun blocker(now: Long, requireArmed: Boolean = true): String? = when {
        !autopilotGood -> "Requires ArduPilot ground rover (not mini rover, boat or aircraft)"
        !fresh(heartbeatAt, now) -> "Rover heartbeat missing or stale"
        !fresh(positionAt, now) || rover == null || heading == null -> "Measured rover position / heading missing or stale"
        !fresh(gpsAt,now) || !gpsGood -> "GPS needs 3D fix, 6+ satellites and HDOP ≤ 2.5"
        !fresh(ekfAt,now) || !ekfGood -> "EKF position / heading not healthy"
        !fresh(healthAt,now) || !healthGood -> "GPS, compass or AHRS health missing"
        battery < 20 -> "Battery telemetry must report at least 20%"
        home == null || !fresh(homeAt,now,10000) -> "Waiting for flight-controller Home"
        required.any { (key, test) -> parameters[key]?.let { fresh(it.second,now,10000) && test(it.first) } != true } ->
            "Failsafe readback required: " + required.filter { (key,test) ->
                parameters[key]?.let { fresh(it.second,now,10000) && test(it.first) } != true }.keys.joinToString(", ")
        functions.any { !functionReady(it,now) } -> "Verify dedicated SERVOx_FUNCTION=0 and PWM limits for " + functions.filter { !functionReady(it,now) }.joinToString { it.name }
        requireArmed && !armed -> "Arm rover explicitly in Controls or with the transmitter"
        else -> null
    }
    private fun functionReady(f:WaypointFunction,now:Long):Boolean {
        fun value(suffix:String)=parameters["SERVO${f.output}_$suffix"]?.takeIf { fresh(it.second,now,10000) }?.first
        val low=value("MIN"); val high=value("MAX")
        return f.valid && value("FUNCTION")==0f && low!=null && high!=null && low<=high &&
            f.off.toFloat() in low..high && f.on.toFloat() in low..high
    }
    private fun auxiliaryBegin(list:List<WaypointAction>,now:Long,cleanup:Boolean=false) {
        auxNow=now; auxiliary.begin(list,functions,now,cleanup)
    }
    @Synchronized fun check(target: Int, now: Long, bindings:List<WaypointFunction> = emptyList()) {
        if (busy || moving || target !in 1..254) return
        if(auxTouched) { message="Function OFF unconfirmed. Restore the link and press STOP to retry OFF."; state=State.ERROR; return }
        if(bindings.size>3 || bindings.any { !it.valid } || bindings.map { it.output }.distinct().size!=bindings.size || bindings.map { it.name }.distinct().size!=bindings.size) {
            message="Invalid function assignments"; state=State.ERROR; return
        }
        functions=bindings.toList(); actions=emptyList(); auxiliary.cancel(); auxTouched=false
        epoch++; pending.clear(); system=target; parameters.clear()
        monitoring=true; state=State.CHECKING; message="Checking telemetry and FC HOLD round trip…"
        modeAck=false; holdObserved=false; stationarySamples=0; holdConfirmedAt=-10000; holdSentAt=now; holdAttempts=1
        queue(WaypointProtocol.hold(sequence++,system))
        request(now)
    }
    private fun request(now: Long) {
        lastRequest=now
        (required.keys + functions.flatMap { f -> listOf("SERVO${f.output}_FUNCTION","SERVO${f.output}_MIN","SERVO${f.output}_MAX") })
            .forEach { queue(WaypointProtocol.parameter(sequence++,system,it)) }
        // Read-only telemetry requests. No PARAM_SET, calibration, arming or pre-arm bypass.
        for (id in (listOf(1,24,33,193,242,87)+if(functions.isNotEmpty()) listOf(36) else emptyList())) queue(WaypointProtocol.command(sequence++,system,511,id.toFloat(),500000f))
    }
    @Synchronized fun start(points: List<Waypoint>, now: Long, sequenceActions:List<List<WaypointAction>> = List(points.size) { emptyList() }, bindings:List<WaypointFunction> = emptyList()): String? {
        val problem = WaypointFunctions.problem(points,sequenceActions,bindings) ?:
            if(bindings!=functions) "Function assignments changed; check again" else blocker(now) ?: WaypointPlan.problem(points,rover) ?:
            if (state != State.READY || mode != 4 || !fresh(holdConfirmedAt,now,10000)) "Check link and confirm HOLD first" else null
        if (problem != null) return problem
        epoch++; pending.clear(); run=points.toList(); origin=rover; index=0; startedAt=now
        state=State.STARTING; message="Starting waypoint 1/${run.size} · waiting for ACK + Guided + target"
        // Release old RC override only after FC HOLD round trip, before atomic reposition.
        queue(Mavlink.release(sequence++,system,1))
        actions=sequenceActions.map { it.toList() }; auxTouched=functions.isNotEmpty()
        if(functions.isNotEmpty()) {
            initializing=true; state=State.ACTION_HOLD; stationarySamples=0; holdObserved=false
            modeAck=false; holdSentAt=now; holdAttempts=1
            queue(WaypointProtocol.hold(sequence++,system))
        } else sendDestination(now)
        return null
    }
    private fun sendDestination(now: Long) {
        destinationAt=now; destinationAck=false; targetEchoAt=-10000; arrivals=0; lastArrivalSample=-10000
        state=State.STARTING
        queue(WaypointProtocol.destination(sequence++,system,run[index]),true)
    }
    @Synchronized fun stop(reason: String, now: Long, result: State = State.STOPPED) {
        if (state == State.HOLDING) return
        epoch++; pending.clear(); monitoring=false; gcsSessionActive=false; stopState=result; modeAck=false; holdObserved=false
        auxiliary.cancel()
        state=State.HOLDING; message="$reason · HOLD requested, awaiting rover confirmation"
        holdSentAt=now; holdAttempts=1
        if (system != 0) {
            queue(WaypointProtocol.hold(sequence++,system))
            if(auxTouched) { request(now); auxiliaryBegin(functions.map { WaypointAction(it.name,false) },now,true) }
        }
        else { state=State.ERROR; message="$reason · no rover link; stop cannot be confirmed" }
    }
    /** A replacement session never inherits telemetry, checked settings or route authority. */
    @Synchronized fun disconnected() {
        epoch++; pending.clear(); monitoring=false; gcsSessionActive=false; auxiliary.cancel()
        if (moving || state == State.HOLDING || auxTouched) { state=State.ERROR; message="Link lost · HOLD${if(auxTouched) " / function OFF" else ""} unconfirmed; use independent cutoff" }
        else { state=State.IDLE; message="Disconnected · planning only; Check link again after reconnect" }
        outputClock=-1L; stationarySamples=0; if(!auxTouched) functions=emptyList(); actions=emptyList()
        system=0; heartbeatAt=-10000; positionAt=-10000; gpsAt=-10000; ekfAt=-10000; healthAt=-10000
        autopilotGood=false; armed=false; mode=-1; positionClock=-1; gpsClock=-1; parameters.clear(); home=null; heading=null; rover=null
    }
    @Synchronized fun receive(frame: WaypointProtocol.Frame, now: Long, target: Int) {
        if (frame.system != target || target !in 1..254 || system != 0 && system != target) return
        system=target
        // Check age BEFORE a recovery packet can make a running route appear fresh again.
        if (moving) blocker(now)?.let { stop(it,now,State.ERROR) }
        auxNow=now
        val p=frame
        when (p.id) {
            0 -> {
                heartbeatAt=now; autopilotGood=p.u8(4)==10 && p.u8(5)==3 && p.u8(7) in 3..4
                armed=p.u8(6) and 128 != 0; mode=p.i32(0)
                if (moving && (!armed || mode !in setOf(4,15) || !autopilotGood)) stop("Rover disarmed or pilot changed mode",now)
                if (mode==4 && modeAck && now>=holdSentAt) {
                    holdObserved=true
                    if(state==State.CHECKING || state==State.READY) { holdConfirmedAt=now; gcsSessionActive=true }
                    finishHold()
                }

            }
            1 -> {
                healthAt=now
                val mask=(1 shl 2) or (1 shl 5) or (1 shl 21)
                healthGood=p.i32(0) and mask == mask && p.i32(4) and mask == mask && p.i32(8) and mask == mask
                battery=p.data[30].toInt()
            }
            22 -> { val name=p.text(8,16); val value=p.f32(0)
                if ((name in required || functions.any { name in listOf("SERVO${it.output}_FUNCTION","SERVO${it.output}_MIN","SERVO${it.output}_MAX") }) && value.isFinite()) parameters[name]=value to now }
            24 -> {
                val stamp=p.i64(0)
                if (stamp<=0 || stamp<=gpsClock) return
                gpsClock=stamp; gpsAt=now; gpsGood=p.u8(28) in 3..6 && p.u8(29) in 6..254 && p.u16(20) in 1..250 }
            33 -> {
                val stamp=p.i32(0).toLong() and 0xffffffffL
                if (positionClock>=0) {
                    val delta=(stamp-positionClock) and 0xffffffffL
                    if (delta==0L) return
                    if (delta>0x7fffffffL) {
                        if (moving) stop("FC clock reset / reordered position",now,State.ERROR)
                        return
                    }
                }
                positionClock=stamp
                val point=Waypoint(p.i32(4)/1e7,p.i32(8)/1e7)
                if (point.valid) {
                    if (moving && rover?.distance(point)?.let { it > 10 + (now-positionAt).coerceAtLeast(0)/1000.0*3 } == true)
                        stop("GPS position jump",now,State.ERROR)
                    if (moving && hypot(p.u16(20).toShort().toDouble(),p.u16(22).toShort().toDouble()) > 150)
                        stop("Unexpected rover speed",now,State.ERROR)
                    speedCm=hypot(p.u16(20).toShort().toDouble(),p.u16(22).toShort().toDouble()).toInt()
                    if(state==State.ACTION_HOLD && now>holdSentAt) {
                        if(mode==4 && speedCm<=10) { if(stationarySamples==0) stationarySince=now; stationarySamples++ }
                        else { stationarySamples=0; stationarySince=-1 }
                    }
                    rover=point; positionAt=now; heading=p.u16(26).takeIf { it<36000 }?.div(100.0)
                }
            }
            36 -> {
                // Port zero covers the first output bank. Extensions are required for outputs 9–16.
                if(p.u8(20)!=0) return
                val stamp=p.i32(0).toLong() and 0xffffffffL
                if(stamp==0L) return
                if(outputClock>=0) { val delta=(stamp-outputClock) and 0xffffffffL; if(delta==0L || delta>0x7fffffffL) return }
                outputClock=stamp
                for(f in functions) {
                    val offset=if(f.output<=8) 4+(f.output-1)*2 else 21+(f.output-9)*2
                    if(offset+2<=p.data.size) auxiliary.output(f.output,p.u16(offset),now)
                }
                finishHold()
            }
            77 -> {
                // V2 ACK extensions must address this GCS if present.
                if (p.data.size > 8 && p.u8(8) !in setOf(0,255) || p.data.size > 9 && p.u8(9) !in setOf(0,190)) return
                when (p.u16(0)) {
                    176 -> if (state in setOf(State.CHECKING,State.HOLDING,State.ACTION_HOLD)) {
                        if (p.u8(2)==0) modeAck=true
                        else if (p.u8(2)!=5) {
                            if(auxTouched && state!=State.HOLDING) stop("FC rejected HOLD",now,State.ERROR)
                            else if(state!=State.HOLDING) { monitoring=false; state=State.ERROR; message="FC rejected HOLD; navigation locked" }
                        }
                    }
                    183 -> { auxiliary.ack(p.u8(2),now); finishHold() }
                    192 -> if (state == State.STARTING) {
                        if (p.u8(2)==0) destinationAck=true
                        else if (p.u8(2)!=5) stop("Destination rejected (${p.u8(2)})",now,State.ERROR)
                    }
                }
            }
            87 -> if (moving) {
                val point=Waypoint(p.i32(4)/1e7,p.i32(8)/1e7)
                if (p.u8(50) in setOf(0,3,5,6) && point.valid && point.distance(run[index]) < 1) targetEchoAt=now
                else if (point.valid && state == State.RUNNING) stop("Flight-controller target changed",now,State.ERROR)
            }
            193 -> { ekfAt=now; val flags=p.u16(20)
                ekfGood=flags and 19 == 19 && flags and (128 or 1024) == 0 &&
                    listOf(p.f32(0),p.f32(4),p.f32(12)).all { it.isFinite() && it in 0f..0.8f } }
            242 -> Waypoint(p.i32(0)/1e7,p.i32(4)/1e7).takeIf { it.valid }?.let { home=it; homeAt=now }
        }
    }
    private fun finishHold() {
        if(state==State.HOLDING && holdObserved && !auxiliary.busy && auxiliary.failure==null) {
            auxTouched=false; gcsSessionActive=true; state=stopState
            message=message.substringBefore(" · HOLD requested")+" · HOLD${if(functions.isNotEmpty()) " / OFF" else ""} confirmed"
        }
    }
    private fun nextDestination(now:Long) {
        index++
        if(index>=run.size) stop("Route complete",now,State.COMPLETE) else sendDestination(now)
    }
    private fun beaconAllowed(now: Long): Boolean = supervisionForeground &&
        (monitoring && (!moving || blocker(now)==null) ||
        gcsSessionActive && autopilotGood && fresh(heartbeatAt,now) && mode in setOf(0,1,3,4,7) && state!=State.HOLDING)
    @Synchronized fun tick(now: Long, connected: Boolean, foreground: Boolean, visible: Boolean = true) {
        supervisionForeground=foreground; auxNow=now
        if (!connected) { if (system != 0) disconnected(); return }
        if ((!foreground || !visible) && (moving || monitoring)) stop("App left foreground or Waypoint tab",now)
        // After confirmed HOLD only, retain ordinary foreground GCS liveness in manual/HOLD modes.
        // Otherwise returning to Controls would trigger the newly enabled FC GCS failsafe.
        // This never sends destinations/RC/ARM; an unconfirmed stop withdraws all beacons.
        if (beaconAllowed(now) && now-lastBeat >= 1000) { lastBeat=now; queue(WaypointProtocol.heartbeat(sequence++)) }
        if ((monitoring || state==State.HOLDING && auxiliary.busy) && now-lastRequest >= 3000) request(now)
        auxiliary.tick(now)
        if(auxiliary.failure!=null && moving) { stop(auxiliary.failure!!,now,State.ERROR); return }
        if(auxiliary.failure!=null && state==State.HOLDING) { state=State.ERROR; message="HOLD / function OFF unconfirmed · use independent cutoff"; return }
        when (state) {
            State.CHECKING -> {
                val reason=blocker(now,false)
                if(now-holdSentAt>=holdAttempts*1000L && holdAttempts<3 && !holdObserved) { holdAttempts++; queue(WaypointProtocol.hold(sequence++,system)) }
                if (reason==null && mode==4 && modeAck && fresh(holdConfirmedAt,now)) {
                    state=State.READY; message="Checks passed · HOLD confirmed · choose and confirm a route"
                } else if (now-holdSentAt>8000) {
                    monitoring=false; state=State.ERROR
                    message=if (!modeAck) "No HOLD reply · gateway may block navigation; planning only" else reason ?: "FC HOLD not confirmed"
                }
            }
            State.ACTION_HOLD, State.ACTIONS -> {
                val reason=blocker(now) ?: if(origin?.distance(rover!!)!!>100) "100 m operating limit exceeded" else null
                if(reason!=null) { stop(reason,now,State.ERROR); return }
                if(state==State.ACTION_HOLD && now-holdSentAt>=holdAttempts*1000L && holdAttempts<3 && !holdObserved) {
                    holdAttempts++; queue(WaypointProtocol.hold(sequence++,system))
                }
                if(now-startedAt>600000) { stop("Route timeout",now,State.ERROR); return }
                if(state==State.ACTION_HOLD) {
                    message="Point ${if(initializing) "start" else (index+1).toString()} · waiting for HOLD and stationary telemetry"
                    if(modeAck && holdObserved && mode==4 && stationarySamples>=2 && now-stationarySince>=250) {
                        state=State.ACTIONS
                        auxiliaryBegin(if(initializing) functions.map { WaypointAction(it.name,false) } else actions[index],now)
                    } else if(now-holdSentAt>=4000) { stop("Stationary HOLD unconfirmed",now,State.ERROR); return }
                } else {
                    if(mode!=4 || speedCm>10) { stop("Rover moved during function action",now,State.ERROR); return }
                    message="Point ${index+1} · ${auxiliary.label} · waiting for ACK and PWM"
                    if(!auxiliary.busy) {
                        if(initializing) { initializing=false; sendDestination(now) } else nextDestination(now)
                    }
                }
            }
            State.READY -> {
                if (mode!=4 || blocker(now,false)!=null) {
                    state=State.IDLE; monitoring=false; message="Readiness changed · Check link again"
                }
            }
            State.STARTING, State.RUNNING -> {
                val reason=blocker(now) ?: if (origin?.distance(rover!!)!! > 100) "100 m operating limit exceeded" else null
                if (reason != null) { stop(reason,now,State.ERROR); return }
                if (now-startedAt > 600000 || now-destinationAt > 120000) { stop("Route / waypoint timeout",now,State.ERROR); return }
                if (state == State.STARTING) {
                    if (destinationAck && mode==15 && fresh(targetEchoAt,now)) { state=State.RUNNING; message="Waypoint ${index+1}/${run.size} · Guided · 0.5 m/s" }
                    else if (now-destinationAt>3000) stop("Destination / mode not confirmed",now,State.ERROR)
                } else {
                    if (mode!=15 || !fresh(targetEchoAt,now,3000)) { stop("Guided target / mode lost",now,State.ERROR); return }
                    if (positionAt != lastArrivalSample) {
                        lastArrivalSample=positionAt
                        arrivals=if (rover!!.distance(run[index])<=3) arrivals+1 else 0
                        if (arrivals>=3 && now-destinationAt>=2000) {
                            if(actions[index].isNotEmpty()) {
                                epoch++; pending.clear(); initializing=false; state=State.ACTION_HOLD
                                modeAck=false; holdObserved=false; stationarySamples=0; holdSentAt=now; holdAttempts=1
                                queue(WaypointProtocol.hold(sequence++,system))
                            } else nextDestination(now)
                        }
                    }
                }
            }
            State.HOLDING -> if (!holdObserved && now-holdSentAt >= 1000) {
                if (holdAttempts<3) { holdAttempts++; holdSentAt=now; queue(WaypointProtocol.hold(sequence++,system)) }
                else if (!auxiliary.busy) { state=State.ERROR; message="HOLD${if(auxTouched) " / function OFF" else ""} unconfirmed · use transmitter / emergency stop; no auto resume" }
            }
            else -> Unit
        }
    }
}
