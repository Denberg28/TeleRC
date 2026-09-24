package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Offline arcade course. These inputs never reach the MAVLink transport. */
class TestDriveView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purple = Color.rgb(112, 88, 166)
    private var steer = 0f
    private var throttle = 0f
    private var x = 0.5f
    private var y = 0.77f
    private var angle = 0f // zero points up
    private var speed = 0f
    private var distance = 0f
    private var lastFrame = 0L
    private var running = true
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            if (lastFrame != 0L) advance(((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f))
            lastFrame = now
            invalidate()
            postDelayed(this, 16)
        }
    }

    init { contentDescription = "Offline retro rover driving course" }

    fun setSteering(value: Int) { steer = ((value - 1500) / 500f).coerceIn(-1f, 1f) }
    fun setDrive(value: Int) { throttle = ((value - 1500) / 500f).coerceIn(-1f, 1f) }

    fun resetCourse() {
        x = 0.5f; y = 0.77f; angle = 0f; speed = 0f; distance = 0f
        steer = 0f; throttle = 0f
        invalidate()
    }

    fun stop() { running = false; removeCallbacks(frame); lastFrame = 0L; steer = 0f; throttle = 0f }

    fun resume() { if (!running && isAttachedToWindow) { running = true; lastFrame = 0L; post(frame) } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = false; resume()
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    private fun advance(dt: Float) {
        val desired = throttle * 0.38f
        speed += (desired - speed) * (dt * if (throttle == 0f) 3.2f else 2.3f).coerceAtMost(1f)
        if (kotlin.math.abs(speed) < 0.001f) speed = 0f
        angle += steer * speed * dt * 3.0f
        val dx = sin(angle) * speed * dt
        val dy = -cos(angle) * speed * dt
        x = (x + dx).coerceIn(0.04f, 0.96f)
        y = (y + dy).coerceIn(0.07f, 0.94f)
        distance += kotlin.math.abs(speed) * dt * 100f
    }

    private fun box(canvas: Canvas, color: Int, left: Float, top: Float, right: Float, bottom: Float) {
        paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawRect(left, top, right, bottom, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val scale = resources.displayMetrics.density
        box(canvas, Color.rgb(30, 40, 48), 0f, 0f, w, h)
        // A flat, top-down pixel-inspired test track with visible direction and motion.
        box(canvas, Color.rgb(52, 69, 62), w * .17f, 0f, w * .83f, h)
        box(canvas, Color.rgb(72, 76, 82), w * .22f, 0f, w * .78f, h)
        for (i in 0..12) {
            val stripeY = i * h / 12f
            box(canvas, Color.rgb(225, 210, 151), w * .495f, stripeY, w * .505f, stripeY + h / 26f)
        }
        for (i in 0..9) {
            val markerY = i * h / 9f
            box(canvas, if (i % 2 == 0) Color.WHITE else Color.rgb(214, 100, 94), w * .19f, markerY, w * .22f, markerY + h / 9f)
            box(canvas, if (i % 2 == 0) Color.rgb(214, 100, 94) else Color.WHITE, w * .78f, markerY, w * .81f, markerY + h / 9f)
        }
        val roverX = x * w; val roverY = y * h
        canvas.save()
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat(), roverX, roverY)
        val rw = 13f * scale; val rh = 20f * scale
        box(canvas, Color.rgb(22, 25, 35), roverX-rw*.8f, roverY-rh*.7f, roverX+rw*.8f, roverY+rh*.7f)
        box(canvas, purple, roverX-rw*.6f, roverY-rh*.8f, roverX+rw*.6f, roverY+rh*.8f)
        box(canvas, Color.rgb(178, 232, 239), roverX-rw*.4f, roverY-rh*.58f, roverX+rw*.4f, roverY-rh*.2f)
        box(canvas, Color.rgb(244, 208, 117), roverX-rw*.4f, roverY-rh*.84f, roverX+rw*.4f, roverY-rh*.72f)
        canvas.restore()
        paint.color = Color.WHITE; paint.textSize = 12f * scale; paint.typeface = android.graphics.Typeface.MONOSPACE
        canvas.drawText("TEST COURSE", 12f * scale, 20f * scale, paint)
        canvas.drawText("SPEED ${kotlin.math.abs(speed * 100).toInt()}   DIST ${distance.toInt()}m", 12f * scale, h - 12f * scale, paint)
    }
}
