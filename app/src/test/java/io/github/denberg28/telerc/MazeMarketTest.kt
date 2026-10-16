package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MazeMarketTest {
    private fun funded():MazeWallet {
        var w=MazeWallet.decode("")
        for(level in 1..MazeLevels.COUNT) for(slot in 0 until MazeMarket.quota(level)) w=w.claim(level,slot)!!
        return w
    }
    @Test fun finiteSupplyCoversEntireCatalogAndEarlyUnlocks() {
        val w=funded()
        assertEquals(220,w.balance)
        assertEquals(180,MazeMarket.items.sumOf { it.price })
        assertEquals(listOf(8,10,12,14),listOf(1,6,11,16).map(MazeMarket::quota))
        val twoLevels=(1..2).sumOf(MazeMarket::quota)
        assertTrue(twoLevels>=MazeMarket.item("forest")!!.price)
        assertTrue(twoLevels<MazeMarket.item("rally")!!.price)
        assertTrue(twoLevels+MazeMarket.quota(3)>=MazeMarket.item("rally")!!.price)
        var bought=w
        for(item in MazeMarket.items.filter { it.price>0 }) {
            bought=bought.buy(item.id)!!.equip(item.id)!!
            assertTrue(bought.balance>=0)
            assertNull(bought.buy(item.id)) // Cannot double charge/rebuy.
        }
        assertEquals(40,bought.balance)
        assertEquals(MazeMarket.items.size,bought.owned.size)
        val restored=MazeWallet.decode(bought.encode())
        assertEquals(bought.encode(),restored.encode())
        assertEquals("hauler",restored.vehicle); assertEquals("glacier",restored.theme)
    }
    @Test fun transactionsRejectInvalidUnownedUnaffordableAndDuplicateClaims() {
        val empty=MazeWallet.decode("")
        assertEquals(0,empty.balance); assertNull(empty.buy("rally")); assertNull(empty.equip("rally"))
        assertNull(empty.buy("invented")); assertNull(empty.claim(0,0)); assertNull(empty.claim(21,0))
        assertNull(empty.claim(1,8)); assertNull(empty.claim(20,14))
        val one=empty.claim(1,0)!!
        assertEquals(1,one.balance); assertNull(one.claim(1,0))
        assertNull(MazeWallet.decode(one.encode()).claim(1,0))
        for(raw in listOf("bad","1|0,0,-1,9999|hauler,hacked|hauler|glacier","x".repeat(8193))) {
            val w=MazeWallet.decode(raw)
            assertTrue(w.balance>=0); assertEquals("scout",w.vehicle); assertEquals("navy",w.theme)
            assertFalse("hacked" in w.owned)
        }
    }
    @Test fun randomCoinsAreUniqueReachableFitAndExcludeStartAndEndForEveryLevel() {
        for(level in 1..MazeLevels.COUNT) {
            val maze=MazeLevels.layout(level)
            val slots=MazeMarket.available(level,MazeWallet.decode(""))
            val a=MazeCoins.spawn(maze,slots,17); val b=MazeCoins.spawn(maze,slots,901)
            assertEquals(MazeMarket.quota(level),a.size)
            assertEquals(a,MazeCoins.spawn(maze,slots,17)); assertNotEquals(a,b)
            assertEquals(a.size,a.map { it.x to it.y }.distinct().size)
            assertEquals(slots.toSet(),a.map { it.slot }.toSet())
            for(coin in a) {
                assertTrue(maze.fits(coin.x,coin.y))
                assertFalse(coin.x==maze.startX && coin.y==maze.startY)
                assertFalse(coin.x==maze.endX && coin.y==maze.endY)
                assertTrue(path(maze,1 to maze.height-2,coin.x.toInt() to coin.y.toInt()).isNotEmpty())
            }
        }
    }
    @Test fun drivingCollectsOncePausedCannotCollectAndResetCannotReissueRewards() {
        var wallet=MazeWallet.decode("")
        val p=MazePractice(MazeLevels.layout(1),MazeMarket.available(1,wallet),91)
        val initial=p.coins
        repeat(100) { p.advance(20) }
        assertEquals(initial,p.coins); assertTrue(p.drainCollectedCoins().isEmpty())
        p.start()
        val target=p.coins.first()
        for(cell in path(p.maze,p.pose.x.toInt() to p.pose.y.toInt(),target.x.toInt() to target.y.toInt()).drop(1)) {
            var steps=0
            while(hypot(cell.first+.5f-p.pose.x,cell.second+.5f-p.pose.y)>.12f) {
                assertTrue(steps++<1500 && p.state==MazePractice.State.RUNNING)
                val angle=atan2(cell.first+.5f-p.pose.x,-(cell.second+.5f-p.pose.y))-p.pose.heading
                val error=atan2(sin(angle),cos(angle))
                p.steering((1500+error.coerceIn(-1f,1f)*400).roundToInt()); p.drive(if(abs(error)<.15f)1740 else 1500)
                p.advance(20)
            }
        }
        val collected=p.drainCollectedCoins()
        assertTrue(target.slot in collected); assertEquals(collected.size,collected.distinct().size)
        for(slot in collected) wallet=wallet.claim(1,slot)!!
        assertTrue(p.drainCollectedCoins().isEmpty())
        p.pause(); val paused=p.coins; repeat(100) { p.advance(20) }; assertEquals(paused,p.coins)
        p.reset()
        assertTrue(p.coins.none { it.slot in collected }); assertEquals(initial.size-collected.size,p.coins.size)
        val afterRestart=MazePractice(p.maze,MazeMarket.available(1,MazeWallet.decode(wallet.encode())),92)
        assertTrue(afterRestart.coins.none { it.slot in collected })
        assertEquals(collected.size,wallet.balance)
    }
    private fun path(maze:MazeLayout,start:Pair<Int,Int>,end:Pair<Int,Int>):List<Pair<Int,Int>> {
        val prev=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>>()
        val seen=mutableSetOf(start); val q=java.util.ArrayDeque<Pair<Int,Int>>().apply { add(start) }
        while(q.isNotEmpty()) {
            val v=q.removeFirst()
            for((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                val n=v.first+dx to v.second+dy
                if(!maze.wall(n.first,n.second) && seen.add(n)) { prev[n]=v; q.add(n) }
            }
        }
        if(end !in seen) return emptyList()
        val out=mutableListOf(end)
        while(out.last()!=start) out.add(prev.getValue(out.last()))
        return out.reversed()
    }
}
