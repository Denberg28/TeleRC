package io.github.denberg28.telerc

import kotlin.math.*

// Mission — Relative step directions anchored to the phone's locked initial orientation.
enum class StepDirection(val shortLabel: String, val relativeBearingDeg: Double) {
    FORWARD("FWD", 0.0),
    RIGHT("RIGHT", 90.0),
    BACK("BACK", 180.0),
    LEFT("LEFT", 270.0)
}

/** Phone's locked starting position and compass orientation (0° = North, 90° = East). */
data class PlanOrigin(
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Double,
    val fromPhoneGps: Boolean
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(headingDegrees.isFinite())
    }

    val normalizedHeading: Double get() = ((headingDegrees % 360.0) + 360.0) % 360.0
}

// Control — Open-loop step calibration. Defaults are conservative for raised-wheel bench testing.
// All limits are clamped before generating CH1/CH2 commands; never rely on open-loop timing as a safety stop.
data class StepCalibration(
    val metersPerSecond: Double = 0.65,
    val drivePercent: Int = 35,
    val steerPercent: Int = 45,
    val turn90Ms: Long = 800L,
    val pauseMs: Long = 300L
) {
    val safeSpeedMps: Double get() = if (metersPerSecond.isFinite()) metersPerSecond.coerceIn(0.2, 2.0) else 0.65
    val safeDrivePercent: Int get() = drivePercent.coerceIn(25, 70)
    val safeSteerPercent: Int get() = steerPercent.coerceIn(25, 70)
    val safeTurn90Ms: Long get() = turn90Ms.coerceIn(200L, 3000L)
    val safePauseMs: Long get() = pauseMs.coerceIn(200L, 1500L)

    fun driveDurationMs(meters: Double): Long {
        if (!meters.isFinite() || meters <= 0.0) return 0L
        return ((meters / safeSpeedMps) * 1000.0).roundToLong().coerceIn(200L, 5000L)
    }

    fun driveCommandUs(reverse: Boolean): Int {
        val delta = ControlResponse.GO_RECEIVER_DEADBAND_US +
            ((400 - ControlResponse.GO_RECEIVER_DEADBAND_US) * (safeDrivePercent / 100f)).roundToInt().coerceAtLeast(1)
        return (if (reverse) 1500 - delta else 1500 + delta).coerceIn(1100, 1900)
    }

    fun steerCommandUs(turnRight: Boolean): Int {
        val delta = ControlResponse.GO_RECEIVER_DEADBAND_US +
            ((400 - ControlResponse.GO_RECEIVER_DEADBAND_US) * (safeSteerPercent / 100f)).roundToInt().coerceAtLeast(1)
        return (if (turnRight) 1500 + delta else 1500 - delta).coerceIn(1100, 1900)
    }
}

data class StepCommand(val direction: StepDirection, val meters: Double = 1.0) {
    init {
        require(meters.isFinite() && meters in 0.25..5.0)
    }
}

// Mission — Local geodesic projection from phone origin + orientation into lat/lon waypoints.
object StepGeometry {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun offsetMeters(latitude: Double, longitude: Double, bearingDegrees: Double, meters: Double): Waypoint {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(bearingDegrees.isFinite() && meters.isFinite() && meters >= 0.0)
        val bearing = Math.toRadians(((bearingDegrees % 360.0) + 360.0) % 360.0)
        val north = meters * cos(bearing)
        val east = meters * sin(bearing)
        val latRad = Math.toRadians(latitude)
        val dLat = Math.toDegrees(north / EARTH_RADIUS_M)
        val cosLat = cos(latRad).coerceAtLeast(1e-6)
        val dLon = Math.toDegrees(east / (EARTH_RADIUS_M * cosLat))
        val nextLat = (latitude + dLat).coerceIn(-90.0, 90.0)
        val rawLon = longitude + dLon
        val nextLon = when {
            rawLon > 180.0 -> rawLon - 360.0
            rawLon < -180.0 -> rawLon + 360.0
            else -> rawLon
        }
        return Waypoint(nextLat, nextLon)
    }

    /** Project a sequence of 1m steps from the locked phone origin & initial heading. */
    fun projectWaypoints(origin: PlanOrigin, steps: List<StepCommand>): List<Waypoint> {
        var lat = origin.latitude
        var lon = origin.longitude
        val heading = origin.normalizedHeading
        return steps.map { step ->
            val worldBearing = (heading + step.direction.relativeBearingDeg) % 360.0
            val next = offsetMeters(lat, lon, worldBearing, step.meters)
            lat = next.latitude
            lon = next.longitude
            next
        }
    }

    fun compassLabel(degrees: Double): String {
        val norm = ((degrees % 360.0) + 360.0) % 360.0
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = ((norm + 22.5) / 45.0).toInt() % 8
        return "${norm.roundToInt() % 360}° ${dirs[idx]}"
    }
}

// Control — Safety gate for open-loop step execution without joystick input.
// Mutually exclusive with Wi-Fi mission upload; never auto-arms or runs while disarmed.
internal object GoStepGate {
    fun ready(
        connected: Boolean,
        peerVerified: Boolean,
        missionForeground: Boolean,
        heartbeatFresh: Boolean,
        armed: Boolean,
        stopped: Boolean,
        mayUsePhone: Boolean,
        missionTransferActive: Boolean
    ): Boolean = connected && peerVerified && missionForeground && heartbeatFresh &&
        armed && !stopped && mayUsePhone && !missionTransferActive
}

