package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Path
import android.os.SystemClock
import android.view.View

/** Entire course fits in the Controls center panel. No map, GPS, USB or network access. */
internal class MazePracticeView(context: Context, val practice: MazePractice) : View(context) {
    var onUpdate: (() -> Unit)? = null
    var onClear: ((Long) -> Unit)? = null
    var onFailure: (() -> Unit)? = null
    private var failureReported = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val surface = AppColors.surface(context)
    private val wallColor = AppColors.muted(context)
    private val passageColor = AppColors.softAccent(context)
    private val roverColor = AppColors.buttonFill(context)
    private val nose = Path()
    private var active = false
    private var previous = 0L
    private var lastLabel = -1L
    private val frame = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            val oldBumps = practice.bumps
            val cleared = previous != 0L && practice.advance(now-previous)
            previous = now
            if (cleared) onClear?.invoke(practice.elapsedMillis)
            reportFailure()
            if (cleared || oldBumps != practice.bumps || practice.elapsedMillis/100 != lastLabel) {
                lastLabel = practice.elapsedMillis/100; onUpdate?.invoke()
            }
            invalidate()
            if (practice.state == MazePractice.State.RUNNING) postDelayed(this,16) else active=false
        }
    }
    init { contentDescription = "Offline level ${practice.maze.level}, START at lower left, END at the longest path from START" }
    private fun reportFailure() {
        if (practice.state != MazePractice.State.FAILED) failureReported = false
        else if (!failureReported) { failureReported = true; onFailure?.invoke() }
    }
    fun resume() {
        if (active || !isAttachedToWindow || practice.state != MazePractice.State.RUNNING) return
        active = true; previous = 0L; post(frame)
    }
    fun pause() {
        active=false; removeCallbacks(frame); previous=0L; practice.pause(); onUpdate?.invoke(); invalidate()
    }
    fun changed() { previous=0L; onUpdate?.invoke(); reportFailure(); invalidate(); resume() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post { if (isAttachedToWindow) reportFailure() }; resume() }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val maze=practice.maze
        val scale = minOf(width/maze.width.toFloat(),height/maze.height.toFloat())
        if (scale<=0) return
        val ox=(width-maze.width*scale)/2; val oy=(height-maze.height*scale)/2
        paint.style=Paint.Style.FILL
        paint.color=surface
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        paint.color=passageColor
        canvas.drawRect(ox+.5f*scale,oy+.5f*scale,ox+(maze.width-.5f)*scale,oy+(maze.height-.5f)*scale,paint)
        paint.color=wallColor
        for (wall in maze.wallShapes) {
            canvas.drawRect(ox+wall.left*scale,oy+wall.top*scale,
                ox+wall.right*scale,oy+wall.bottom*scale,paint)
        }
        fun marker(x:Float,y:Float,label:String) {
            paint.color=roverColor
            canvas.drawRoundRect(ox+(x-.68f)*scale,oy+(y-.30f)*scale,
                ox+(x+.68f)*scale,oy+(y+.30f)*scale,scale*.14f,scale*.14f,paint)
            paint.color=android.graphics.Color.WHITE
            paint.textAlign=Paint.Align.CENTER; paint.textSize=scale*.34f
            paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(label,ox+x*scale,oy+y*scale-(paint.ascent()+paint.descent())/2,paint)
        }
        marker(maze.startX,maze.startY,"START"); marker(maze.endX,maze.endY,"END")
        val p=practice.pose
        canvas.save()
        canvas.translate(ox+p.x*scale,oy+p.y*scale)
        canvas.rotate(Math.toDegrees(p.heading.toDouble()).toFloat())
        // Restore the earlier top-view rover: four wheels, colored body,
        // cyan windshield and a yellow front. A white outline stays visible in Dark.
        fun rect(color:Int,l:Float,t:Float,r:Float,b:Float) {
            paint.style=Paint.Style.FILL; paint.color=color
            canvas.drawRoundRect(l*scale,t*scale,r*scale,b*scale,scale*.055f,scale*.055f,paint)
        }
        fun wheel(l:Float,t:Float,r:Float,b:Float,output:Float) {
            rect(Color.rgb(22,25,35),l,t,r,b)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=(scale*.035f).coerceAtLeast(1f)
            paint.color=when { output>.08f -> Color.rgb(54,207,176)
                output<-.08f -> Color.rgb(238,147,88); else -> Color.rgb(211,224,237) }
            canvas.drawRoundRect(l*scale,t*scale,r*scale,b*scale,scale*.055f,scale*.055f,paint)
        }
        wheel(-.51f,-.40f,-.34f,-.12f,p.leftTrack)
        wheel(-.51f,.12f,-.34f,.40f,p.leftTrack)
        wheel(.34f,-.40f,.51f,-.12f,p.rightTrack)
        wheel(.34f,.12f,.51f,.40f,p.rightTrack)
        rect(roverColor,-.35f,-.54f,.35f,.54f)
        paint.style=Paint.Style.STROKE; paint.color=Color.WHITE
        paint.strokeWidth=(scale*.045f).coerceAtLeast(1.2f)
        canvas.drawRoundRect(-.35f*scale,-.54f*scale,.35f*scale,.54f*scale,scale*.07f,scale*.07f,paint)
        rect(Color.rgb(178,232,239),-.25f,-.29f,.25f,-.04f)
        rect(Color.rgb(244,208,117),-.27f,-.51f,.27f,-.40f)
        rect(Color.rgb(238,147,88),-.25f,.39f,-.11f,.47f)
        rect(Color.rgb(238,147,88),.11f,.39f,.25f,.47f)
        // Large front-pointing chevron; it rotates with the body, including in reverse.
        paint.style=Paint.Style.STROKE; paint.color=Color.WHITE
        paint.strokeWidth=(scale*.055f).coerceAtLeast(1.5f)
        paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        nose.reset(); nose.moveTo(-.14f*scale,.19f*scale)
        nose.lineTo(0f,.05f*scale); nose.lineTo(.14f*scale,.19f*scale)
        canvas.drawPath(nose,paint)
        paint.style=Paint.Style.FILL
        canvas.restore()
        // World-anchored, opaque impact dots have no timer or fade. Only reset clears them.
        paint.style=Paint.Style.FILL
        val dotRadius=maxOf(scale*.14f,resources.displayMetrics.density*2.5f)
        val outline=resources.displayMetrics.density*1f
        for (bump in practice.bumpMarks) {
            val bx=ox+bump.x*scale; val by=oy+bump.y*scale
            paint.color=Color.WHITE
            canvas.drawCircle(bx,by,dotRadius+outline,paint)
            paint.color=Color.rgb(239,68,68)
            canvas.drawCircle(bx,by,dotRadius,paint)
        }
    }
}
