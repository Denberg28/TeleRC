package io.github.denberg28.telerc

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** Two-dimensional visual position; only the assigned axis produces channel output. */
internal data class StickPosition(val x: Float, val y: Float)

internal fun limitStick(x: Float, y: Float, vertical: Boolean? = null): StickPosition {
    if (!x.isFinite() || !y.isFinite()) return StickPosition(0f, 0f)
    if (vertical != null) return if (vertical) StickPosition(0f, y.coerceIn(-1f, 1f))
    else StickPosition(x.coerceIn(-1f, 1f), 0f)
    val length = sqrt(x * x + y * y)
    return if (length > 1f) StickPosition(x / length, y / length) else StickPosition(x, y)
}

/** Expo blends linear and cubic response before sensitivity; it adds no delay. */
internal fun stickChannel(
    position: StickPosition, vertical: Boolean, sensitivity: Float = 1f, expo: Float = 0f,
    receiverDeadbandUs: Int = 0
): Int {
    val axis = if (vertical) -position.y else position.x
    if (!axis.isFinite() || !sensitivity.isFinite() || !expo.isFinite() || abs(axis) < .08f) return 1500
    if (receiverDeadbandUs !in 0..99) return 1500
    if (receiverDeadbandUs > 0) {
        val travel = ((abs(axis).coerceAtMost(1f) - .08f) / .92f).coerceAtLeast(0f)
        if (travel == 0f) return 1500
        val e = expo.coerceIn(0f, 1f) * .75f
        val shaped = (1f - e) * travel + e * travel * travel * travel
        val maximum = (400f * sensitivity.coerceIn(.25f, 1f)).roundToInt()
        val delta = receiverDeadbandUs + ((maximum - receiverDeadbandUs) * shaped).roundToInt().coerceAtLeast(1)
        return 1500 + sign(axis).toInt() * delta
    }
    val x = axis.coerceIn(-1f, 1f)
    val e = expo.coerceIn(0f, 1f)
    val shaped = (1f - e) * x + e * x * x * x
    return (1500 + shaped * 400f * sensitivity.coerceIn(.25f, 1f)).toInt().coerceIn(1100, 1900)
}

// Control — Go proportional mapping. Fixed 8% neutral guard, then compensates
// receiverDeadbandUs after gain; half line travel requests half selected strength.
internal fun proportionalStickChannel(
    position: StickPosition, vertical: Boolean, sensitivity: Float,
    receiverDeadbandUs: Int
): Int {
    val axis = if (vertical) -position.y else position.x
    if (!axis.isFinite() || !sensitivity.isFinite() || receiverDeadbandUs !in 0..99 || abs(axis) <= .08f) return 1500
    val demand = abs(axis).coerceAtMost(1f) * sensitivity.coerceIn(.25f, 1f)
    val delta = receiverDeadbandUs + ((400 - receiverDeadbandUs) * demand).roundToInt().coerceAtLeast(1)
    return 1500 + sign(axis).toInt() * delta
}