// Control — Bounded open-loop step runner owned by GoLink under its single lock.
// Every step passes through neutral before/after movement and between turn and drive phases.
internal class StepRunner {
    data class Output(val steer: Int, val drive: Int)

    private data class Segment(
        val stepNumber: Int,
        val label: String,
        val steer: Int,
        val drive: Int,
        val durationMs: Long
    )

    companion object {
        const val MAX_STEPS = MissionPlan.MAX_WAYPOINTS
        const val MAX_TOTAL_DURATION_MS = 180_000L
    }

    var active = false; private set
    var status = "Step test idle"; private set
    private var segments = emptyList<Segment>()
    private var cursor = 0
    private var startedAt = 0L
    private var segmentStartedAt = 0L

    fun begin(steps: List<StepCommand>, calibration: StepCalibration, now: Long) {
        require(!active && steps.size in 1..MAX_STEPS)
        val built = buildSegments(steps, calibration)
        require(built.isNotEmpty())
        segments = built
        cursor = 0
        startedAt = now
        segmentStartedAt = now
        active = true
        status = "Step 1/${steps.size} · ${built.first().label}"
    }

    fun cancel(reason: String) {
        if (active) {
            active = false
            segments = emptyList()
            cursor = 0
            status = reason
        }
    }

    fun poll(now: Long): Output {
        if (!active) return Output(1500, 1500)
        if (now < startedAt || now < segmentStartedAt || now - startedAt >= MAX_TOTAL_DURATION_MS) {
            cancel("Step run timed out · neutral")
            return Output(1500, 1500)
        }
        while (cursor < segments.size) {
            val current = segments[cursor]
            if (now - segmentStartedAt < current.durationMs) {
                val totalSteps = segments.last().stepNumber
                status = "Running ${current.stepNumber}/$totalSteps · ${current.label} (open-loop)"
                return Output(current.steer, current.drive)
            }
            segmentStartedAt += current.durationMs
            cursor++
        }
        active = false
        segments = emptyList()
        cursor = 0
        status = "Step plan complete · neutral"
        return Output(1500, 1500)
    }

    /**
     * Compile phone-frame steps (FWD/BACK/LEFT/RIGHT) into minimal-turn neutral-delimited RC segments.
     * Tracks rover heading relative to the phone's initial orientation (starting at 0°):
     * - Same facing: drive forward directly.
     * - Opposite facing (180°): drive reverse directly without spinning in place.
     * - 90° left/right: pivot 90°, pause at neutral, then drive forward 1 m.
     */
    private fun buildSegments(steps: List<StepCommand>, cal: StepCalibration): List<Segment> {
        val list = mutableListOf<Segment>()
        val pause = cal.safePauseMs
        val turnMs = cal.safeTurn90Ms
        val fwdUs = cal.driveCommandUs(reverse = false)
        val revUs = cal.driveCommandUs(reverse = true)
        val rightUs = cal.steerCommandUs(turnRight = true)
        val leftUs = cal.steerCommandUs(turnRight = false)

        // Initial neutral dwell before any actuator output.
        list.add(Segment(1, "neutral check", 1500, 1500, pause))

        var facingDeg = 0 // 0=FWD, 90=RIGHT, 180=BACK, 270=LEFT in phone origin frame
        steps.forEachIndexed { idx, step ->
            val stepNum = idx + 1
            val targetDeg = step.direction.relativeBearingDeg.roundToInt() % 360
            val diff = ((targetDeg - facingDeg) % 360 + 360) % 360
            val driveMs = cal.driveDurationMs(step.meters)

            when (diff) {
                0 -> {
                    list.add(Segment(stepNum, "${step.direction.shortLabel} ${step.meters}m", 1500, fwdUs, driveMs))
                }
                180 -> {
                    // Drive in reverse directly rather than spinning 180° in place.
                    list.add(Segment(stepNum, "${step.direction.shortLabel} ${step.meters}m (rev)", 1500, revUs, driveMs))
                }
                90 -> {
                    list.add(Segment(stepNum, "turn RIGHT 90°", rightUs, 1500, turnMs))
                    list.add(Segment(stepNum, "neutral pause", 1500, 1500, pause))
                    list.add(Segment(stepNum, "${step.direction.shortLabel} ${step.meters}m", 1500, fwdUs, driveMs))
                    facingDeg = targetDeg
                }
                else -> {
                    // 270° == 90° left
                    list.add(Segment(stepNum, "turn LEFT 90°", leftUs, 1500, turnMs))
                    list.add(Segment(stepNum, "neutral pause", 1500, 1500, pause))
                    list.add(Segment(stepNum, "${step.direction.shortLabel} ${step.meters}m", 1500, fwdUs, driveMs))
                    facingDeg = targetDeg
                }
            }
            // Mandatory neutral dwell after every step.
            list.add(Segment(stepNum, "neutral settle", 1500, 1500, pause))
        }
        return list
    }
}
