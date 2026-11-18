package io.github.denberg28.telerc

import kotlin.math.*

data class Waypoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

/** Planning data stays independent of transport; explicit upload uses a validated snapshot. */
class MissionPlan {
    private val points = mutableListOf<Waypoint>()
    val waypoints: List<Waypoint> get() = points.toList()

    // Mission — Bounds and CSV schema. Changing these requires import/upgrade regression checks.
    companion object {
        const val MAX_WAYPOINTS = 100
        const val MAX_CSV_CHARS = 16_384
        private const val HEADER = "sequence,latitude,longitude"

        /** Validate the complete file before replacing any existing plan. */
        fun decode(csv: String): MissionPlan {
            require(csv.length <= MAX_CSV_CHARS) { "Mission too large" }
            val lines = csv.trim().lineSequence().toList()
            require(lines.firstOrNull()?.removePrefix("\uFEFF") == HEADER) { "Unknown mission format" }
            require(lines.size <= MAX_WAYPOINTS + 1) { "Too many waypoints" }
            return MissionPlan().apply {
                lines.drop(1).forEachIndexed { index, line ->
                    val cells = line.split(',')
                    require(cells.size == 3 && cells[0].toIntOrNull() == index + 1) { "Invalid sequence" }
                    add(Waypoint(cells[1].toDouble(), cells[2].toDouble()))
                }
            }
        }
    }

    fun add(point: Waypoint) {
        require(points.size < MAX_WAYPOINTS) { "Maximum 100 waypoints" }
        points.add(point)
    }
    fun edit(index: Int, point: Waypoint) { points[index] = point }
    fun remove(index: Int) { points.removeAt(index) }
    fun move(index: Int, delta: Int) {
        require(delta == -1 || delta == 1)
        require(index in points.indices && index + delta in points.indices)
        val point = points.removeAt(index)
        points.add(index + delta, point)
    }
    fun clear() { points.clear() }
    fun replaceWith(other: MissionPlan) {
        val copy = other.waypoints
        points.clear(); points.addAll(copy)
    }
    fun encode(): String = buildString {
        append(HEADER).append('\n')
        points.forEachIndexed { i, p -> append("${i + 1},${p.latitude},${p.longitude}\n") }
    }

    // Mission — Distance is a surface estimate between consecutive points, excluding phone Home.
    val distanceMeters: Double get() = points.zipWithNext().sumOf { (a, b) ->
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        12_742_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
