package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Spring-return rover input. Axis output is neutral immediately on release. */
class JoystickView(context: Context, private val vertical: Boolean, private val changed: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val accent = AppColors.accent(context)
    private val track = AppColors.softAccent(context)
    private val gradient = LinearGradient(0f, 0f, 1f, 0f, Color.TRANSPARENT, accent, Shader.TileMode.CLAMP)
    private val gradientMatrix = Matrix()
    private var position = StickPosition(0f, 0f)
    private var pointer = -1
    // Settings are edited only in Setup; entering Controls creates new sticks.
    private val response = ControlResponseStore.read(context)

    init {
        isEnabled = false
        contentDescription = if (vertical) "Drive joystick, forward up and reverse down" else "Steering joystick, left and right"
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun radius() = min(width * 0.40f, height * 0.44f).coerceAtLeast(1f)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val radius = radius()
        paint.style = Paint.Style.FILL
        paint.color = track
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        paint.color = accent
        canvas.drawCircle(cx, cy, radius, paint)
        // UI — Axis scale. Match Go's line/ticks and position light; travel stays at 62%.
        val travel = radius * 0.62f
        canvas.save()
        canvas.translate(cx, cy)
        if (vertical) canvas.rotate(-90f)
        paint.strokeWidth = dp(2f)
        paint.color = if (isEnabled) accent else Color.rgb(116, 144, 170)
        canvas.drawLine(-travel, 0f, travel, 0f, paint)
        for (tick in -2..2) {
            val x = tick * travel / 2f
            val half = radius * if (tick % 2 == 0) .055f else .028f
            canvas.drawLine(x, -half, x, half, paint)
        }
        val offset = (if (vertical) -position.y else position.x) * travel
        if (isEnabled && kotlin.math.abs(offset) > .5f) {
            gradientMatrix.setScale(offset, 1f)
            gradient.setLocalMatrix(gradientMatrix)
            paint.shader = gradient
            paint.strokeWidth = dp(5f)
            canvas.drawLine(0f, 0f, offset, 0f, paint)
            paint.shader = null
        }
        paint.style = Paint.Style.FILL
        paint.color = if (isEnabled) accent else Color.rgb(116, 144, 170)
        canvas.drawCircle(offset, 0f, radius * .09f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(offset, 0f, radius * .035f, paint)
        canvas.restore()
    }
    private fun input(x: Float, y: Float) {
        val radius = radius() * 0.62f
        position = limitStick((x - width / 2f) / radius, (y - height / 2f) / radius)
        changed(response.channel(position, vertical)); invalidate()
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
                if (pointer == -1) return true
                val index = event.findPointerIndex(pointer)
                if (index >= 0) input(event.getX(index), event.getY(index)) else reset()
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
