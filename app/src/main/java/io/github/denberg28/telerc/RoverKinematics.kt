package io.github.denberg28.telerc

import kotlin.math.cos
import kotlin.math.sin

/** Small offline bicycle-like preview, not a calibrated vehicle model. */
internal data class RoverPose(val x: Float = .5f, val y: Float = .76f, val heading: Float = 0f, val speed: Float = 0f)

/** Arcade movement is direct and works even before forward drive is pressed. */
internal fun gameX(x: Float, steering: Float, dt: Float): Float =
    (x + steering.coerceIn(-1f, 1f) * dt.coerceIn(0f, .05f) * 1.05f).coerceIn(.245f, .755f)

internal fun roverStep(pose: RoverPose, steering: Float, drive: Float, dt: Float): RoverPose {
    val step = dt.coerceIn(0f, .05f)
    val demand = if (kotlin.math.abs(drive) < .08f) 0f else drive.coerceIn(-1f, 1f)
    val response = if (demand == 0f) 18f else 12f
    val speed = pose.speed + (demand * .7f - pose.speed) * (step * response).coerceAtMost(1f)
    val heading = (pose.heading + steering.coerceIn(-1f, 1f) * speed * step * 3.1f).coerceIn(-1.3f, 1.3f)
    return RoverPose(
        (pose.x + sin(heading) * speed * step).coerceIn(.24f, .76f),
        (pose.y - cos(heading) * speed * step * .45f).coerceIn(.15f, .85f),
        heading, speed
    )
}
