package io.github.denberg28.telerc

/** Pure preview geometry and USB-frame age checks, with no vehicle command access. */
object FpvPreviewPolicy {
    fun frameFresh(lastFrameMs: Long, nowMs: Long): Boolean =
        lastFrameMs > 0 && nowMs >= lastFrameMs && nowMs - lastFrameMs <= 2000

    fun fit(boxWidth: Int, boxHeight: Int, videoWidth: Int, videoHeight: Int): Pair<Int, Int>? {
        if (minOf(boxWidth, boxHeight, videoWidth, videoHeight) <= 0) return null
        val scale = minOf(boxWidth.toDouble() / videoWidth, boxHeight.toDouble() / videoHeight)
        return (videoWidth * scale).toInt().coerceIn(1, boxWidth) to
            (videoHeight * scale).toInt().coerceIn(1, boxHeight)
    }
}
