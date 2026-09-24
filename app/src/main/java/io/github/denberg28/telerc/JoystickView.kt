package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Spring-return rover input. Axis output is neutral immediately on release. */
class JoystickView(context: Context, private val vertical: Boolean, private val changed: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purple = Color.rgb(112, 88, 166)
    private val track = Color.rgb(233, 226, 245)
    private var position = StickPosition(0f, 0f)
    private var pointer = -1

    init {
        isEnabled = false
        contentDescription = if (vertical) "Drive joystick, forward up and reverse down" else "Steering joystick, left and right"
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val radius = min(width * 0.37f, height * 0.43f).coerceAtLeast(dp(24f))
        paint.style = Paint.Style.FILL
        paint.color = track
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        paint.color = purple
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (isEnabled) purple else Color.rgb(179, 167, 203)
        val travel = radius * 0.62f
        val knobX = cx + position.x * travel
        val knobY = cy + position.y * travel
        canvas.drawCircle(knobX, knobY, radius * 0.30f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(knobX, knobY, radius * 0.12f, paint)
    }
    private fun input(x: Float, y: Float) {
        val radius = min(width * 0.37f, height * 0.43f).coerceAtLeast(dp(24f)) * 0.62f
        position = limitStick((x - width / 2f) / radius, (y - height / 2f) / radius)
        changed(stickChannel(position, vertical)); invalidate()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
                input(event.x, event.y); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index >= 0) input(event.getX(index), event.getY(index))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) != pointer) return true
                release(); performClick(); return true
            }
        }
        return true
    }
    private fun release() {
        pointer = -1; changed(1500)
        position = StickPosition(0f, 0f); invalidate()
    }
    fun reset() {
        pointer = -1; position = StickPosition(0f, 0f); changed(1500); invalidate()
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) reset()
        invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
