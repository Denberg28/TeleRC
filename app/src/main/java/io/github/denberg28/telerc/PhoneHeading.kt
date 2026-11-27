package io.github.denberg28.telerc

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Sensors — Display-rotation-aware phone compass heading (0°–359°, 0° = North).
// Active only while Mission tab is resumed; smoothed circularly to avoid 359°/0° wrap jitter.
internal class PhoneHeading(
    private val activity: Activity,
    private val onHeading: (Double) -> Unit
) : SensorEventListener {
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var lastAccel: FloatArray? = null
    private var lastMag: FloatArray? = null
    private var running = false
    private var sinSum = 0.0
    private var cosSum = 0.0
    private var hasSample = false

    val available: Boolean get() = rotationVector != null || (accelerometer != null && magnetometer != null)
    var currentDegrees: Double? = null; private set

    fun start() {
        if (running) return
        running = true
        if (rotationVector != null) {
            manager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else if (accelerometer != null && magnetometer != null) {
            manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        manager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val ok = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel = event.values.clone()
                val m = lastMag
                m != null && SensorManager.getRotationMatrix(rotationMatrix, null, event.values, m)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMag = event.values.clone()
                val a = lastAccel
                a != null && SensorManager.getRotationMatrix(rotationMatrix, null, a, event.values)
            }
            else -> false
        }
        if (!ok) return

        val displayRotation = if (Build.VERSION.SDK_INT >= 30) {
            activity.display?.rotation ?: Surface.ROTATION_90
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.rotation
        }
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)
        val rawDeg = ((Math.toDegrees(orientation[0].toDouble()) % 360.0) + 360.0) % 360.0
        val rad = Math.toRadians(rawDeg)
        if (!hasSample) {
            sinSum = sin(rad); cosSum = cos(rad); hasSample = true
        } else {
            // Circular exponential smoothing prevents 359° <-> 0° flicker.
            sinSum = sinSum * 0.75 + sin(rad) * 0.25
            cosSum = cosSum * 0.75 + cos(rad) * 0.25
        }
        val smoothed = ((Math.toDegrees(atan2(sinSum, cosSum)) % 360.0) + 360.0) % 360.0
        currentDegrees = smoothed
        onHeading(smoothed)
    }
}
