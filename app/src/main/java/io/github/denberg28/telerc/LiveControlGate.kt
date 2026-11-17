package io.github.denberg28.telerc

import java.util.concurrent.atomic.AtomicBoolean

/** Transport recovery never restores revoked driving authority. */
internal class LiveControlGate {
    private val enabled = AtomicBoolean(false)

    fun get(): Boolean = enabled.get()
    fun disable() { enabled.set(false) }

    fun enable(system: Int, heartbeatAt: Long, now: Long,
               steering: Int = 1500, drive: Int = 1500): Boolean {
        if (!HeartbeatHealth.isFresh(system, heartbeatAt, now) ||
            steering !in 1475..1525 || drive !in 1475..1525) {
            disable()
            return false
        }
        enabled.set(true)
        return true
    }

    fun expire(system: Int, heartbeatAt: Long, now: Long): Boolean =
        !HeartbeatHealth.isFresh(system, heartbeatAt, now) && enabled.compareAndSet(true, false)
}
