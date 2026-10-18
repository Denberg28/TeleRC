package io.github.denberg28.telerc

/** USB attachment IDs are temporary. Remember receiver type, never a permission grant. */
class FpvReconnectPolicy(val preferred: Receiver? = null, restore: Boolean = false) {
    data class Receiver(val vendor: Int, val product: Int)
    data class Device(val attachment: String, val receiver: Receiver, val permitted: Boolean)
    enum class Action { IDLE, KEEP, LOST, WAIT_DEVICE, AMBIGUOUS, WAIT_PERMISSION, NEED_PERMISSION, REQUEST_PERMISSION, WAIT_RETRY, OPEN }
    data class Decision(val action: Action, val device: Device? = null)
    var receiver: Receiver? = preferred; private set
    var wanted = restore && preferred != null; private set
    var pendingPermission: String? = null; private set
    var failures = 0; private set
    var retryAt = 0L; private set
    private var allowPrompt = false
    private var lastFrame = 0L
    private var healthySince = 0L
    private var lastAttachment: String? = null

    fun request(selected: Receiver? = null) {
        if (selected != null) receiver = selected
        wanted = true
        if (pendingPermission == null) allowPrompt = true
        failures = 0; retryAt = 0
    }
    fun disallowPrompt() { allowPrompt = false }
    fun remember(selected: Receiver) { receiver = selected }
    fun decide(devices: List<Device>, active: Boolean, session: String?, now: Long): Decision {
        if (!wanted || !active) return Decision(Action.IDLE)
        if (session != null) {
            allowPrompt = false; lastAttachment = session
            return Decision(if (devices.any { it.attachment == session && it.permitted }) Action.KEEP else Action.LOST)
        }
        val candidates = devices.filter { receiver == null || it.receiver == receiver }
        if (candidates.isEmpty()) { detached(); return Decision(Action.WAIT_DEVICE) }
        if (candidates.size != 1) return Decision(Action.AMBIGUOUS)
        val device = candidates.single()
        if (lastAttachment != null && lastAttachment != device.attachment) {
            failures = 0; retryAt = 0; resetFrames()
        }
        lastAttachment = device.attachment
        if (pendingPermission != null && pendingPermission != device.attachment) pendingPermission = null
        if (device.permitted) {
            // A system default/attachment grant is sufficient; never ask again to open it.
            pendingPermission = null; allowPrompt = false
            return Decision(if (now < retryAt) Action.WAIT_RETRY else Action.OPEN, device)
        }
        if (pendingPermission != null) return Decision(Action.WAIT_PERMISSION, device)
        if (!allowPrompt) return Decision(Action.NEED_PERMISSION, device)
        allowPrompt = false; pendingPermission = device.attachment
        return Decision(Action.REQUEST_PERMISSION, device)
    }
    fun permissionResult(attachment: String, granted: Boolean): Boolean {
        if (pendingPermission != attachment) return false
        pendingPermission = null; allowPrompt = false
        if (!granted) wanted = false
        return true
    }
    fun detached() {
        pendingPermission = null; allowPrompt = false
        failures = 0; retryAt = 0; lastAttachment = null; resetFrames()
    }
    fun failed(now: Long): Long {
        val delay = FpvStreamPolicy.retryDelay(failures) ?: 30000L
        failures = (failures + 1).coerceAtMost(4)
        retryAt = now + delay; resetFrames()
        return delay
    }
    fun defer(until: Long) { retryAt = maxOf(retryAt, until); resetFrames() }
    fun frame(now: Long) {
        if (!FpvPreviewPolicy.frameFresh(lastFrame, now)) healthySince = now
        lastFrame = now
        if (healthySince > 0 && now - healthySince >= 3000) failures = 0
    }
    fun resetFrames() { lastFrame = 0; healthySince = 0 }
    fun stop() { wanted = false; pendingPermission = null; allowPrompt = false; resetFrames() }
}
