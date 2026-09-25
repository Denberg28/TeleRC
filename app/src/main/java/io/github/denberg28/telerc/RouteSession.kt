package io.github.denberg28.telerc

import kotlin.math.*

data class TrackPoint(val latitude: Double, val longitude: Double, val timeMs: Long, val headingDegrees: Double? = null)
data class ControlSample(val timeMs: Long, val steering: Int, val drive: Int)

/** The phone's first accepted fix is the immutable origin of its own track. */
class RouteSession {
    var home: TrackPoint? = null
        private set
    val phone = mutableListOf<TrackPoint>()
    val rover = mutableListOf<TrackPoint>()
    val commands = mutableListOf<ControlSample>()

    fun addPhone(point: TrackPoint, accuracyMeters: Float): Boolean {
        if (!valid(point) || !accuracyMeters.isFinite() || accuracyMeters !in 0f..50f) return false
        val previous = phone.lastOrNull()
        if (previous != null && (point.timeMs <= previous.timeMs ||
                    distance(previous, point) < 3.0 || distance(previous, point) > 50.0 * ((point.timeMs - previous.timeMs) / 1000.0).coerceAtLeast(1.0))) return false
        if (home == null) home = point
        phone.add(point)
        if (phone.size > 20_000) phone.removeAt(1) // retain fixed Home and recent positions
        return true
    }

    fun addRover(point: TrackPoint): Boolean {
        if (!valid(point) || point.headingDegrees != null &&
            (!point.headingDegrees.isFinite() || point.headingDegrees !in 0.0..<360.0)) return false
        val previous = rover.lastOrNull()
        if (previous != null) {
            if (point.timeMs <= previous.timeMs) return false
            val moved = distance(previous, point)
            if (moved > 35.0 * ((point.timeMs - previous.timeMs) / 1000.0).coerceAtLeast(1.0)) return false
            if (moved < 1.0) {
                // Rotation in place changes the symbol without adding a false distance segment.
                if (point.headingDegrees == previous.headingDegrees) return false
                rover[rover.lastIndex] = point
                return true
            }
        }
        rover.add(point)
        if (rover.size > 20_000) rover.removeAt(0)
        return true
    }

    fun addCommand(sample: ControlSample) {
        if (commands.lastOrNull()?.timeMs == sample.timeMs) return
        commands.add(sample)
        if (commands.size > 100_000) commands.removeAt(0)
    }

    fun reset() { home = null; phone.clear(); rover.clear(); commands.clear() }

    fun encode(): String = buildString {
        append("kind,time_ms,latitude,longitude,steer_us,drive_us,heading_deg\n")
        phone.forEach { append("phone,${it.timeMs},${it.latitude},${it.longitude},,,\n") }
        rover.forEach { append("rover,${it.timeMs},${it.latitude},${it.longitude},,,${it.headingDegrees ?: ""}\n") }
        commands.forEach { append("command,${it.timeMs},,,${it.steering},${it.drive},\n") }
    }

    fun decode(csv: String) {
        reset()
        for (line in csv.lineSequence().drop(1).take(140_000)) {
            val cells = line.split(',')
            if (cells.size != 6 && cells.size != 7) continue // existing sessions had no bearing column
            val time = cells[1].toLongOrNull() ?: continue
            when (cells[0]) {
                "phone", "rover" -> {
                    val lat = cells[2].toDoubleOrNull() ?: continue
                    val lon = cells[3].toDoubleOrNull() ?: continue
                    val heading = cells.getOrNull(6)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..<360.0 }
                    val point = TrackPoint(lat, lon, time, if (cells[0] == "rover") heading else null)
                    if (cells[0] == "phone") {
                        if (valid(point) && phone.size < 20_000) { if (home == null) home = point; phone.add(point) }
                    } else if (valid(point) && rover.size < 20_000) rover.add(point)
                }
                "command" -> {
                    val steer = cells[4].toIntOrNull() ?: continue
                    val drive = cells[5].toIntOrNull() ?: continue
                    if (steer in 1000..2000 && drive in 1000..2000 && commands.size < 100_000)
                        commands.add(ControlSample(time, steer, drive))
                }
            }
        }
    }

    private fun valid(p: TrackPoint) = p.latitude.isFinite() && p.longitude.isFinite() &&
        p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0 &&
        (p.latitude != 0.0 || p.longitude != 0.0)

    private fun distance(a: TrackPoint, b: TrackPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 12_742_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
