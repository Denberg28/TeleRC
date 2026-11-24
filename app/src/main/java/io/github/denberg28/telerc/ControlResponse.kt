package io.github.denberg28.telerc

// Control — Input response. CH1 steering and CH2 drive keep independent 25–100% gain.
// Go proportional controls compensate the receiver neutral window after gain.
internal data class ControlResponse(
    val steeringPercent: Int = 100,
    val drivePercent: Int = 100,
    val expoPercent: Int = 0
) {
    fun channel(position: StickPosition, vertical: Boolean, receiverDeadbandUs: Int = 0): Int = stickChannel(
        position, vertical,
        (if (vertical) drivePercent else steeringPercent).coerceIn(25, 100) / 100f,
        expoPercent.coerceIn(0, 100) / 100f,
        receiverDeadbandUs
    )

    fun proportionalChannel(position: StickPosition, vertical: Boolean, receiverDeadbandUs: Int): Int =
        proportionalStickChannel(
            position, vertical,
            (if (vertical) drivePercent else steeringPercent).coerceIn(25, 100) / 100f,
            receiverDeadbandUs
        )

    companion object {
        // Covers the 25 µs mini-rover and 35 µs hybrid receiver neutral windows.
        const val GO_RECEIVER_DEADBAND_US = 35
        const val STEERING_KEY = "steering_sensitivity"
        const val DRIVE_KEY = "drive_sensitivity"
        const val EXPO_KEY = "control_expo"

        // An absent axis inherits the old shared slider. Existing axes win,
        // including a partially migrated preference set. Invalid values clamp.
        fun fromStored(values: Map<String, *>): ControlResponse {
            val legacy = (values["sensitivity"] as? Int ?: 100).coerceIn(25, 100)
            return ControlResponse(
                (values[STEERING_KEY] as? Int ?: legacy).coerceIn(25, 100),
                (values[DRIVE_KEY] as? Int ?: legacy).coerceIn(25, 100),
                (values[EXPO_KEY] as? Int ?: 0).coerceIn(0, 100)
            )
        }
    }
}
