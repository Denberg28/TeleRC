package io.github.denberg28.telerc

import kotlin.math.abs
import kotlin.math.sqrt

/** Two-dimensional visual position; only the assigned axis produces channel output. */
internal data class StickPosition(val x: Float, val y: Float)

internal fun limitStick(x: Float, y: Float): StickPosition {
    if (!x.isFinite() || !y.isFinite()) return StickPosition(0f, 0f)
    val length = sqrt(x * x + y * y)
    return if (length > 1f) StickPosition(x / length, y / length) else StickPosition(x, y)
}

/** Expo blends linear and cubic response before sensitivity; it adds no delay. */
internal fun stickChannel(
    position: StickPosition, vertical: Boolean, sensitivity: Float = 1f, expo: Float = 0f
): Int {
    val axis = if (vertical) -position.y else position.x
    if (!axis.isFinite() || !sensitivity.isFinite() || !expo.isFinite() || abs(axis) < .08f) return 1500
    val x = axis.coerceIn(-1f, 1f)
    val e = expo.coerceIn(0f, 1f)
    val shaped = (1f - e) * x + e * x * x * x
    return (1500 + shaped * 400f * sensitivity.coerceIn(.25f, 1f)).toInt().coerceIn(1100, 1900)
}
