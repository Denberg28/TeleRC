package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.view.View

/** Shared top-view cosmetics; all silhouettes/accessories fit the existing .68 collision radius. */
internal object MazeVehicleRenderer {
    fun draw(canvas:Canvas,paint:Paint,nose:Path,p:RoverPose,scale:Float,bodyColor:Int,style:String,
        accessories:Set<String> = emptySet()) {
        if(scale<=0) return
        canvas.save(); canvas.translate(p.x*scale,p.y*scale)
        canvas.rotate(Math.toDegrees(p.heading.toDouble()).toFloat()); canvas.scale(scale,scale)
        paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        val edge=(1.2f/scale).coerceAtLeast(.035f)
        fun rect(color:Int,l:Float,t:Float,r:Float,b:Float) {
            paint.style=Paint.Style.FILL; paint.color=color
            canvas.drawRoundRect(l,t,r,b,.045f,.045f,paint)
        }
        fun outline(l:Float,t:Float,r:Float,b:Float) {
            paint.style=Paint.Style.STROKE; paint.color=Color.WHITE; paint.strokeWidth=edge
            canvas.drawRoundRect(l,t,r,b,.055f,.055f,paint)
        }
        fun polygon(color:Int,vararg points:Float) {
            nose.reset(); nose.moveTo(points[0],points[1])
            for(i in 2 until points.size step 2) nose.lineTo(points[i],points[i+1])
            nose.close(); paint.style=Paint.Style.FILL; paint.color=color; canvas.drawPath(nose,paint)
            paint.style=Paint.Style.STROKE; paint.color=Color.WHITE; paint.strokeWidth=edge
            canvas.drawPath(nose,paint)
        }
        fun wheel(l:Float,t:Float,r:Float,b:Float,output:Float) {
            rect(0xff161923.toInt(),l,t,r,b)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=edge
            paint.color=when { output>.08f -> 0xff36cfb0.toInt(); output<-.08f -> 0xffee9358.toInt(); else -> 0xffd3e0ed.toInt() }
            canvas.drawRoundRect(l,t,r,b,.045f,.045f,paint)
        }
        when(style) {
            "skiff" -> {
                polygon(bodyColor,0f,-.61f,.31f,-.16f,.32f,.40f,.22f,.53f,-.22f,.53f,-.32f,.40f,-.31f,-.16f)
                rect(0xff18384a.toInt(),-.22f,-.05f,.22f,.36f)
                rect(0xffb2e8ef.toInt(),-.20f,-.20f,.20f,-.06f)
                rect(0xffe6edf3.toInt(),-.16f,.21f,.16f,.30f)
                rect(0xff303e4e.toInt(),-.10f,.45f,.10f,.61f)
            }
            "plane" -> {
                polygon(bodyColor,0f,-.61f,.11f,-.26f,.58f,.03f,.58f,.17f,.11f,.06f,
                    .09f,.35f,.27f,.46f,.27f,.54f,0f,.47f,-.27f,.54f,-.27f,.46f,
                    -.09f,.35f,-.11f,.06f,-.58f,.17f,-.58f,.03f,-.11f,-.26f)
                rect(0xffb2e8ef.toInt(),-.07f,-.31f,.07f,-.08f)
                rect(0xffd8e3eb.toInt(),-.29f,-.44f,.29f,-.40f)
            }
            "shuttle" -> {
                polygon(bodyColor,0f,-.61f,.16f,-.30f,.25f,.12f,.48f,.45f,.19f,.38f,
                    .12f,.54f,-.12f,.54f,-.19f,.38f,-.48f,.45f,-.25f,.12f,-.16f,-.30f)
                polygon(0xff34547b.toInt(),0f,-.35f,.11f,-.12f,-.11f,-.12f)
                rect(0xffe9a86a.toInt(),-.10f,.45f,.10f,.59f)
            }
            else -> {
                val halfWidth=when(style) { "buggy" -> .40f; "rally" -> .30f; "hauler" -> .36f; else -> .35f }
                val halfLength=when(style) { "buggy" -> .43f; "rally" -> .57f; "hauler" -> .53f; else -> .54f }
                wheel(-.51f,-.40f,-.34f,-.12f,p.leftTrack); wheel(-.51f,.12f,-.34f,.40f,p.leftTrack)
                wheel(.34f,-.40f,.51f,-.12f,p.rightTrack); wheel(.34f,.12f,.51f,.40f,p.rightTrack)
                rect(bodyColor,-halfWidth,-halfLength,halfWidth,halfLength); outline(-halfWidth,-halfLength,halfWidth,halfLength)
                when(style) {
                    "rally" -> rect(Color.WHITE,-.045f,-.51f,.045f,.49f)
                    "hauler" -> { rect(0xff292f26.toInt(),-.28f,.01f,.28f,.44f); rect(bodyColor,-.20f,.10f,.20f,.34f) }
                    "buggy" -> {
                        rect(0xff192226.toInt(),-.26f,-.08f,.26f,.31f)
                        rect(0xffd3e0ed.toInt(),-.03f,-.08f,.03f,.31f)
                        rect(0xffd3e0ed.toInt(),-.26f,.10f,.26f,.15f)
                    }
                }
                rect(0xffb2e8ef.toInt(),-.25f,-.29f,.25f,-.04f)
                rect(0xfff4d075.toInt(),-.27f,-.51f,.27f,-.40f)
                rect(0xffee9358.toInt(),-.25f,.39f,-.11f,.47f); rect(0xffee9358.toInt(),.11f,.39f,.25f,.47f)
            }
        }
        if("hood" in accessories) {
            polygon(0xffefc577.toInt(),-.15f,-.36f,.15f,-.36f,.19f,-.17f,-.19f,-.17f)
        }
        if("wheels" in accessories) {
            wheel(-.56f,-.27f,-.40f,.08f,p.leftTrack); wheel(.40f,-.27f,.56f,.08f,p.rightTrack)
            rect(0xff68d3b4.toInt(),-.55f,-.14f,-.41f,-.06f); rect(0xff68d3b4.toInt(),.41f,-.14f,.55f,-.06f)
        }
        if("armament" in accessories) {
            rect(0xffb1c3d3.toInt(),-.04f,-.57f,.04f,-.10f)
            paint.style=Paint.Style.FILL; paint.color=0xff506379.toInt(); canvas.drawCircle(0f,-.08f,.14f,paint)
            paint.style=Paint.Style.STROKE; paint.color=Color.WHITE; paint.strokeWidth=edge; canvas.drawCircle(0f,-.08f,.14f,paint)
        }
        if("exhaust" in accessories) {
            rect(0xff263343.toInt(),-.22f,.38f,-.11f,.58f); rect(0xff263343.toInt(),.11f,.38f,.22f,.58f)
            rect(0xffec9f6b.toInt(),-.21f,.48f,-.12f,.56f); rect(0xffec9f6b.toInt(),.12f,.48f,.21f,.56f)
        }
        if("spoiler" in accessories) {
            rect(0xff263343.toInt(),-.25f,.26f,-.19f,.46f); rect(0xff263343.toInt(),.19f,.26f,.25f,.46f)
            rect(0xffd6a4d9.toInt(),-.42f,.35f,.42f,.45f); outline(-.42f,.35f,.42f,.45f)
        }
        // White chevron remains on top of all cosmetics and always indicates the front.
        paint.style=Paint.Style.STROKE; paint.color=Color.WHITE; paint.strokeWidth=(1.5f/scale).coerceAtLeast(.05f)
        nose.reset(); nose.moveTo(-.13f,.20f); nose.lineTo(0f,.07f); nose.lineTo(.13f,.20f)
        canvas.drawPath(nose,paint)
        paint.style=Paint.Style.FILL; canvas.restore()
    }
}
internal class MazeMarketPreview(context:Context,private val item:MazeMarketItem):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val nose=Path()
    init { contentDescription="${item.name} preview" }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val scale=minOf(width/1.5f,height/1.5f)
        if(scale<=0) return
        val vehicle=if(item.kind==MazeItemKind.VEHICLE) item else MazeMarket.item("scout")!!
        MazeVehicleRenderer.draw(canvas,paint,nose,RoverPose(width/2f/scale,height/2f/scale),scale,vehicle.color,vehicle.id,
            if(item.kind==MazeItemKind.ACCESSORY) setOf(item.id) else emptySet())
    }
}
