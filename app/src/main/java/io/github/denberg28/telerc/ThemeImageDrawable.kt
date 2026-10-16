package io.github.denberg28.telerc

import android.graphics.*
import android.graphics.drawable.Drawable

/** Center-cropped photo with a theme-colored scrim; cards retain their readable fill. */
class ThemeImageDrawable(private val bitmap: Bitmap, private val tint: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas) {
        val scale = maxOf(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val left = bounds.exactCenterX() - w / 2; val top = bounds.exactCenterY() - h / 2
        val save = canvas.save(); canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), paint)
        canvas.drawColor((tint and 0x00ffffff) or 0x88000000.toInt())
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
