package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MazePracticeTest {
    @Test fun generatedCoursesAreConnectedClosedAndReproducible() {
        val signatures=mutableSetOf<String>()
        for(seed in 0..199) {
            val maze=MazeLayout.generate(seed)
            val again=MazeLayout.generate(seed)
            val signature=(0 until maze.height).joinToString("\n") { y ->
                (0 until maze.width).map { x -> if(maze.wall(x,y)) '#' else '.' }.joinToString("")
            }
            signatures.add(signature)
            val reached=mutableSetOf(1 to maze.height-2)
            val queue=java.util.ArrayDeque<Pair<Int,Int>>().apply { add(1 to maze.height-2) }
            while(queue.isNotEmpty()) {
                val (x,y)=queue.removeFirst()
                for((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                    val p=x+dx to y+dy
                    if(!maze.wall(p.first,p.second) && reached.add(p)) queue.add(p)
                }
            }
            assertTrue(reached.contains(maze.width-2 to 1))
            var passages=0; var edges=0
            for(y in 0 until maze.height) for(x in 0 until maze.width) {
                assertEquals(maze.wall(x,y),again.wall(x,y))
                if(x==0 || y==0 || x==maze.width-1 || y==maze.height-1) assertTrue(maze.wall(x,y))
                if(!maze.wall(x,y)) {
                    passages++
                    if(!maze.wall(x+1,y)) edges++
                    if(!maze.wall(x,y+1)) edges++
                    assertTrue(maze.fits(x+.5f,y+.5f))
                }
            }
            assertEquals(passages,reached.size)
            assertEquals(passages-1,edges) // Perfect maze: no disconnected areas or cycles.
        }
        // Different seeds can legitimately produce the same smaller maze.
        assertTrue("Too little course variation: ${signatures.size}/200",signatures.size>=180)
    }
    private fun course(vararg rows:String): MazePractice {
        return MazePractice(MazeLayout(42,rows[0].length,rows.size,
            rows.joinToString("").map { it=='#' }.toBooleanArray()))
    }
    @Test fun controlsStayLocalPausedAndRetryClearsInputs() {
        val p=course("#####","#...#","###.#","#...#","#####")
        val initial=p.pose
        p.drive(1900); p.advance(500)
        assertEquals(initial,p.pose); assertEquals(0L,p.elapsedMillis)
        p.start(); p.drive(1900)
        repeat(50) { p.advance(20) }
        assertNotEquals(initial.x,p.pose.x)
        p.pause(); val paused=p.pose
        p.drive(1900); p.steering(1900); p.advance(5000)
        assertEquals(paused,p.pose); assertEquals(1000L,p.elapsedMillis)
        p.start(); repeat(20) { p.advance(20) }
        assertEquals(paused.x,p.pose.x,0f) // Resume discards held input.
        p.reset(); assertEquals(initial,p.pose); assertEquals(0L,p.elapsedMillis)
        assertEquals(MazePractice.State.READY,p.state)
    }
    @Test fun wallsBlockLongFramesAndRepeatedDriveInBothDirections() {
        val p=course("#####","#...#","###.#","#...#","#####")
        p.start(); p.drive(1900)
        repeat(200) { p.advance(1000) }
        assertTrue(p.maze.fits(p.pose.x,p.pose.y))
        assertTrue(p.pose.x<3.81f); assertEquals(3.5f,p.pose.y,.01f)
        assertEquals(MazePractice.State.RUNNING,p.state)
        p.drive(1100); repeat(200) { p.advance(50) }
        assertTrue(p.maze.fits(p.pose.x,p.pose.y)); assertTrue(p.pose.x>1.19f)
    }
    @Test fun finishRecordsOnceAndFreezesTimeAndMovement() {
        val p=course("#####","#...#","#####")
        p.start(); p.drive(1900)
        var clears=0
        repeat(300) { if(p.advance(20)) clears++ }
        assertEquals(1,clears); assertEquals(MazePractice.State.CLEARED,p.state)
        assertTrue(p.elapsedMillis in 500..3000)
        val time=p.elapsedMillis; val pose=p.pose
        p.start(); p.drive(1100); assertFalse(p.advance(5000))
        assertEquals(time,p.elapsedMillis); assertEquals(pose,p.pose)
        p.reset(); assertEquals(MazePractice.State.READY,p.state)
    }
    @Test fun generatedMazeCanBeClearedUsingSteerAndDriveOnly() {
        for(level in listOf(1,6,11,16,20)) driveToEnd(MazePractice(MazeLevels.layout(level)))
    }
    private fun driveToEnd(p: MazePractice) {
        val maze=p.maze
        val prev=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>>()
        val start=1 to maze.height-2; val end=maze.endX.toInt() to maze.endY.toInt()
        val queue=java.util.ArrayDeque<Pair<Int,Int>>().apply { add(start) }
        val seen=mutableSetOf(start)
        while(queue.isNotEmpty()) {
            val v=queue.removeFirst()
            for((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                val n=v.first+dx to v.second+dy
                if(!maze.wall(n.first,n.second) && seen.add(n)) { prev[n]=v; queue.add(n) }
            }
        }
        val path=mutableListOf(end)
        while(path.last()!=start) path.add(prev.getValue(path.last()))
        path.reverse(); p.start()
        for(cell in path.drop(1)) {
            var steps=0
            while(hypot(cell.first+.5f-p.pose.x,cell.second+.5f-p.pose.y)>.12f && p.state!=MazePractice.State.CLEARED && steps++<1500) {
                val dx=cell.first+.5f-p.pose.x; val dy=cell.second+.5f-p.pose.y
                val angle=atan2(dx,-dy)-p.pose.heading
                val error=atan2(sin(angle),cos(angle))
                p.steering((1500+error.coerceIn(-1f,1f)*400).roundToInt())
                p.drive(if(abs(error)<.15f) 1740 else 1500)
                p.advance(20)
                assertTrue(maze.fits(p.pose.x,p.pose.y))
            }
            assertTrue("Could not reach $cell from ${p.pose}",steps<1500)
        }
        assertEquals(MazePractice.State.CLEARED,p.state)
        assertTrue(p.bumps < MazePractice.BUMP_LIMIT)
    }
    @Test fun thinWallsLeaveVisibleFreeSpaceAndBlockSegmentsAndCorners() {
        val maze=course("#####","#...#","###.#","#...#","#####").maze
        assertTrue(maze.fits(1.5f,2.25f,.1f)) // Space the old full-cell wall filled.
        assertFalse(maze.fits(1.5f,2.35f,.1f)) // The actual thin wall still blocks.
        assertFalse(maze.fits(2.5f,2.5f,.1f)) // Junction center.
        assertFalse(maze.fits(2.68f,2.68f,.2f)) // No diagonal gap at an end/corner.
        assertTrue(maze.fits(1.5f,1.30f)) // Full larger rover fits near the outer wall.
        assertFalse(maze.fits(1.5f,1.20f))
        assertFalse(maze.fits(-10f,-10f))
        assertFalse(maze.fits(Float.NaN,1.5f))
        assertFalse(maze.fits(1.5f,1.5f,Float.NaN))
    }

    @Test fun everyLevelIsFixedTallerAndEndsAtTheLongestRouteFromStart() {
        val signatures=mutableSetOf<String>()
        for(level in 1..MazeLevels.COUNT) {
            val maze=MazeLevels.layout(level); val again=MazeLevels.layout(level)
            assertEquals(level,maze.level)
            assertTrue(maze.height>=9)
            val signature=(0 until maze.height).joinToString("\n") { y ->
                (0 until maze.width).joinToString("") { x ->
                    assertEquals(maze.wall(x,y),again.wall(x,y))
                    if(maze.wall(x,y)) "#" else "."
                }
            }
            assertTrue(signatures.add(signature))
            val start=1 to maze.height-2
            val distance=mutableMapOf(start to 0)
            val queue=java.util.ArrayDeque<Pair<Int,Int>>().apply { add(start) }
            while(queue.isNotEmpty()) {
                val cell=queue.removeFirst()
                for((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                    val n=cell.first+dx to cell.second+dy
                    if(!maze.wall(n.first,n.second) && n !in distance) {
                        distance[n]=distance.getValue(cell)+1; queue.add(n)
                    }
                }
            }
            val goal=maze.endX.toInt() to maze.endY.toInt()
            assertEquals(distance.values.maxOrNull(),distance[goal])
            assertTrue(maze.fits(maze.endX,maze.endY))
            var passages=0; var edges=0
            for(y in 0 until maze.height) for(x in 0 until maze.width) {
                if(x==0 || y==0 || x==maze.width-1 || y==maze.height-1) assertTrue(maze.wall(x,y))
                if(!maze.wall(x,y)) {
                    passages++; if(!maze.wall(x+1,y)) edges++; if(!maze.wall(x,y+1)) edges++
                }
            }
            assertEquals(passages,distance.size); assertEquals(passages-1,edges)
            assertEquals(maze.endX,again.endX,0f); assertEquals(maze.endY,again.endY,0f)
        }
    }
    @Test fun threeSeparateBumpsStopAndFreezeRunUntilReset() {
        // Straight lower corridor with the finish beyond a turn: never clear by accident.
        val p=course("#######","#.....#","#####.#","#.....#","#######")
        p.start(); p.drive(1900); repeat(300) { p.advance(20) }
        assertEquals(1,p.bumps) // Holding into the wall is one impact.
        val atWall=p.pose
        p.drive(1500); repeat(100) { p.advance(20) }
        p.drive(1900); repeat(100) { p.advance(20) }
        assertEquals(1,p.bumps) // Neutral/re-push without retreat is still the same contact.
        p.pause(); p.start(); p.drive(1900); repeat(100) { p.advance(20) }
        assertEquals(1,p.bumps) // Pause does not grant a fresh bump at the same wall.
        p.drive(1100); repeat(28) { p.advance(20) }
        assertTrue(p.pose.x<atWall.x-.15f)
        p.drive(1900); repeat(150) { p.advance(20) }
        assertEquals(2,p.bumps); assertEquals(MazePractice.State.RUNNING,p.state)
        p.drive(1100); repeat(28) { p.advance(20) }
        p.drive(1900); repeat(150) { p.advance(20) }
        assertEquals(3,p.bumps); assertEquals(MazePractice.State.FAILED,p.state)
        assertEquals(0f,p.pose.speed,0f); assertEquals(0f,p.pose.leftTrack,0f)
        val stopped=p.pose; val time=p.elapsedMillis
        p.pause(); p.start(); p.drive(1100); repeat(100) { assertFalse(p.advance(20)) }
        assertEquals(stopped,p.pose); assertEquals(time,p.elapsedMillis)
        assertEquals(MazePractice.State.FAILED,p.state)
        p.reset(); assertEquals(0,p.bumps); assertEquals(0L,p.elapsedMillis)
        assertEquals(MazePractice.State.READY,p.state)
        p.start(); repeat(100) { p.advance(20) }
        assertEquals(1.5f,p.pose.x,0f) // Reset discards held reverse/steer.
    }
    @Test fun progressUnlocksSequentiallyPersistsAndSanitizesInvalidValues() {
        var p=MazeProgress.restore(99,-4)
        assertEquals(1,p.current); assertEquals(1,p.unlocked)
        assertEquals(1,p.select(20).current)
        for(level in 1..MazeLevels.COUNT) {
            p=p.select(level)
            assertEquals(level,p.current)
            p=p.clear()
            assertEquals(level,p.completed)
            assertEquals(minOf(level+1,MazeLevels.COUNT),p.unlocked)
            assertEquals(p,MazeProgress.restore(p.current,p.completed))
        }
        assertEquals(20,p.select(1).clear().completed) // Replay never relocks levels.
        assertEquals(20,MazeProgress.restore(Int.MAX_VALUE,Int.MAX_VALUE).current)
    }
    @Test fun levelRecordsRoundTripWithoutReclassifyingLegacyClears() {
        val legacy=MazeRecords.decode("37,1200,1000,2").single()
        val current=MazeClear(37,1500,2000,3,1)
        assertEquals(0,legacy.level)
        assertEquals(listOf(current,legacy),MazeRecords.decode(MazeRecords.encode(listOf(current,legacy))))
        assertTrue(MazeRecords.decode("37,1200,1000,3,0\n37,1200,1000,3,21\n37,1200,1000,2,1").isEmpty())
    }
    @Test fun classicRecordsRemainReadableAndKeepTheirCourseVersion() {
        val classic=MazeRecords.decode("42,12340,1792110000000").single()
        assertEquals(1,classic.courseVersion)
        val modern=MazeClear(42,11000,1792111000000,MazeLayout.COURSE_VERSION)
        val all=MazeRecords.add(listOf(classic),modern)
        assertEquals(all,MazeRecords.decode(MazeRecords.encode(all)))
        assertEquals(listOf(modern),all.filter { it.courseVersion==MazeLayout.COURSE_VERSION })
        assertTrue(MazeRecords.decode("42,12340,1792110000000,999").isEmpty())
    }
    @Test fun boundedRecordsRoundTripAndRejectCorruptRows() {
        var records=emptyList<MazeClear>()
        for(i in 1..60) records=MazeRecords.add(records,MazeClear(i,i*100L,i*1000L))
        assertEquals(50,records.size); assertEquals(60,records.first().seed)
        assertEquals(records,MazeRecords.decode(MazeRecords.encode(records)))
        assertTrue(MazeRecords.decode("bad\n1,-5,10\n1,500,0\n1,0,3\n1,90000000,3").isEmpty())
        assertEquals("01:05.43",MazeRecords.time(65432))
    }
}
