package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/** Entire course fits in the Controls center panel. No map, GPS, USB or network access. */
internal class MazePracticeView(context: Context, val practice: MazePractice) : View(context) {
    var onUpdate: (() -> Unit)? = null
    var onClear: ((Long) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val surface = AppColors.surface(context)
    private val wallColor = AppColors.muted(context)
    private val passageColor = AppColors.softAccent(context)
    private val roverColor = AppColors.buttonFill(context)
    private var active = false
    private var previous = 0L
    private var lastLabel = -1L
    private val frame = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            val cleared = previous != 0L && practice.advance(now-previous)
            previous = now
            if (cleared) onClear?.invoke(practice.elapsedMillis)
            if (cleared || practice.elapsedMillis/100 != lastLabel) {
                lastLabel = practice.elapsedMillis/100; onUpdate?.invoke()
            }
            invalidate()
            if (practice.state == MazePractice.State.RUNNING) postDelayed(this,16) else active=false
        }
    }
    init { contentDescription = "Offline maze, START at lower left, END at upper right" }
    fun resume() {
        if (active || !isAttachedToWindow || practice.state != MazePractice.State.RUNNING) return
        active = true; previous = 0L; post(frame)
    }
    fun pause() {
        active=false; removeCallbacks(frame); previous=0L; practice.pause(); onUpdate?.invoke(); invalidate()
    }
    fun changed() { previous=0L; onUpdate?.invoke(); invalidate(); resume() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); resume() }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val maze=practice.maze
        val scale = minOf(width/(maze.width+1f),height/(maze.height+1f))
        if (scale<=0) return
        val ox=(width-maze.width*scale)/2; val oy=(height-maze.height*scale)/2
        paint.style=Paint.Style.FILL
        paint.color=surface
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        for (y in 0 until maze.height) for (x in 0 until maze.width) {
            paint.color=if (maze.wall(x,y)) wallColor else passageColor
            canvas.drawRect(ox+x*scale,oy+y*scale,ox+(x+1)*scale+.5f,oy+(y+1)*scale+.5f,paint)
        }
        fun marker(x:Float,y:Float,label:String) {
            paint.color=roverColor
            canvas.drawRoundRect(ox+(x-.44f)*scale,oy+(y-.44f)*scale,
                ox+(x+.44f)*scale,oy+(y+.44f)*scale,scale*.14f,scale*.14f,paint)
            paint.color=android.graphics.Color.WHITE
            paint.textAlign=Paint.Align.CENTER; paint.textSize=scale*.27f
            paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(label,ox+x*scale,oy+y*scale-(paint.ascent()+paint.descent())/2,paint)
        }
        marker(maze.startX,maze.startY,"START"); marker(maze.endX,maze.endY,"END")
        val p=practice.pose
        canvas.save()
        canvas.translate(ox+p.x*scale,oy+p.y*scale)
        canvas.rotate(Math.toDegrees(p.heading.toDouble()).toFloat())
        paint.color=roverColor
        canvas.drawRoundRect(-scale*.19f,-scale*.25f,scale*.19f,scale*.25f,scale*.06f,scale*.06f,paint)
        paint.color=android.graphics.Color.WHITE
        canvas.drawCircle(0f,-scale*.13f,scale*.07f,paint)
        canvas.restore()
    }
}
