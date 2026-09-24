package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.View
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.floor
import kotlin.random.Random

/** Offline arcade course; input is never forwarded to the MAVLink transport. */
class TestDriveView(context: Context) : View(context) {
    enum class Mode { GAME, TEST }
    var mode = Mode.GAME
        private set
    private val scores = context.getSharedPreferences("game", Context.MODE_PRIVATE)
    private var highScore = scores.getInt("high_score", 0)
    private var music = false
    private var tone: ToneGenerator? = null
    private var beatAt = 0L
    private data class Gate(var y: Float, val center: Float, var passed: Boolean = false)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purple = Color.rgb(112, 88, 166)
    private val gates = mutableListOf<Gate>()
    private val random = Random.Default
    private var steer = 0f
    private var throttle = 0f
    private var x = .5f
    private var y = .76f
    private var heading = 0f
    private var speed = 0f
    private var leftTrack = 0f
    private var rightTrack = 0f
    private var distance = 0f
    // Visual scroll is independent of the per-run distance, so crashes never snap the road.
    private var roadScroll = 0f
    private var passed = 0
    private var waitForRelease = false
    private var lastFrame = 0L
    private var running = true
    private val roverY = .76f
    private val gapWidth = .25f
    private val gateHeight = .085f
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            if (lastFrame != 0L) advance(((now - lastFrame) / 1_000_000_000f).coerceIn(0f, .05f))
            lastFrame = now
            invalidate()
            postDelayed(this, 16)
        }
    }

    init { contentDescription = "Offline rover game and steering test"; resetCourse() }
    fun setMode(value: Mode) {
        if (mode == value) return
        mode = value; resetCourse(preserveInput = true)
        if (value == Mode.TEST) setMusic(false)
    }
    fun setMusic(enabled: Boolean) {
        music = enabled && mode == Mode.GAME
        if (!music) { tone?.release(); tone = null }
        else if (tone == null) tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 22) }.getOrNull()
    }
    fun setSteering(value: Int) { steer = ((value - 1500) / 500f).coerceIn(-1f, 1f) }
    fun setDrive(value: Int) { throttle = ((value - 1500) / 500f).coerceIn(-1f, 1f); if (abs(throttle) < .08f) waitForRelease = false }

    fun resetCourse(preserveInput: Boolean = false) {
        x = .5f; y = .76f; heading = 0f; speed = 0f
        leftTrack = 0f; rightTrack = 0f; distance = 0f; passed = 0
        gates.clear()
        gates.add(Gate(-.35f, .5f))
        if (!preserveInput) { steer = 0f; throttle = 0f }
        waitForRelease = false
        invalidate()
    }

    fun stop() { running = false; removeCallbacks(frame); lastFrame = 0L; steer = 0f; throttle = 0f; setMusic(false) }
    fun resume() { if (!running && isAttachedToWindow) { running = true; lastFrame = 0L; post(frame) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = false; resume() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    private fun advance(dt: Float) {
        // Game remains an arcade lane game; Test mixes CH1/CH3 into skid-steer tracks.
        val demand = if (waitForRelease || abs(throttle) < .08f) 0f else throttle
        if (mode == Mode.TEST) {
            val next = roverStep(RoverPose(x, y, heading, speed, leftTrack, rightTrack), steer, demand, dt)
            x = next.x; y = next.y; heading = next.heading; speed = next.speed
            leftTrack = next.leftTrack; rightTrack = next.rightTrack
            distance += abs(speed * dt) * 100f
            return
        }
        speed += (demand * .75f - speed) * (dt * 14f).coerceAtMost(1f)
        // Arcade lane control is independent of forward drive: left always moves left.
        x = gameX(x, steer, dt)
        if (music && System.nanoTime() - beatAt > 360_000_000L) {
            beatAt = System.nanoTime()
            tone?.startTone(if ((passed and 1) == 0) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK, 55)
        }
        if (speed <= 0f) return
        val travel = speed * dt
        distance += travel * 100f
        roadScroll = (roadScroll + travel) % .25f // two stripe periods; preserve color and avoid float drift
        for (gate in gates) {
            gate.y += travel
            if (gate.y < roverY + .04f && gate.y + gateHeight > roverY - .04f &&
                abs(x - gate.center) > (gapWidth - .055f) / 2f) {
                if (passed > highScore) { highScore = passed; scores.edit().putInt("high_score", highScore).apply() }
                resetCourse()
                waitForRelease = true
                return
            }
            if (!gate.passed && gate.y > roverY + .045f) {
                gate.passed = true; passed++
                if (passed > highScore) { highScore = passed; scores.edit().putInt("high_score", highScore).apply() }
            }
        }
        gates.removeAll { it.y > 1.1f }
        if (gates.isEmpty() || gates.last().y > -.12f) {
            gates.add(Gate(-.48f, random.nextInt(34, 67) / 100f))
        }
    }

    private fun box(canvas: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float) {
        paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawRect(l, t, r, b, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val dp = resources.displayMetrics.density
        // Test uses world positions measured in screen-height units. Keep the rover
        // at the camera anchor while the infinite road moves in both axes.
        val cameraX = if (mode == Mode.TEST) (x - .5f) * h else 0f
        val cameraY = if (mode == Mode.TEST) (y - roverY) * h else 0f
        box(canvas, Color.rgb(30, 40, 48), 0f, 0f, w, h)
        box(canvas, Color.rgb(52, 69, 62), w * .17f - cameraX, 0f, w * .83f - cameraX, h)
        box(canvas, Color.rgb(72, 76, 82), w * .22f - cameraX, 0f, w * .78f - cameraX, h)
        val period = h / 8f
        val travel = if (mode == Mode.TEST) -cameraY else roadScroll * h
        val shift = ((travel % period) + period) % period
        val tile = floor(travel / period).toInt()
        for (i in -1..9) {
            val sy = i * period + shift
            box(canvas, Color.rgb(225, 210, 151), w * .495f - cameraX, sy, w * .505f - cameraX, sy + h / 22f)
            val stripe = if ((i - tile) % 2 == 0) Color.WHITE else Color.rgb(214, 100, 94)
            box(canvas, stripe, w * .19f - cameraX, sy, w * .22f - cameraX, sy + period)
            box(canvas, if ((i - tile) % 2 == 0) Color.rgb(214, 100, 94) else Color.WHITE,
                w * .78f - cameraX, sy, w * .81f - cameraX, sy + period)
        }
        for (gate in if (mode == Mode.GAME) gates else emptyList()) {
            val left = (gate.center - gapWidth / 2f) * w
            val right = (gate.center + gapWidth / 2f) * w
            val top = gate.y * h
            val bottom = top + gateHeight * h
            box(canvas, Color.rgb(232, 153, 83), w * .22f, top, left, bottom)
            box(canvas, Color.rgb(232, 153, 83), right, top, w * .78f, bottom)
            box(canvas, Color.rgb(255, 225, 133), left - 3f * dp, top, left, bottom)
            box(canvas, Color.rgb(255, 225, 133), right, top, right + 3f * dp, bottom)
        }
        val rx = if (mode == Mode.GAME) x * w else w * .5f
        val ry = roverY * h
        val rw = 13f * dp; val rh = 20f * dp
        canvas.save(); canvas.rotate(Math.toDegrees(heading.toDouble()).toFloat(), rx, ry)
        val idleTrack = Color.rgb(22, 25, 35)
        fun trackColor(output: Float) = when {
            output > .08f -> Color.rgb(54, 207, 176)
            output < -.08f -> Color.rgb(238, 147, 88)
            else -> idleTrack
        }
        box(canvas, if (mode == Mode.TEST) trackColor(leftTrack) else idleTrack,
            rx-rw*.88f, ry-rh*.7f, rx-rw*.58f, ry+rh*.7f)
        box(canvas, if (mode == Mode.TEST) trackColor(rightTrack) else idleTrack,
            rx+rw*.58f, ry-rh*.7f, rx+rw*.88f, ry+rh*.7f)
        box(canvas, purple, rx-rw*.6f, ry-rh*.8f, rx+rw*.6f, ry+rh*.8f)
        box(canvas, Color.rgb(178, 232, 239), rx-rw*.4f, ry-rh*.58f, rx+rw*.4f, ry-rh*.2f)
        box(canvas, Color.rgb(244, 208, 117), rx-rw*.4f, ry-rh*.84f, rx+rw*.4f, ry-rh*.72f)
        canvas.restore()
        paint.color = Color.WHITE; paint.textSize = 12f * dp; paint.typeface = android.graphics.Typeface.MONOSPACE
        if (mode == Mode.GAME) {
            canvas.drawText("SCORE $passed   BEST $highScore", 12f * dp, 20f * dp, paint)
            canvas.drawText("DIST ${distance.toInt()}", 12f * dp, h - 12f * dp, paint)
        } else {
            box(canvas, Color.rgb(26, 32, 42), 0f, 0f, w, 42f * dp)
            paint.color = Color.WHITE
            val ch1 = (1500 + steer * 500).toInt()
            val ch3 = (1500 + throttle * 500).toInt()
            val degrees = ((heading * 180f / PI.toFloat()).toInt() + 360) % 360
            fun signed(value: Int) = if (value >= 0) "+$value" else "$value"
            val first = "CH1 $ch1  CH3 $ch3  H ${degrees}°"
            val second = "L ${signed((leftTrack * 100).toInt())}%  R ${signed((rightTrack * 100).toInt())}%  V ${signed((speed * 100).toInt())}"
            paint.textSize = 10f * dp
            paint.textSize *= minOf(1f, (w - 16f * dp) / max(paint.measureText(first), paint.measureText(second)))
            canvas.drawText(first, 8f * dp, 17f * dp, paint)
            canvas.drawText(second, 8f * dp, 34f * dp, paint)
        }
    }
}
