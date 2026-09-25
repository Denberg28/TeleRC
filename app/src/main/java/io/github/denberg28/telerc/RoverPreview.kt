package io.github.denberg28.telerc

import kotlin.math.cos

internal data class PreviewLocation(val latitude: Double, val longitude: Double, val headingDegrees: Double)

/** Offline pose projection for map preview; never saved as rover telemetry. */
internal fun previewLocation(home: TrackPoint, origin: RoverPose, pose: RoverPose): PreviewLocation {
    val northMeters = (origin.y - pose.y) * 4.0
    val eastMeters = (pose.x - origin.x) * 4.0
    val lat = home.latitude + northMeters / 111_320.0
    val lon = home.longitude + eastMeters / (111_320.0 * cos(Math.toRadians(home.latitude)).coerceAtLeast(.01))
    val heading = (Math.toDegrees(pose.heading.toDouble()) + 360.0) % 360.0
    return PreviewLocation(lat, lon, heading)
}
