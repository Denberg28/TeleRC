package io.github.denberg28.telerc

import kotlin.math.abs
import kotlin.math.sqrt

/** Two-dimensional visual position; only the assigned axis produces channel output. */
internal data class StickPosition(val x: Float, val y: Float)

internal fun limitStick(x: Float, y: Float): StickPosition {
    val length = sqrt(x * x + y * y)
    return if (length > 1f) StickPosition(x / length, y / length) else StickPosition(x, y)
}

internal fun stickChannel(position: StickPosition, vertical: Boolean): Int {
    val axis = if (vertical) -position.y else position.x
    return if (abs(axis) < .08f) 1500 else
        (1500 + axis.coerceIn(-1f, 1f) * 500f).toInt().coerceIn(1000, 2000)
}
