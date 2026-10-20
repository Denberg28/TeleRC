package io.github.denberg28.telerc

import android.graphics.Canvas
import android.graphics.Paint

/** Quiet, deterministic scenery below the actual walls, markers, coins and vehicle. */
internal object MazeEnvironmentRenderer {
    fun draw(canvas:Canvas,paint:Paint,maze:MazeLayout,scale:Float,ox:Float,oy:Float) {
        val environment=MazeEnvironment.forLevel(maze.level)
        canvas.save(); canvas.translate(ox,oy); canvas.scale(scale,scale)
        canvas.clipRect(.5f,.5f,maze.width-.5f,maze.height-.5f)
        paint.style=Paint.Style.FILL; paint.color=environment.floor
        canvas.drawPaint(paint)
        paint.color=environment.detail
        when(environment) {
            MazeEnvironment.CITY -> {
                paint.style=Paint.Style.STROKE; paint.strokeWidth=.025f
                for(x in 1 until maze.width) canvas.drawLine(x.toFloat(),.5f,x.toFloat(),maze.height-.5f,paint)
                for(y in 1 until maze.height) canvas.drawLine(.5f,y.toFloat(),maze.width-.5f,y.toFloat(),paint)
                paint.style=Paint.Style.FILL
                for(y in 2 until maze.height step 4) for(x in 2 until maze.width step 4) {
                    if(!maze.wall(x,y)) continue
                    canvas.drawRect(x+.16f,y+.08f,x+.79f,y+.85f,paint)
                    paint.color=environment.floor
                    canvas.drawRect(x+.30f,y+.22f,x+.42f,y+.37f,paint)
                    canvas.drawRect(x+.56f,y+.50f,x+.68f,y+.65f,paint)
                    paint.color=environment.detail
                }
            }
            MazeEnvironment.FOREST -> {
                for(y in 1 until maze.height step 2) for(x in 1 until maze.width step 2) {
                    val px=x+.22f; val py=y+.20f
                    canvas.drawCircle(px,py,.20f,paint)
                    canvas.drawCircle(px+.18f,py+.05f,.15f,paint)
                    canvas.drawCircle(px+.08f,py-.13f,.14f,paint)
                }
                paint.style=Paint.Style.STROKE; paint.strokeWidth=.04f
                for(y in 2 until maze.height step 3) for(x in 3 until maze.width step 5)
                    canvas.drawArc(x-.4f,y-.1f,x+.4f,y+.5f,20f,135f,false,paint)
            }
            MazeEnvironment.DESERT -> {
                paint.style=Paint.Style.STROKE; paint.strokeWidth=.035f
                for(y in 1 until maze.height step 2) for(x in 1 until maze.width step 3) {
                    canvas.drawArc(x-.8f,y-.4f,x+1.6f,y+.6f,195f,145f,false,paint)
                    canvas.drawArc(x-.5f,y-.2f,x+1.9f,y+.8f,195f,145f,false,paint)
                }
                paint.strokeWidth=.08f; paint.strokeCap=Paint.Cap.ROUND
                for(y in 2 until maze.height step 4) for(x in 2 until maze.width step 6) {
                    canvas.drawLine(x+.2f,y-.2f,x+.2f,y+.3f,paint)
                    canvas.drawLine(x+.2f,y+.05f,x+.02f,y-.05f,paint)
                    canvas.drawLine(x+.2f,y+.12f,x+.40f,y-.06f,paint)
                }
            }
            MazeEnvironment.SPACE -> {
                paint.style=Paint.Style.FILL
                for(y in 1 until maze.height) for(x in 1 until maze.width) {
                    val hash=(x*73+y*151+maze.seed).let { it xor (it ushr 3) }
                    if(hash%3!=0) continue
                    val px=x+(hash and 7)/10f; val py=y+((hash ushr 3) and 7)/10f
                    canvas.drawCircle(px,py,if(hash%7==0) .055f else .025f,paint)
                }
                canvas.drawCircle(maze.width*.76f,maze.height*.29f,.75f,paint)
                paint.style=Paint.Style.STROKE; paint.strokeWidth=.045f
                canvas.drawOval(maze.width*.76f-1.1f,maze.height*.29f-.30f,
                    maze.width*.76f+1.1f,maze.height*.29f+.30f,paint)
            }
        }
        paint.style=Paint.Style.FILL; paint.strokeCap=Paint.Cap.BUTT
        canvas.restore()
    }
}
