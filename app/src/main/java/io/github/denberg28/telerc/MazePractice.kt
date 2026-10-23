package io.github.denberg28.telerc

import kotlin.math.*
import kotlin.random.Random

/** World rectangles shared by painting and collision checks. */
internal data class MazeWall(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A persistent impact position in maze world coordinates. */
internal data class MazeBump(val x: Float, val y: Float)

/** Seeded perfect maze: every passage belongs to one connected, acyclic course. */
internal class MazeLayout(val seed: Int, val width: Int, val height: Int, private val walls: BooleanArray, val level: Int = 0) {
    val startX = 1.5f
    val startY = height - 1.5f
    // A perfect maze is a tree: the farthest passage gives the longest route from START.
    private val finish = run {
        val distance = IntArray(width * height) { -1 }
        val queue = java.util.ArrayDeque<Pair<Int, Int>>()
        val start = 1 to height - 2
        queue.add(start); distance[start.second * width + start.first] = 0
        var farthest = start
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (distance[y * width + x] > distance[farthest.second * width + farthest.first]) farthest = x to y
            for ((dx, dy) in listOf(1 to 0, 0 to -1, -1 to 0, 0 to 1)) {
                val nx = x + dx; val ny = y + dy
                if (!wall(nx, ny) && distance[ny * width + nx] < 0) {
                    distance[ny * width + nx] = distance[y * width + x] + 1
                    queue.add(nx to ny)
                }
            }
        }
        farthest
    }
    val endX = finish.first + .5f
    val endY = finish.second + .5f
    fun wall(x: Int, y: Int) = x !in 0 until width || y !in 0 until height || walls[y * width + x]
    // Connect the centers of adjacent wall cells. The old solid one-cell blocks
    // become thin continuous walls without introducing gaps at corners or junctions.
    val wallShapes: List<MazeWall> = buildList {
        val half = WALL_THICKNESS / 2
        for (y in 0 until height) for (x in 0 until width) {
            if (!wall(x,y)) continue
            val cx=x+.5f; val cy=y+.5f
            add(MazeWall(cx-half,cy-half,cx+half,cy+half))
            if (x+1<width && wall(x+1,y)) add(MazeWall(cx-half,cy-half,cx+1+half,cy+half))
            if (y+1<height && wall(x,y+1)) add(MazeWall(cx-half,cy-half,cx+half,cy+1+half))
        }
    }
    fun fits(x: Float, y: Float, radius: Float = RADIUS): Boolean {
        if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius<=0 ||
            x-radius<0 || y-radius<0 || x+radius>width || y+radius>height) return false
        for (wall in wallShapes) {
            val dx=x-x.coerceIn(wall.left,wall.right)
            val dy=y-y.coerceIn(wall.top,wall.bottom)
            if (dx*dx+dy*dy<radius*radius) return false
        }
        return true
    }
    fun bumpPoint(x: Float, y: Float): MazeBump {
        val nearest = wallShapes.minByOrNull { wall ->
            val dx=x-x.coerceIn(wall.left,wall.right)
            val dy=y-y.coerceIn(wall.top,wall.bottom)
            dx*dx+dy*dy
        }
        return nearest?.let { MazeBump(x.coerceIn(it.left,it.right),y.coerceIn(it.top,it.bottom)) }
            ?: MazeBump(x.coerceIn(0f,width.toFloat()),y.coerceIn(0f,height.toFloat()))
    }
    companion object {
        const val WALL_THICKNESS = .18f
        // Encloses the complete body and wheels in any orientation.
        const val RADIUS = .68f
        const val COURSE_VERSION = 3
        fun generate(seed: Int, w: Int = 15, h: Int = 7, level: Int = 0): MazeLayout {
            require(w in 5..31 && h in 3..19 && w % 2 == 1 && h % 2 == 1)
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
            return MazeLayout(seed,w,h,walls,level)
        }
    }
}

/** Fixed catalog; seeds and dimensions are part of course version 3, never shuffled. */
internal object MazeLevels {
    const val COUNT = 20
    private val seeds = intArrayOf(37, 83, 129, 211, 307, 419, 523, 631, 743, 857,
        971, 1087, 1201, 1327, 1453, 1583, 1721, 1861, 2003, 2153)
    fun layout(level: Int): MazeLayout {
        require(level in 1..COUNT)
        val tier = (level - 1) / 5
        return MazeLayout.generate(seeds[level - 1], 15 + tier * 4, 9 + tier * 2, level)
    }
}

/** Sanitizes saved progress; only a clear unlocks the next level. */
internal data class MazeProgress private constructor(val current: Int, val completed: Int) {
    val unlocked get() = (completed + 1).coerceAtMost(MazeLevels.COUNT)
    fun select(level: Int) = restore(level, completed)
    fun clear() = restore(current, maxOf(completed, current))
    companion object {
        fun restore(current: Int, completed: Int): MazeProgress {
            val safeCompleted = completed.coerceIn(0, MazeLevels.COUNT)
            return MazeProgress(current.coerceIn(1, (safeCompleted + 1).coerceAtMost(MazeLevels.COUNT)), safeCompleted)
        }
    }
}

