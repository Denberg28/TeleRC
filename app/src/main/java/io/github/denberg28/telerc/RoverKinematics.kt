package io.github.denberg28.telerc

import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin

/** Offline skid-steer preview. Track outputs are normalized, not measured motor speeds. */
internal data class RoverPose(
    val x: Float = .5f, val y: Float = .76f, val heading: Float = 0f,
    val speed: Float = 0f, val leftTrack: Float = 0f, val rightTrack: Float = 0f
)

/** Arcade movement is direct and works even before forward drive is pressed. */
internal fun gameX(x: Float, steering: Float, dt: Float): Float =
    (x + steering.coerceIn(-1f, 1f) * dt.coerceIn(0f, .05f) * .38f).coerceIn(.245f, .755f)

internal fun roverStep(pose: RoverPose, steering: Float, drive: Float, dt: Float,
                       maxYawRateDegrees: Float = 220f): RoverPose {
    val step = dt.coerceIn(0f, .05f)
    val steer = steering.takeIf { abs(it) >= .08f }?.coerceIn(-1f, 1f) ?: 0f
    val throttle = drive.takeIf { abs(it) >= .08f }?.coerceIn(-1f, 1f) ?: 0f
    val leftDemand = throttle + steer
    val rightDemand = throttle - steer
    val scale = max(1f, max(abs(leftDemand), abs(rightDemand)))
    val response = if (steer == 0f && throttle == 0f) 18f else 14f
    val blend = (step * response).coerceAtMost(1f)
    val left = pose.leftTrack + (leftDemand / scale - pose.leftTrack) * blend
    val right = pose.rightTrack + (rightDemand / scale - pose.rightTrack) * blend
    val speed = (left + right) * .35f
    val yaw = (left - right) * Math.toRadians(maxYawRateDegrees.toDouble()).toFloat() / 2f
    val midHeading = pose.heading + yaw * step * .5f
    val angle = pose.heading + yaw * step
    val heading = atan2(sin(angle), cos(angle))
    return RoverPose(
        pose.x + sin(midHeading) * speed * step,
        pose.y - cos(midHeading) * speed * step,
        heading, speed, left, right
    )
}
