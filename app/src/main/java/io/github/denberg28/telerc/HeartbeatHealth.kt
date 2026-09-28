package io.github.denberg28.telerc

internal object HeartbeatHealth {
    const val TIMEOUT_MS = 2500L
    const val STATUS_TIMEOUT_MS = 6000L

    fun isFresh(system: Int, lastHeartbeatMs: Long, nowMs: Long): Boolean =
        system != 0 && lastHeartbeatMs > 0L && nowMs >= lastHeartbeatMs &&
            nowMs - lastHeartbeatMs < TIMEOUT_MS

    // UI grace is independent of the shorter timeout that disarms control.
    fun isRecentlySeen(system: Int, lastHeartbeatMs: Long, nowMs: Long): Boolean =
        system != 0 && lastHeartbeatMs > 0L && nowMs >= lastHeartbeatMs &&
            nowMs - lastHeartbeatMs < STATUS_TIMEOUT_MS
}
