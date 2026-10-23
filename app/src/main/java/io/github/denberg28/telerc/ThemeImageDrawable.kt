package io.github.denberg28.telerc

import android.graphics.*
import android.graphics.drawable.Drawable

/** Center-cropped photo at the chosen opacity over the plain theme background. */
class ThemeImageDrawable(private val bitmap: Bitmap, background: Int = Color.BLACK, opacityPercent: Int = 100) : Drawable() {
    private val imageAlpha = opacityPercent.coerceIn(0, 100) * 255 / 100
    private val base = Paint().apply { color = background }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = imageAlpha }
    private var drawableAlpha = 255
    override fun draw(canvas: Canvas) {
        val scale = maxOf(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val left = bounds.exactCenterX() - w / 2; val top = bounds.exactCenterY() - h / 2
        val save = if (drawableAlpha < 255) canvas.saveLayerAlpha(RectF(bounds), drawableAlpha) else canvas.save()
        canvas.clipRect(bounds)
        canvas.drawRect(bounds, base)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
