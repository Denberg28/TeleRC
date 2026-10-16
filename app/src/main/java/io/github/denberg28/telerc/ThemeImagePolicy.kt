package io.github.denberg28.telerc

object ThemeImagePolicy {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_EDGE = 1920
    fun target(width: Int, height: Int): Pair<Int, Int>? {
        if (width !in 1..100000 || height !in 1..100000) return null
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
    fun sample(width: Int, height: Int): Int {
        if (target(width, height) == null) return 0
        var result = 1
        while ((maxOf(width, height).toLong() + result - 1) / result > MAX_EDGE) result *= 2
        return result
    }
}
