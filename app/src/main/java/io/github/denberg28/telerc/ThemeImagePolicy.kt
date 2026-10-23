package io.github.denberg28.telerc

object ThemeImagePolicy {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_EDGE = 1920
    fun target(width: Int, height: Int): Pair<Int, Int>? {
        if (width !in 1..100000 || height !in 1..100000 || width.toLong() * height > 100_000_000L) return null
        if (maxOf(width, height) <= MAX_EDGE) return width to height
        return if (width >= height) MAX_EDGE to maxOf(1, (height.toLong() * MAX_EDGE / width).toInt())
            else maxOf(1, (width.toLong() * MAX_EDGE / height).toInt()) to MAX_EDGE
    }
    fun sample(width: Int, height: Int): Int {
        if (target(width, height) == null) return 0
        var result = 1
        while ((maxOf(width, height).toLong() + result - 1) / result > MAX_EDGE) result *= 2
        return result
    }
}
