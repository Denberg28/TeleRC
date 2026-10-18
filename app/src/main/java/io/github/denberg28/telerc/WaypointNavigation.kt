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
    enum class State { IDLE, CHECKING, READY, STARTING, RUNNING, HOLDING, STOPPED, COMPLETE, ERROR }
    data class Dispatch(val bytes: ByteArray, val epoch: Long, val motion: Boolean)
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
    val busy get() = state in setOf(State.STARTING, State.RUNNING, State.HOLDING, State.CHECKING)
    val moving get() = state == State.RUNNING || state == State.STARTING
    private var monitoring = false
    private var sequence = 0
    private var heartbeatAt = -10000L
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
    private val pending = ArrayDeque<Dispatch>()
    private val parameters = mutableMapOf<String, Pair<Float,Long>>()
    private val required = linkedMapOf<String, (Float)->Boolean>(
        "SYSID_MYGCS" to { it == 255f }, "FS_GCS_ENABLE" to { it == 1f },
        "FS_ACTION" to { it == 2f }, "FS_EKF_ACTION" to { it == 1f },
        "FS_GCS_TIMEOUT" to { it in 2f..3f }, "FS_TIMEOUT" to { it in 1f..3f },
        "WP_SPEED" to { it in .1f..1f })

    @Synchronized fun permits(dispatch: Dispatch, now: Long) = dispatch.epoch == epoch &&
        (!dispatch.motion || moving && monitoring && blocker(now)==null)
    /** Stop and the final send share one lock, so a cancelled generation cannot slip past validation. */
    @Synchronized fun transmit(dispatch: Dispatch, now: Long, send: (ByteArray)->Unit): Boolean {
        if (!permits(dispatch,now)) return false
        send(dispatch.bytes)
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
        requireArmed && !armed -> "Arm rover explicitly in Controls or with the transmitter"
        else -> null
    }
    @Synchronized fun check(target: Int, now: Long) {
        if (busy || moving || target !in 1..254) return
        epoch++; pending.clear(); system=target; parameters.clear()
        monitoring=true; state=State.CHECKING; message="Checking telemetry and FC HOLD round trip…"
        modeAck=false; holdConfirmedAt=-10000; holdSentAt=now; holdAttempts=1
        queue(WaypointProtocol.hold(sequence++,system))
        request(now)
    }
    private fun request(now: Long) {
        lastRequest=now
        required.keys.forEach { queue(WaypointProtocol.parameter(sequence++,system,it)) }
        // Read-only telemetry requests. No PARAM_SET, calibration, arming or pre-arm bypass.
        for (id in listOf(1,24,33,193,242,87)) queue(WaypointProtocol.command(sequence++,system,511,id.toFloat(),500000f))
    }
    @Synchronized fun start(points: List<Waypoint>, now: Long): String? {
        val problem = blocker(now) ?: WaypointPlan.problem(points,rover) ?:
            if (state != State.READY || mode != 4 || !fresh(holdConfirmedAt,now,10000)) "Check link and confirm HOLD first" else null
        if (problem != null) return problem
        epoch++; pending.clear(); run=points.toList(); origin=rover; index=0; startedAt=now
        state=State.STARTING; message="Starting waypoint 1/${run.size} · waiting for ACK + Guided + target"
        // Release old RC override only after FC HOLD round trip, before atomic reposition.
        queue(Mavlink.release(sequence++,system,1))
        sendDestination(now)
        return null
    }
    private fun sendDestination(now: Long) {
        destinationAt=now; destinationAck=false; targetEchoAt=-10000; arrivals=0; lastArrivalSample=-10000
        state=State.STARTING
        queue(WaypointProtocol.destination(sequence++,system,run[index]),true)
    }
    @Synchronized fun stop(reason: String, now: Long, result: State = State.STOPPED) {
        if (state == State.HOLDING) return
        epoch++; pending.clear(); monitoring=false; stopState=result; modeAck=false
        state=State.HOLDING; message="$reason · HOLD requested, awaiting rover confirmation"
        holdSentAt=now; holdAttempts=1
        if (system != 0) queue(WaypointProtocol.hold(sequence++,system))
        else { state=State.ERROR; message="$reason · no rover link; stop cannot be confirmed" }
    }
    /** A replacement session never inherits telemetry, checked settings or route authority. */
    @Synchronized fun disconnected() {
        epoch++; pending.clear(); monitoring=false
        if (moving || state == State.HOLDING) { state=State.ERROR; message="Link lost · HOLD unconfirmed; FC failsafe must stop rover" }
        else { state=State.IDLE; message="Disconnected · planning only; Check link again after reconnect" }
        system=0; heartbeatAt=-10000; positionAt=-10000; gpsAt=-10000; ekfAt=-10000; healthAt=-10000
        autopilotGood=false; armed=false; mode=-1; parameters.clear(); home=null; heading=null; rover=null
    }
    @Synchronized fun receive(frame: WaypointProtocol.Frame, now: Long, target: Int) {
        if (frame.system != target || target !in 1..254 || system != 0 && system != target) return
        system=target
        // Check age BEFORE a recovery packet can make a running route appear fresh again.
        if (moving) blocker(now)?.let { stop(it,now,State.ERROR) }
        val p=frame
        when (p.id) {
            0 -> {
                heartbeatAt=now; autopilotGood=p.u8(4)==10 && p.u8(5)==3 && p.u8(7) in 3..4
                armed=p.u8(6) and 128 != 0; mode=p.i32(0)
                if (moving && (!armed || mode !in setOf(4,15) || !autopilotGood)) stop("Rover disarmed or pilot changed mode",now)
                if (state == State.HOLDING && mode == 4 && modeAck && now >= holdSentAt) {
                    state=stopState; message=message.substringBefore(" · HOLD requested") + " · HOLD confirmed"
                } else if ((state == State.CHECKING || state == State.READY) && mode == 4 && modeAck) holdConfirmedAt=now
            }
            1 -> {
                healthAt=now
                val mask=(1 shl 2) or (1 shl 5) or (1 shl 21)
                healthGood=p.i32(0) and mask == mask && p.i32(4) and mask == mask && p.i32(8) and mask == mask
                battery=p.data[30].toInt()
            }
            22 -> { val name=p.text(8,16); val value=p.f32(0)
                if (name in required && value.isFinite()) parameters[name]=value to now }
            24 -> { gpsAt=now; gpsGood=p.u8(28) in 3..6 && p.u8(29) in 6..254 && p.u16(20) in 1..250 }
            33 -> {
                val point=Waypoint(p.i32(4)/1e7,p.i32(8)/1e7)
                if (point.valid) {
                    if (moving && rover?.distance(point)?.let { it > 10 + (now-positionAt).coerceAtLeast(0)/1000.0*3 } == true)
                        stop("GPS position jump",now,State.ERROR)
                    if (moving && hypot(p.u16(20).toShort().toDouble(),p.u16(22).toShort().toDouble()) > 150)
                        stop("Unexpected rover speed",now,State.ERROR)
                    rover=point; positionAt=now; heading=p.u16(26).takeIf { it<36000 }?.div(100.0)
                }
            }
            77 -> {
                // V2 ACK extensions must address this GCS if present.
                if (p.data.size > 8 && p.u8(8) !in setOf(0,255) || p.data.size > 9 && p.u8(9) !in setOf(0,190)) return
                when (p.u16(0)) {
                    176 -> if (state in setOf(State.CHECKING,State.HOLDING)) {
                        if (p.u8(2)==0) modeAck=true
                        else if (p.u8(2)!=5) { monitoring=false; state=State.ERROR; message="FC rejected HOLD; navigation locked" }
                    }
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
    @Synchronized fun tick(now: Long, connected: Boolean, foreground: Boolean) {
        if (!connected) { if (system != 0) disconnected(); return }
        if (!foreground && (moving || monitoring)) stop("App left foreground",now)
        if (monitoring && now-lastBeat >= 1000) { lastBeat=now; queue(WaypointProtocol.heartbeat(sequence++)) }
        if (monitoring && now-lastRequest >= 3000) request(now)
        when (state) {
            State.CHECKING -> {
                val reason=blocker(now,false)
                if (reason==null && mode==4 && modeAck && fresh(holdConfirmedAt,now)) {
                    state=State.READY; message="Checks passed · HOLD confirmed · choose and confirm a route"
                } else if (now-holdSentAt>8000) {
                    monitoring=false; state=State.ERROR
                    message=if (!modeAck) "No HOLD reply · gateway may block navigation; planning only" else reason ?: "FC HOLD not confirmed"
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
                            index++
                            if (index>=run.size) stop("Route complete",now,State.COMPLETE)
                            else sendDestination(now)
                        }
                    }
                }
            }
            State.HOLDING -> if (now-holdSentAt >= 1000) {
                if (holdAttempts<3) { holdAttempts++; holdSentAt=now; queue(WaypointProtocol.hold(sequence++,system)) }
                else { state=State.ERROR; message="HOLD unconfirmed · use transmitter / emergency stop; no auto resume" }
            }
            else -> Unit
        }
    }
}
