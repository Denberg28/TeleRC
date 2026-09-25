package io.github.denberg28.telerc

import kotlin.math.min

/** Integrates frames actually sent to the rover. This is an open-loop estimate, never GPS telemetry. */
internal class DeadReckoning(var turnDegreesPerSecond: Float = 220f) {
    var pose = RoverPose()
        private set
    private var lastMs: Long? = null
    private var previousSteer = 0f
    private var previousDrive = 0f

    fun accept(timeMs: Long, steeringUs: Int, driveUs: Int): RoverPose {
        val elapsed = lastMs?.let { (timeMs - it).coerceIn(0L, 250L) / 1000f } ?: 0f
        var remaining = elapsed
        while (remaining > 0f) {
            val step = min(.05f, remaining)
            pose = roverStep(pose, previousSteer, previousDrive, step, turnDegreesPerSecond)
            remaining -= step
        }
        lastMs = timeMs
        previousSteer = ((steeringUs - 1500) / 500f).coerceIn(-1f, 1f)
        previousDrive = ((driveUs - 1500) / 500f).coerceIn(-1f, 1f)
        return pose
    }

    fun reset() {
        pose = RoverPose(); lastMs = null; previousSteer = 0f; previousDrive = 0f
    }
}
