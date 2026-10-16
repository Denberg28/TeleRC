package io.github.denberg28.telerc

/** Only negotiate modes actually advertised by the receiver. */
object FpvStreamPolicy {
    const val YUYV = 5
    const val MJPEG = 7
    enum class Mode(val label: String) { AUTO("Auto · MJPEG preferred"), YUYV("YUYV compatibility"), MJPEG("MJPEG") }
    data class Profile(val type: Int, val width: Int, val height: Int, val fps: Int)
    fun profiles(advertised: List<Profile>, mode: Mode): List<Profile> = advertised
        .filter { it.type in listOf(YUYV, MJPEG) && it.width in 1..1920 && it.height in 1..1080 && it.fps in 1..60 }
        .filter { mode == Mode.AUTO || it.type == if (mode == Mode.YUYV) YUYV else MJPEG }
        .distinct().sortedWith(compareBy<Profile>(
            { if (mode == Mode.AUTO && it.type != MJPEG) 1 else 0 },
            { if (it.width == 640 && it.height == 480) 0 else 1 },
            { if (it.width <= 1280 && it.height <= 720) 0 else 1 },
            { kotlin.math.abs(it.width.toLong() * it.height - 640L * 480) },
            { if (it.fps <= 30) 0 else 1 }, { kotlin.math.abs(it.fps - 30) }))
    fun retryDelay(attempt: Int): Long? = when (attempt) { 0 -> 1000L; 1 -> 2000L; 2 -> 4000L; else -> null }
}
