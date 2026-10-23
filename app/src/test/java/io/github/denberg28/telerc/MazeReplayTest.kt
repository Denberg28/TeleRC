package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MazeReplayTest {
    @Test fun chanceOnlyRollsForCompletedLevelsAndPersistsBothOutcomes() {
        val never:()->Int={ error("Saved or ineligible attempt rerolled") }
        assertFalse(MazeReplayCoins.offer(false,null,never))
        assertFalse(MazeReplayCoins.offer(false,true,never))
        assertFalse(MazeReplayCoins.offer(true,false,never))
        assertTrue(MazeReplayCoins.offer(true,true,never))
        assertEquals(listOf(true,false,false,false),(0..3).map { roll -> MazeReplayCoins.offer(true,null) { roll } })
    }
    @Test fun walletMigratesV2AndPreservesReplayEarningsThroughTransactionsAndReload() {
        var wallet=MazeWallet.decode("2|0,1,2,3,4,5|scout,hood|scout|hood")
        assertEquals(0,wallet.balance); assertTrue("hood" in wallet.accessories)
        repeat(25) { wallet=wallet.creditReplay()!! }
        assertEquals(25,wallet.balance)
        wallet=wallet.buy("rally")!!.equip("rally")!!.remove("hood")!!.claim(1,6)!!
        assertEquals(6,wallet.balance); assertEquals(25,wallet.replayCoins)
        val restored=MazeWallet.decode(wallet.encode())
        assertEquals(wallet.encode(),restored.encode()); assertEquals("rally",restored.vehicle)
        assertEquals(wallet.claimed,restored.claimed)
        assertEquals(6,restored.balance)
        for(raw in listOf("-1","1000001","2147483647","bad"))
            assertEquals(0,MazeWallet.decode("3||scout|scout||$raw").replayCoins)
        val capped=MazeWallet.decode("3||scout|scout||1000000")
        assertEquals(1_000_000,capped.balance); assertNull(capped.creditReplay())
    }
    @Test fun replayCoinFitsEveryCourseAndOnlyPaysAfterPickupAndClearOnce() {
        for(level in 1..20) {
            val p=MazePractice(MazeLevels.layout(level),coinSeed=47,replayCoin=true)
            val coin=p.coins.single()
            assertEquals(MazeReplayCoins.SLOT,coin.slot)
            assertTrue(p.maze.fits(coin.x,coin.y))
            assertFalse(coin.x==p.maze.startX && coin.y==p.maze.startY)
            assertFalse(coin.x==p.maze.endX && coin.y==p.maze.endY)
        }
        val p=MazePractice(MazeLevels.layout(1),coinSeed=91,replayCoin=true)
        p.start(); val coin=p.coins.single()
        driveTo(p,coin.x.toInt() to coin.y.toInt())
        assertTrue(p.coins.isEmpty()); assertTrue(p.drainCollectedCoins().isEmpty())
        assertFalse(p.takeReplayReward()) // Pickup alone cannot credit the wallet.
        p.pause(); assertFalse(p.takeReplayReward())
        p.restart(); assertEquals(MazePractice.State.RUNNING,p.state)
        assertEquals(1,p.coins.size); assertFalse(p.takeReplayReward())
        val retry=p.coins.single(); driveTo(p,retry.x.toInt() to retry.y.toInt())
        driveTo(p,p.maze.endX.toInt() to p.maze.endY.toInt())
        assertEquals(MazePractice.State.CLEARED,p.state)
        assertTrue(p.takeReplayReward()); assertFalse(p.takeReplayReward())
        p.restart(false); assertTrue(p.coins.isEmpty()); assertFalse(p.takeReplayReward())
    }
    @Test fun immediateRestartNeutralizesHeldInputsAndClearsTimeAndBumps() {
        val p=MazePractice(MazeLevels.layout(1))
        p.start(); p.steering(1900); p.drive(1900); repeat(100) { p.advance(20) }
        p.restart()
        assertEquals(MazePractice.State.RUNNING,p.state)
        assertEquals(0L,p.elapsedMillis); assertEquals(0,p.bumps)
        val initial=p.pose
        repeat(20) { p.advance(20) }
        assertEquals(initial,p.pose); assertEquals(400L,p.elapsedMillis)
    }
    private fun driveTo(p:MazePractice,end:Pair<Int,Int>) {
        p.pause(); p.start()
        val maze=p.maze; val start=p.pose.x.toInt() to p.pose.y.toInt()
        val previous=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>>()
        val seen=mutableSetOf(start); val queue=java.util.ArrayDeque<Pair<Int,Int>>().apply { add(start) }
        while(queue.isNotEmpty()) {
            val v=queue.removeFirst()
            for((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                val n=v.first+dx to v.second+dy
                if(!maze.wall(n.first,n.second) && seen.add(n)) { previous[n]=v; queue.add(n) }
            }
        }
        val path=mutableListOf(end)
        while(path.last()!=start) path.add(previous.getValue(path.last()))
        path.reverse()
        for(cell in path.drop(1)) {
            var steps=0
            while(hypot(cell.first+.5f-p.pose.x,cell.second+.5f-p.pose.y)>.12f && p.state!=MazePractice.State.CLEARED) {
                assertTrue(steps++<1500 && p.state==MazePractice.State.RUNNING)
                val error=atan2(cell.first+.5f-p.pose.x,-(cell.second+.5f-p.pose.y))-p.pose.heading
                val turn=atan2(sin(error),cos(error))
                p.steering((1500+turn.coerceIn(-1f,1f)*400).roundToInt())
                p.drive(if(abs(turn)<.15f)1740 else 1500); p.advance(20)
            }
        }
    }
}
