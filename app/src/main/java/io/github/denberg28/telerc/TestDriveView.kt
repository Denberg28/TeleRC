package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.random.Random

/** Offline arcade course; input is never forwarded to the MAVLink transport. */
class TestDriveView(context: Context) : View(context) {
    private data class Gate(var y: Float, val center: Float, var passed: Boolean = false)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purple = Color.rgb(112, 88, 166)
    private val gates = mutableListOf<Gate>()
    private val random = Random.Default
    private var steer = 0f
    private var throttle = 0f
    private var x = .5f
    private var speed = 0f
    private var distance = 0f
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

    init { contentDescription = "Offline endless rover obstacle course"; resetCourse() }
    fun setSteering(value: Int) { steer = ((value - 1500) / 500f).coerceIn(-1f, 1f) }
    fun setDrive(value: Int) { throttle = ((value - 1500) / 500f).coerceIn(-1f, 1f); if (abs(throttle) < .08f) waitForRelease = false }

    fun resetCourse() {
        x = .5f; speed = 0f; distance = 0f; passed = 0
        gates.clear()
        gates.add(Gate(-.35f, .5f))
        steer = 0f; throttle = 0f; waitForRelease = false
        invalidate()
    }

    fun stop() { running = false; removeCallbacks(frame); lastFrame = 0L; steer = 0f; throttle = 0f }
    fun resume() { if (!running && isAttachedToWindow) { running = true; lastFrame = 0L; post(frame) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = false; resume() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    private fun advance(dt: Float) {
        // Direct response on press and release, with a small neutral deadband.
        speed = if (waitForRelease || abs(throttle) < .08f) 0f else throttle * .65f
        x = (x + steer * dt * .75f).coerceIn(.245f, .755f)
        if (speed <= 0f) return
        val travel = speed * dt
        distance += travel * 100f
        for (gate in gates) {
            gate.y += travel
            if (gate.y < roverY + .04f && gate.y + gateHeight > roverY - .04f &&
                abs(x - gate.center) > (gapWidth - .055f) / 2f) {
                resetCourse()
                waitForRelease = true
                return
            }
            if (!gate.passed && gate.y > roverY + .045f) { gate.passed = true; passed++ }
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
        box(canvas, Color.rgb(30, 40, 48), 0f, 0f, w, h)
        box(canvas, Color.rgb(52, 69, 62), w * .17f, 0f, w * .83f, h)
        box(canvas, Color.rgb(72, 76, 82), w * .22f, 0f, w * .78f, h)
        val shift = (distance * .015f % (h / 8f))
        for (i in -1..9) {
            val sy = i * h / 8f + shift
            box(canvas, Color.rgb(225, 210, 151), w * .495f, sy, w * .505f, sy + h / 22f)
            box(canvas, if (i % 2 == 0) Color.WHITE else Color.rgb(214, 100, 94), w * .19f, sy, w * .22f, sy + h / 8f)
            box(canvas, if (i % 2 == 0) Color.rgb(214, 100, 94) else Color.WHITE, w * .78f, sy, w * .81f, sy + h / 8f)
        }
        for (gate in gates) {
            val left = (gate.center - gapWidth / 2f) * w
            val right = (gate.center + gapWidth / 2f) * w
            val top = gate.y * h
            val bottom = top + gateHeight * h
            box(canvas, Color.rgb(232, 153, 83), w * .22f, top, left, bottom)
            box(canvas, Color.rgb(232, 153, 83), right, top, w * .78f, bottom)
            box(canvas, Color.rgb(255, 225, 133), left - 3f * dp, top, left, bottom)
            box(canvas, Color.rgb(255, 225, 133), right, top, right + 3f * dp, bottom)
        }
        val rx = x * w; val ry = roverY * h
        val rw = 13f * dp; val rh = 20f * dp
        box(canvas, Color.rgb(22, 25, 35), rx-rw*.8f, ry-rh*.7f, rx+rw*.8f, ry+rh*.7f)
        box(canvas, purple, rx-rw*.6f, ry-rh*.8f, rx+rw*.6f, ry+rh*.8f)
        box(canvas, Color.rgb(178, 232, 239), rx-rw*.4f, ry-rh*.58f, rx+rw*.4f, ry-rh*.2f)
        box(canvas, Color.rgb(244, 208, 117), rx-rw*.4f, ry-rh*.84f, rx+rw*.4f, ry-rh*.72f)
        paint.color = Color.WHITE; paint.textSize = 12f * dp; paint.typeface = android.graphics.Typeface.MONOSPACE
        canvas.drawText("OBSTACLES $passed", 12f * dp, 20f * dp, paint)
        canvas.drawText("SPEED ${abs(speed * 100).toInt()}   DIST ${distance.toInt()}m", 12f * dp, h - 12f * dp, paint)
    }
}
