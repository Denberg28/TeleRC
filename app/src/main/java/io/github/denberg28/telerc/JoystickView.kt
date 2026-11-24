package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Spring-return rover input adopted from TeleRC Go. Axis output is neutral immediately on release. */
class JoystickView(
    context: Context,
    private val vertical: Boolean,
    private val translucent: Boolean = true,
    private val receiverDeadbandUs: Int = ControlResponse.GO_RECEIVER_DEADBAND_US,
    private val proportionalResponse: Boolean = true,
    private val changed: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val accent = AppColors.accent(context)
    private val track = AppColors.softAccent(context)
    private var position = StickPosition(0f, 0f)
    private var channel = 1500
    private var pointer = -1
    // Settings are edited only in the Settings sheet; returning to Controls creates new sticks.
    private val response = ControlResponseStore.read(context)

    init {
        isEnabled = false
        contentDescription = if (vertical) "Drive joystick, forward up and reverse down" else "Steering joystick, left and right"
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun radius() = min(width * 0.40f, height * 0.44f).coerceAtLeast(1f)
    /** Center to the visible line endpoint, used for drawing and touch input. */
    private fun travel(radius: Float) = radius * 0.62f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = radius()
        if (!translucent) {
            drawClassic(canvas, cx, cy, radius)
            return
        }
        drawTranslucent(canvas, cx, cy, radius)
    }

    private fun drawClassic(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.color = track
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = accent
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (isEnabled) accent else Color.rgb(116, 144, 170)
        val knobX = cx + position.x * travel(radius)
        val knobY = cy + position.y * travel(radius)
        canvas.drawCircle(knobX, knobY, radius * 0.33f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(knobX, knobY, radius * 0.12f, paint)
    }

    // UI — Go translucent dial. Dark outlines keep ticks and cyan light readable over wallpaper.
    private fun drawTranslucent(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val travel = travel(radius)
        val active = isEnabled && channel != 1500

        paint.style = Paint.Style.FILL
        paint.color = 0x30071320
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.color = 0xA0081420.toInt()
        canvas.drawCircle(cx, cy, radius, paint)
        paint.strokeWidth = dp(1f)
        paint.color = if (isEnabled) 0xD5E4F2FF.toInt() else 0xA0CFDCE8.toInt()
        canvas.drawCircle(cx, cy, radius, paint)

        // Rotate only the scale and position marker, so up on Drive is forward.
        canvas.save()
        canvas.translate(cx, cy)
        if (vertical) canvas.rotate(-90f)
        paint.color = 0xB0081420.toInt()
        paint.strokeWidth = dp(3f)
        drawScale(canvas, travel, radius)
        paint.color = 0xB8DEE9F4.toInt()
        paint.strokeWidth = dp(1f)
        drawScale(canvas, travel, radius)

        // The line is the touch travel scale. Sensitivity changes the command,
        // while the marker follows the finger from center to the line endpoint.
        val axis = if (vertical) -position.y else position.x
        val offset = if (isEnabled) axis * travel else 0f
        val marker = radius * 0.075f
        if (active) {
            paint.color = 0xD08DEBFF.toInt()
            paint.strokeWidth = dp(2f)
            canvas.drawLine(0f, 0f, offset, 0f, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xE0081420.toInt()
        canvas.drawCircle(offset, 0f, marker + dp(2f), paint)
        when {
            active -> {
                paint.color = 0xFF8DEBFF.toInt()
                canvas.drawCircle(offset, 0f, marker, paint)
            }
            isEnabled -> {
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.5f)
                canvas.drawCircle(offset, 0f, marker, paint)
            }
            else -> {
                paint.color = 0xFFADBCCB.toInt()
                paint.strokeWidth = dp(2f)
                canvas.drawLine(-marker, 0f, marker, 0f, paint)
            }
        }
        canvas.restore()
    }

    private fun drawScale(canvas: Canvas, travel: Float, radius: Float) {
        val tick = radius * 0.055f
        canvas.drawLine(-travel, 0f, travel, 0f, paint)
        canvas.drawLine(-travel, -tick, -travel, tick, paint)
        canvas.drawLine(-travel / 2f, -tick / 2f, -travel / 2f, tick / 2f, paint)
        canvas.drawLine(0f, -tick, 0f, tick, paint)
        canvas.drawLine(travel / 2f, -tick / 2f, travel / 2f, tick / 2f, paint)
        canvas.drawLine(travel, -tick, travel, tick, paint)
    }

    private fun input(x: Float, y: Float) {
        val travel = travel(radius())
        position = limitStick(
            (x - width / 2f) / travel,
            (y - height / 2f) / travel,
            if (receiverDeadbandUs > 0) vertical else null
        )
        channel = if (proportionalResponse) response.proportionalChannel(position, vertical, receiverDeadbandUs)
        else response.channel(position, vertical, receiverDeadbandUs)
        changed(channel); invalidate()
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
                reset(); performClick(); return true
            }
        }
        return true
    }

    fun reset() {
        pointer = -1
        position = StickPosition(0f, 0f)
        channel = 1500
        changed(channel)
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) reset()
        invalidate()
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
