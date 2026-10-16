package io.github.denberg28.telerc

import kotlin.random.Random

internal enum class MazeItemKind { VEHICLE, COLOR }
internal data class MazeMarketItem(val id:String,val name:String,val kind:MazeItemKind,val price:Int,
    val color:Int,val floor:Int=0,val wall:Int=0)

/** Cosmetic-only, fixed prices. 220 earnable coins, 180 total paid catalog cost. */
internal object MazeMarket {
    val items=listOf(
        MazeMarketItem("scout","Scout",MazeItemKind.VEHICLE,0,0xff244366.toInt()),
        MazeMarketItem("rally","Rally",MazeItemKind.VEHICLE,20,0xffdf7536.toInt()),
        MazeMarketItem("buggy","Buggy",MazeItemKind.VEHICLE,35,0xff248f84.toInt()),
        MazeMarketItem("hauler","Hauler",MazeItemKind.VEHICLE,50,0xff82743b.toInt()),
        MazeMarketItem("navy","Navy",MazeItemKind.COLOR,0,0xff809bbb.toInt()),
        MazeMarketItem("forest","Forest",MazeItemKind.COLOR,15,0xffa5d6ae.toInt(),0xff203b30.toInt(),0xffa5d6ae.toInt()),
        MazeMarketItem("ember","Ember",MazeItemKind.COLOR,25,0xffedb695.toInt(),0xff432e2a.toInt(),0xffedb695.toInt()),
        MazeMarketItem("glacier","Glacier",MazeItemKind.COLOR,35,0xfface3ed.toInt(),0xff203a49.toInt(),0xfface3ed.toInt())
    )
    val freeIds=items.filter { it.price==0 }.map { it.id }.toSet()
    fun item(id:String)=items.firstOrNull { it.id==id }
    fun quota(level:Int)=if(level in 1..MazeLevels.COUNT) 8+((level-1)/5)*2 else 0
    fun key(level:Int,slot:Int):Int? = if(slot in 0 until quota(level)) (level-1)*14+slot else null
    fun validKey(key:Int)=key>=0 && MazeMarket.key(key/14+1,key%14)==key
    fun available(level:Int,wallet:MazeWallet)=(0 until quota(level)).filter { key(level,it) !in wallet.claimed }
}

/** A single bounded save: balance is derived from unique claims minus paid ownership. */
internal class MazeWallet private constructor(val claimed:Set<Int>,val owned:Set<String>,val vehicle:String,val theme:String) {
    val balance:Int get()=claimed.size-MazeMarket.items.filter { it.id in owned }.sumOf { it.price }
    fun claim(level:Int,slot:Int):MazeWallet? {
        val key=MazeMarket.key(level,slot) ?: return null
        if(key in claimed) return null
        return MazeWallet(claimed+key,owned,vehicle,theme)
    }
    fun buy(id:String):MazeWallet? {
        val item=MazeMarket.item(id) ?: return null
        if(id in owned || item.price>balance) return null
        return MazeWallet(claimed,owned+id,vehicle,theme)
    }
    fun equip(id:String):MazeWallet? {
        val item=MazeMarket.item(id) ?: return null
        if(id !in owned) return null
        return MazeWallet(claimed,owned,if(item.kind==MazeItemKind.VEHICLE) id else vehicle,
            if(item.kind==MazeItemKind.COLOR) id else theme)
    }
    fun encode()="1|${claimed.sorted().joinToString(",")}|${MazeMarket.items.filter { it.id in owned }.joinToString(",") { it.id }}|$vehicle|$theme"
    companion object {
        fun decode(raw:String):MazeWallet {
            val fields=if(raw.length<=8192) raw.split('|') else emptyList()
            if(fields.size!=5 || fields[0]!="1") return MazeWallet(emptySet(),MazeMarket.freeIds,"scout","navy")
            val claims=fields[1].split(',').take(280).mapNotNull { it.toIntOrNull() }.filter(MazeMarket::validKey).toSet()
            val requested=fields[2].split(',').take(MazeMarket.items.size).toSet()
            var budget=claims.size
            val owned=MazeMarket.freeIds.toMutableSet()
            for(item in MazeMarket.items.filter { it.price>0 }) if(item.id in requested && item.price<=budget) {
                owned.add(item.id); budget-=item.price
            }
            val vehicle=fields[3].takeIf { it in owned && MazeMarket.item(it)?.kind==MazeItemKind.VEHICLE } ?: "scout"
            val theme=fields[4].takeIf { it in owned && MazeMarket.item(it)?.kind==MazeItemKind.COLOR } ?: "navy"
            return MazeWallet(claims,owned,vehicle,theme)
        }
    }
}

internal data class MazeCoin(val slot:Int,val x:Float,val y:Float)
internal object MazeCoins {
    fun spawn(maze:MazeLayout,slots:List<Int>,seed:Int):List<MazeCoin> {
        val cells=buildList {
            for(y in 1 until maze.height-1 step 2) for(x in 1 until maze.width-1 step 2) {
                val px=x+.5f; val py=y+.5f
                if(!maze.wall(x,y) && maze.fits(px,py) && !(px==maze.startX && py==maze.startY) &&
                    !(px==maze.endX && py==maze.endY)) add(px to py)
            }
        }.shuffled(Random(seed))
        return slots.distinct().filter { MazeMarket.key(maze.level,it)!=null }.sorted().take(cells.size)
            .mapIndexed { index,slot -> MazeCoin(slot,cells[index].first,cells[index].second) }
    }
}