/** Local input and monotonic active time only; deliberately has no transport dependency. */
internal class MazePractice(val maze: MazeLayout, coinSlots: List<Int> = emptyList(), coinSeed: Int = Random.nextInt(), replayCoin:Boolean = false) {
    private val coinRandom=Random(coinSeed)
    private val availableSlots=coinSlots.filter { MazeMarket.key(maze.level,it)!=null }.toMutableSet()
    private var replayEnabled=replayCoin
    private var replayPicked=false
    init { if(replayEnabled) availableSlots.add(MazeReplayCoins.SLOT) }
    private val pickedCoins=java.util.ArrayDeque<Int>()
    var coins:List<MazeCoin> = MazeCoins.spawn(maze,availableSlots.toList(),coinRandom.nextInt()); private set
    fun drainCollectedCoins():List<Int> = pickedCoins.toList().also { pickedCoins.clear() }
    /** Provisional replay pickup pays only on clear, at most once per attempt. */
    fun takeReplayReward():Boolean = (state==State.CLEARED && replayPicked).also { if(it) replayPicked=false }
    enum class State { READY, RUNNING, PAUSED, CLEARED, FAILED }
    var state = State.READY; private set
    var elapsedMillis = 0L; private set
    var pose = initialPose(); private set
    private val impacts = ArrayList<MazeBump>(BUMP_LIMIT)
    val bumpMarks: List<MazeBump> get() = impacts.toList()
    val bumps: Int get() = impacts.size
    private var contact: Pair<Float, Float>? = null
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
        if (state == State.CLEARED || state == State.FAILED) return
        steer = 0f; drive = 0f
        state = State.RUNNING
    }
    fun pause() {
        if (state == State.RUNNING) state = State.PAUSED
        steer = 0f; drive = 0f
        pose = pose.copy(speed=0f,leftTrack=0f,rightTrack=0f)
    }
    fun reset(replayCoin:Boolean=replayEnabled) {
        state = State.READY; elapsedMillis = 0L; pose = initialPose(); steer = 0f; drive = 0f
        impacts.clear(); contact = null
        replayEnabled=replayCoin; replayPicked=false
        availableSlots.remove(MazeReplayCoins.SLOT)
        if(replayEnabled) availableSlots.add(MazeReplayCoins.SLOT)
        coins=MazeCoins.spawn(maze,availableSlots.toList(),coinRandom.nextInt())
    }
    fun restart(replayCoin:Boolean=replayEnabled) { reset(replayCoin); start() }
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
        val blocked = safeX != x || safeY != y
        val xContactY=pose.y
        pose = next.copy(x=safeX,y=safeY)
        if (blocked && contact == null) {
            impacts.add(if (safeX != x) maze.bumpPoint(x,xContactY) else maze.bumpPoint(safeX,y))
            contact = pose.x to pose.y
            if (bumps >= BUMP_LIMIT) {
                state = State.FAILED; steer = 0f; drive = 0f
                pose = pose.copy(speed=0f,leftTrack=0f,rightTrack=0f)
                return false
            }
        } else if (!blocked) {
            // Neutral, pivoting and repeated pushes at the same wall cannot farm bumps.
            contact?.let { if (hypot(pose.x-it.first,pose.y-it.second) >= .15f && maze.fits(pose.x,pose.y,MazeLayout.RADIUS+.10f)) contact = null }
        }
        val collected=coins.filter { hypot(pose.x-it.x,pose.y-it.y)<=.50f }
        for(coin in collected) if(availableSlots.remove(coin.slot)) {
            if(coin.slot==MazeReplayCoins.SLOT) replayPicked=true else pickedCoins.add(coin.slot)
        }
        if(collected.isNotEmpty()) coins=coins.filterNot { it in collected }
        if (hypot(pose.x-maze.endX,pose.y-maze.endY) > .30f) return false
        state = State.CLEARED; steer=0f; drive=0f
        pose = pose.copy(speed=0f,leftTrack=0f,rightTrack=0f)
        return true
    }
    companion object { const val BUMP_LIMIT = 3 }
}

internal data class MazeClear(val seed: Int, val millis: Long, val at: Long, val courseVersion: Int = 1, val level: Int = 0)
internal object MazeRecords {
    const val LIMIT = 50
    fun decode(raw: String): List<MazeClear> = raw.lineSequence().take(LIMIT).mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size !in 3..5) null else {
            val seed = parts[0].toIntOrNull(); val time = parts[1].toLongOrNull(); val at = parts[2].toLongOrNull()
            val version=if(parts.size==3) 1 else parts[3].toIntOrNull()
            val level=if(parts.size==5) parts[4].toIntOrNull() else 0
            if (level==null || (parts.size==5 && (version!=3 || level !in 1..MazeLevels.COUNT)) || version==null || version !in 1..MazeLayout.COURSE_VERSION || seed==null || time==null || time !in 1..86_400_000L || at==null || at<=0) null
            else MazeClear(seed,time,at,version,level)
        }
    }.toList()
    fun encode(records: List<MazeClear>) = records.take(LIMIT).joinToString("\n") { "${it.seed},${it.millis},${it.at},${it.courseVersion}" + if(it.level>0) ",${it.level}" else "" }
    fun add(records: List<MazeClear>, clear: MazeClear) = (listOf(clear)+records).take(LIMIT)
    fun time(millis: Long): String = java.lang.String.format(java.util.Locale.US,"%02d:%02d.%02d",
        millis/60_000, millis/1000%60, millis/10%100)
}
