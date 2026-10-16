package io.github.denberg28.telerc

import kotlin.math.*
import kotlin.random.Random

/** Seeded perfect maze: every passage belongs to one connected, acyclic course. */
internal class MazeLayout(val seed: Int, val width: Int, val height: Int, private val walls: BooleanArray) {
    val startX = 1.5f
    val startY = height - 1.5f
    val endX = width - 1.5f
    val endY = 1.5f
    fun wall(x: Int, y: Int) = x !in 0 until width || y !in 0 until height || walls[y * width + x]
    fun fits(x: Float, y: Float, radius: Float = RADIUS): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        for (cy in floor(y-radius).toInt()..floor(y+radius).toInt()) {
            for (cx in floor(x-radius).toInt()..floor(x+radius).toInt()) {
                if (!wall(cx, cy)) continue
                val dx = x - x.coerceIn(cx.toFloat(), cx+1f)
                val dy = y - y.coerceIn(cy.toFloat(), cy+1f)
                if (dx*dx + dy*dy < radius*radius) return false
            }
        }
        return true
    }
    companion object {
        const val RADIUS = .20f
        fun generate(seed: Int): MazeLayout {
            val w = 15; val h = 11
            val walls = BooleanArray(w*h) { true }
            val random = Random(seed)
            val stack = mutableListOf(1 to h-2)
            walls[(h-2)*w+1] = false
            while (stack.isNotEmpty()) {
                val (x,y) = stack.last()
                val next = listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2)
                    .map { (dx,dy) -> x+dx to y+dy }
                    .filter { (nx,ny) -> nx in 1 until w-1 && ny in 1 until h-1 && walls[ny*w+nx] }
                if (next.isEmpty()) { stack.removeAt(stack.lastIndex); continue }
                val (nx,ny) = next[random.nextInt(next.size)]
                walls[((y+ny)/2)*w+(x+nx)/2] = false
                walls[ny*w+nx] = false
                stack.add(nx to ny)
            }
            return MazeLayout(seed,w,h,walls)
        }
    }
}

/** Local input and monotonic active time only; deliberately has no transport dependency. */
internal class MazePractice(val maze: MazeLayout) {
    enum class State { READY, RUNNING, PAUSED, CLEARED }
    var state = State.READY; private set
    var elapsedMillis = 0L; private set
    var pose = initialPose(); private set
    private var steer = 0f
    private var drive = 0f
    private fun initialPose() = RoverPose(maze.startX, maze.startY,
        if (!maze.wall(2, maze.height-2)) (PI/2).toFloat() else 0f)
    fun steering(value: Int) { steer = axis(value) }
    fun drive(value: Int) { drive = axis(value) }
    private fun axis(value: Int): Float {
        val delta = value.coerceIn(1100,1900)-1500
        return if (abs(delta)<=25) 0f else sign(delta.toFloat())*(abs(delta)-25)/375f
    }
    fun start() {
        if (state == State.CLEARED) return
        steer = 0f; drive = 0f
        state = State.RUNNING
    }
    fun pause() {
        if (state == State.RUNNING) state = State.PAUSED
        steer = 0f; drive = 0f
        pose = pose.copy(speed=0f,leftTrack=0f,rightTrack=0f)
    }
    fun reset() {
        state = State.READY; elapsedMillis = 0L; pose = initialPose(); steer = 0f; drive = 0f
    }
    /** Returns true once, on the frame that reaches END. Long frames cannot tunnel through walls. */
    fun advance(milliseconds: Long): Boolean {
        if (state != State.RUNNING || milliseconds <= 0) return false
        elapsedMillis = (elapsedMillis + milliseconds.coerceAtMost(86_400_000L)).coerceAtMost(86_400_000L)
        val dt = milliseconds.coerceAtMost(50L)/1000f
        val next = roverStep(pose,steer,drive,dt)
        val x = pose.x + (next.x-pose.x)*4f
        val y = pose.y + (next.y-pose.y)*4f
        // Resolve each axis separately so a glancing wall contact can slide along the passage.
        val safeX = if (maze.fits(x,pose.y)) x else pose.x
        val safeY = if (maze.fits(safeX,y)) y else pose.y
        pose = next.copy(x=safeX,y=safeY)
        if (hypot(pose.x-maze.endX,pose.y-maze.endY) > .30f) return false
        state = State.CLEARED; steer=0f; drive=0f
        pose = pose.copy(speed=0f,leftTrack=0f,rightTrack=0f)
        return true
    }
}

internal data class MazeClear(val seed: Int, val millis: Long, val at: Long)
internal object MazeRecords {
    const val LIMIT = 50
    fun decode(raw: String): List<MazeClear> = raw.lineSequence().take(LIMIT).mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size != 3) null else {
            val seed = parts[0].toIntOrNull(); val time = parts[1].toLongOrNull(); val at = parts[2].toLongOrNull()
            if (seed==null || time==null || time !in 1..86_400_000L || at==null || at<=0) null
            else MazeClear(seed,time,at)
        }
    }.toList()
    fun encode(records: List<MazeClear>) = records.take(LIMIT).joinToString("\n") { "${it.seed},${it.millis},${it.at}" }
    fun add(records: List<MazeClear>, clear: MazeClear) = (listOf(clear)+records).take(LIMIT)
    fun time(millis: Long): String = java.lang.String.format(java.util.Locale.US,"%02d:%02d.%02d",
        millis/60_000, millis/1000%60, millis/10%100)
}
