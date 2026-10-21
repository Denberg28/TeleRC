package io.github.denberg28.telerc

/** Distinguish a short render gap from a failed USB session. No permission or control access. */
class FpvHealthPolicy {
    enum class Action { WAIT, RESTART_PREVIEW, REOPEN_USB }
    private var startedAt = 0L
    private var lastFrame = 0L
    private var healthySince = 0L
    private var restarted = false
    var stable = false; private set

    fun started(now: Long) { startedAt = now; lastFrame = 0; healthySince = 0; stable = false }
    fun frame(now: Long) {
        if (!FpvPreviewPolicy.frameFresh(lastFrame, now)) healthySince = now
        lastFrame = now
        stable = healthySince > 0 && now - healthySince >= 3000
        if (stable) restarted = false
    }
    fun check(now: Long): Action {
        if (startedAt <= 0 || now < startedAt) return Action.WAIT
        val stalled = if (lastFrame > 0) now - lastFrame >= 3000 else now - startedAt >= 8000
        if (!stalled) return Action.WAIT
        stable = false
        return if (restarted) Action.REOPEN_USB else {
            restarted = true
            Action.RESTART_PREVIEW
        }
    }
    fun reset() { startedAt = 0; lastFrame = 0; healthySince = 0; restarted = false; stable = false }
}
