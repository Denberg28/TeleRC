package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.view.View

/** The same cosmetic silhouettes in the maze and market. Physics stays unchanged. */
internal object MazeVehicleRenderer {
    fun draw(canvas:Canvas,paint:Paint,nose:Path,p:RoverPose,scale:Float,bodyColor:Int,style:String) {
        canvas.save()
        canvas.translate(p.x*scale,p.y*scale)
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
        val halfWidth=when(style) { "buggy" -> .40f; "rally" -> .30f; "hauler" -> .36f; else -> .35f }
        val halfLength=when(style) { "buggy" -> .43f; "rally" -> .57f; "hauler" -> .53f; else -> .54f }
        wheel(-.51f,-.40f,-.34f,-.12f,p.leftTrack)
        wheel(-.51f,.12f,-.34f,.40f,p.leftTrack)
        wheel(.34f,-.40f,.51f,-.12f,p.rightTrack)
        wheel(.34f,.12f,.51f,.40f,p.rightTrack)
        rect(bodyColor,-halfWidth,-halfLength,halfWidth,halfLength)
        paint.style=Paint.Style.STROKE; paint.color=Color.WHITE
        paint.strokeWidth=(scale*.045f).coerceAtLeast(1.2f)
        canvas.drawRoundRect(-halfWidth*scale,-halfLength*scale,halfWidth*scale,halfLength*scale,scale*.07f,scale*.07f,paint)
        if(style=="rally") {
            rect(Color.WHITE,-.055f,-halfLength+.04f,.055f,halfLength-.04f)
        } else if(style=="hauler") {
            rect(Color.rgb(42,47,38),-.27f,.02f,.27f,.42f)
            rect(bodyColor,-.20f,.10f,.20f,.34f)
        } else if(style=="buggy") {
            rect(Color.rgb(25,34,38),-.24f,-.08f,.24f,.31f)
            rect(Color.rgb(211,224,237),-.045f,-.08f,.045f,.31f)
        }
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
    }
}
internal class MazeMarketPreview(context:Context,private val item:MazeMarketItem):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val nose=Path()
    init { contentDescription="${item.name} preview" }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(item.kind==MazeItemKind.VEHICLE) {
            val scale=minOf(width/1.5f,height/1.5f)
            if(scale<=0) return
            MazeVehicleRenderer.draw(canvas,paint,nose,RoverPose(width/2f/scale,height/2f/scale),scale,item.color,item.id)
        } else {
            paint.style=Paint.Style.FILL; paint.color=if(item.floor!=0) item.floor else AppColors.softAccent(context)
            canvas.drawRoundRect(4f,4f,width-4f,height-4f,12f,12f,paint)
            paint.color=if(item.wall!=0) item.wall else AppColors.muted(context)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=4*resources.displayMetrics.density
            canvas.drawLine(width*.25f,height*.25f,width*.70f,height*.25f,paint)
            canvas.drawLine(width*.70f,height*.25f,width*.70f,height*.75f,paint)
        }
    }
}
