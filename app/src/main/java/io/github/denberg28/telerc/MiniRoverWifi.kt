package io.github.denberg28.telerc

/** Wire settings for the standalone MiniRover 1.2.0 Wi-Fi-only firmware. */
internal object MiniRoverWifi {
    const val TRANSPORT = "mini_wifi"
    const val HOST = "192.168.4.1"
    const val PORT = 14550 // Also the required local UDP source port.
    const val SSID = "TeleRC-MiniRover"
    const val SETUP = "Join TeleRC-MiniRover in Android Wi-Fi settings. Password: telerc123. " +
        "Choose Stay connected when Android reports no internet.\n\n" +
        "Return here and tap Connect. Center both joysticks, tap ARM, and wait for ARMED. " +
        "Left joystick steers; right joystick drives forward/reverse.\n\n" +
        "DISARM, STOP, leaving the app, or connection loss stops and disarms the rover. " +
        "Reconnection requires another ARM. No receiver or flight controller is needed. " +
        "Use only the matching Wi-Fi-only sketch. Test with raised wheels first."

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
