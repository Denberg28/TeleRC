package io.github.denberg28.telerc

/** Telemetry registration is independent of FC heartbeat freshness and never grants authority. */
internal class BackpackRecovery {
    enum class Action { WAIT, REGISTER, REBIND }
    private var checkedAt: Long? = null

    fun poll(now: Long, foreground: Boolean, requested: Boolean, networkPresent: () -> Boolean): Action {
        if (!foreground || !requested || now < 0) return Action.WAIT
        val previous = checkedAt
        if (previous != null && now >= previous && now - previous < 1000L) return Action.WAIT
        checkedAt = now
        return if (networkPresent()) Action.REGISTER else Action.REBIND
    }
}
