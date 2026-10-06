package io.github.denberg28.telerc

import java.util.concurrent.atomic.AtomicBoolean

/** Transport recovery never restores revoked driving authority. */
internal class LiveControlGate {
    private val enabled = AtomicBoolean(false)

    fun get(): Boolean = enabled.get()
    fun disable() { enabled.set(false) }

    fun enable(system: Int, heartbeatAt: Long, now: Long): Boolean {
        if (!HeartbeatHealth.isFresh(system, heartbeatAt, now)) return false
        enabled.set(true)
        return true
    }

    fun expire(system: Int, heartbeatAt: Long, now: Long): Boolean =
        !HeartbeatHealth.isFresh(system, heartbeatAt, now) && enabled.compareAndSet(true, false)
}
