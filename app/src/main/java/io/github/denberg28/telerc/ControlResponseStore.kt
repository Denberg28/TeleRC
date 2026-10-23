package io.github.denberg28.telerc

import android.content.Context

internal object ControlResponseStore {
    private fun preferences(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun read(context: Context): ControlResponse = ControlResponse.fromStored(preferences(context).all)

    fun save(context: Context, value: ControlResponse) {
        preferences(context).edit()
            .putInt(ControlResponse.STEERING_KEY, value.steeringPercent.coerceIn(25, 100))
            .putInt(ControlResponse.DRIVE_KEY, value.drivePercent.coerceIn(25, 100))
            .putInt(ControlResponse.EXPO_KEY, value.expoPercent.coerceIn(0, 100))
            .apply()
    }

    fun migrate(context: Context) {
        val values = preferences(context).all
        val value = ControlResponse.fromStored(values)
        if (values[ControlResponse.STEERING_KEY] != value.steeringPercent ||
            values[ControlResponse.DRIVE_KEY] != value.drivePercent ||
            values[ControlResponse.EXPO_KEY] != value.expoPercent) save(context, value)
    }
}
