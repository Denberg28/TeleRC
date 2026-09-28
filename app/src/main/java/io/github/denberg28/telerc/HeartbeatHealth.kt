package io.github.denberg28.telerc

internal object HeartbeatHealth {
    const val TIMEOUT_MS = 2500L

    fun isFresh(system: Int, lastHeartbeatMs: Long, nowMs: Long): Boolean =
        system != 0 && lastHeartbeatMs > 0L && nowMs >= lastHeartbeatMs &&
            nowMs - lastHeartbeatMs < TIMEOUT_MS
}
