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
    var vehicleId="scout"
    var themeId="navy"
    var onCoin: ((Int) -> Unit)? = null
    private fun collectCoins() { for(slot in practice.drainCollectedCoins()) onCoin?.invoke(slot) }
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
            collectCoins()
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
    fun changed() { previous=0L; collectCoins(); onUpdate?.invoke(); reportFailure(); invalidate(); resume() }
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
        val theme=MazeMarket.item(themeId)
        paint.color=theme?.floor?.takeIf { it!=0 } ?: passageColor
        canvas.drawRect(ox+.5f*scale,oy+.5f*scale,ox+(maze.width-.5f)*scale,oy+(maze.height-.5f)*scale,paint)
        paint.color=theme?.wall?.takeIf { it!=0 } ?: wallColor
        for (wall in maze.wallShapes) {
            canvas.drawRect(ox+wall.left*scale,oy+wall.top*scale,
                ox+wall.right*scale,oy+wall.bottom*scale,paint)
        }
        for(coin in practice.coins) {
            val cx=ox+coin.x*scale; val cy=oy+coin.y*scale
            paint.style=Paint.Style.FILL; paint.color=Color.rgb(255,222,112)
            canvas.drawCircle(cx,cy,scale*.23f,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=(scale*.035f).coerceAtLeast(1f)
            paint.color=Color.rgb(181,119,25)
            canvas.drawCircle(cx,cy,scale*.18f,paint)
            canvas.drawLine(cx,cy-scale*.10f,cx,cy+scale*.10f,paint)
        }
        paint.style=Paint.Style.FILL
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
        canvas.save(); canvas.translate(ox,oy)
        val vehicle=MazeMarket.item(vehicleId) ?: MazeMarket.items.first()
        MazeVehicleRenderer.draw(canvas,paint,nose,practice.pose,scale,
            if(vehicle.id=="scout") roverColor else vehicle.color,vehicle.id)
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
