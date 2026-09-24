package io.github.denberg28.telerc

import kotlin.math.cos
import kotlin.math.sin

/** Small offline bicycle-like preview, not a calibrated vehicle model. */
internal data class RoverPose(val x: Float = .5f, val y: Float = .76f, val heading: Float = 0f, val speed: Float = 0f)

internal fun roverStep(pose: RoverPose, steering: Float, drive: Float, dt: Float): RoverPose {
    val step = dt.coerceIn(0f, .05f)
    val demand = if (kotlin.math.abs(drive) < .08f) 0f else drive.coerceIn(-1f, 1f)
    val speed = pose.speed + (demand * .65f - pose.speed) * (step * 5f).coerceAtMost(1f)
    val heading = (pose.heading + steering.coerceIn(-1f, 1f) * speed * step * 2.3f).coerceIn(-1.3f, 1.3f)
    return RoverPose(
        (pose.x + sin(heading) * speed * step).coerceIn(.24f, .76f),
        (pose.y - cos(heading) * speed * step * .45f).coerceIn(.15f, .85f),
        heading, speed
    )
}
