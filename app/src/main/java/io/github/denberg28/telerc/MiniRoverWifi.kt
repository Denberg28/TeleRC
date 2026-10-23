package io.github.denberg28.telerc

/** Wire settings for the standalone MiniRover Wi-Fi firmware. */
internal object MiniRoverWifi {
    const val TRANSPORT = "mini_wifi"
    const val HOST = "192.168.4.1"
    const val PORT = 14550 // Also the required local UDP source port.
    const val SSID = "TeleRC-MiniRover"
    const val SETUP = "Join TeleRC-MiniRover in Android Wi-Fi settings (default password: telerc123). " +
        "Choose Stay connected if Android reports no internet. Close PC TeleRC; one Wi-Fi client is supported.\n\n" +
        "Use the standalone Wi-Fi mini rover sketch 1.2.0. No receiver or flight controller is required.\n\n" +
        "Connect, open Controls and tap ENABLE CONTROL. Center both app sticks until ARM · READY, " +
        "then tap ARM and wait for rover-confirmed ARMED. DISARM also waits for heartbeat confirmation. " +
        "CH1 steers; CH2 drives forward/reverse.\n\n" +
        "STOP CONTROL releases the mini rover channels and disarms it. Tab changes/background hold neutral " +
        "and revoke app control; recovery never enables or arms automatically. Test with raised wheels first. " +
        "Older receiver-gated sketches still require their own radio permissions."

}

/** Track successfully sent neutral frames, never discovery or local joystick state alone. */
internal class MiniRoverArmGate {
    private var neutralSince: Long? = null
    private var lastNeutral: Long? = null

    @Synchronized fun reset() { neutralSince = null; lastNeutral = null }

    @Synchronized fun sent(steer: Int, drive: Int, now: Long) {
        if (steer !in 1475..1525 || drive !in 1475..1525) { reset(); return }
        val previous = lastNeutral
        // Match the firmware's 500 ms command expiry; an interrupted dwell starts over.
        if (previous == null || now < previous || now - previous >= 500) neutralSince = now
        lastNeutral = now
    }

    @Synchronized fun ready(now: Long): Boolean {
        val since = neutralSince ?: return false
        val last = lastNeutral ?: return false
        // A little longer than the firmware's 250 ms neutral dwell; do not queue ARM.
        return now >= last && now - last < 250 && now - since >= 300
    }
}
