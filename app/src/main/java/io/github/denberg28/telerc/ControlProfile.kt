package io.github.denberg28.telerc

/** Channel mappings belong to the craft profile, not the transport. */
enum class CraftProfile(val title: String, val available: Boolean) {
    ROVER("Rover", true), MULTIROTOR("Multirotor", false),
    FIXED_WING("Fixed wing", false), WATERCRAFT("Watercraft", false), ROCKET("Rocket", false)
}

data class RcChannels(val one: Int, val two: Int, val three: Int, val four: Int)

object RoverControls {
    val neutral = RcChannels(1500, 1500, 1500, 1500)
    /** ArduRover default: steering on CH1, bidirectional throttle on CH3. */
    fun channels(steering: Int, drive: Int): RcChannels {
        require(steering in 1000..2000 && drive in 1000..2000)
        return RcChannels(steering, 1500, drive, 1500)
    }
}
