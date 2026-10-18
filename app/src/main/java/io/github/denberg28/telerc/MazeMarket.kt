package io.github.denberg28.telerc

import kotlin.random.Random

internal enum class MazeItemKind { VEHICLE, ACCESSORY }
internal enum class MazeVehicleClass(val label:String) { LAND("Land"), SEA("Sea"), AIR("Air"), SPACE("Space") }
internal data class MazeMarketItem(val id:String,val name:String,val kind:MazeItemKind,val price:Int,
    val color:Int,val vehicleClass:MazeVehicleClass=MazeVehicleClass.LAND)

/** Cosmetic catalog. Fixed lifetime supply and total paid catalog cost are both 220. */
internal object MazeMarket {
    val items=listOf(
        MazeMarketItem("scout","Scout",MazeItemKind.VEHICLE,0,0xff4384b5.toInt()),
        MazeMarketItem("rally","Rally",MazeItemKind.VEHICLE,20,0xffdf7536.toInt()),
        MazeMarketItem("buggy","Buggy",MazeItemKind.VEHICLE,35,0xff248f84.toInt()),
        MazeMarketItem("hauler","Hauler",MazeItemKind.VEHICLE,50,0xffa89955.toInt()),
        MazeMarketItem("skiff","Skiff",MazeItemKind.VEHICLE,18,0xff46bfc5.toInt(),MazeVehicleClass.SEA),
        MazeMarketItem("plane","Skywing",MazeItemKind.VEHICLE,22,0xffe7ad60.toInt(),MazeVehicleClass.AIR),
        MazeMarketItem("shuttle","Starship",MazeItemKind.VEHICLE,25,0xffa797e7.toInt(),MazeVehicleClass.SPACE),
        MazeMarketItem("hood","Hood",MazeItemKind.ACCESSORY,6,0xffefc577.toInt()),
        MazeMarketItem("armament","Armament",MazeItemKind.ACCESSORY,12,0xffb1c3d3.toInt()),
        MazeMarketItem("wheels","Wheels",MazeItemKind.ACCESSORY,10,0xff68d3b4.toInt()),
        MazeMarketItem("exhaust","Exhaust",MazeItemKind.ACCESSORY,8,0xffec9f6b.toInt()),
        MazeMarketItem("spoiler","Spoiler",MazeItemKind.ACCESSORY,14,0xffd6a4d9.toInt())
    )
    val freeIds=items.filter { it.price==0 }.map { it.id }.toSet()
    fun item(id:String)=items.firstOrNull { it.id==id }
    fun quota(level:Int)=if(level in 1..MazeLevels.COUNT) 8+((level-1)/5)*2 else 0
    fun key(level:Int,slot:Int):Int? = if(slot in 0 until quota(level)) (level-1)*14+slot else null
    fun validKey(key:Int)=key>=0 && MazeMarket.key(key/14+1,key%14)==key
    fun available(level:Int,wallet:MazeWallet)=(0 until quota(level)).filter { key(level,it) !in wallet.claimed }
}

/** Bounded v2 save. Removed v1 colors are refunded by retaining claims and omitting their cost. */
internal class MazeWallet private constructor(val claimed:Set<Int>,val owned:Set<String>,val vehicle:String,
    val accessories:Set<String>) {
    val balance:Int get()=claimed.size-MazeMarket.items.filter { it.id in owned }.sumOf { it.price }
    fun equipped(id:String)=id==vehicle || id in accessories
    fun claim(level:Int,slot:Int):MazeWallet? {
        val key=MazeMarket.key(level,slot) ?: return null
        if(key in claimed) return null
        return MazeWallet(claimed+key,owned,vehicle,accessories)
    }
    fun buy(id:String):MazeWallet? {
        val item=MazeMarket.item(id) ?: return null
        if(id in owned || item.price>balance) return null
        return MazeWallet(claimed,owned+id,vehicle,accessories)
    }
    fun equip(id:String):MazeWallet? {
        val item=MazeMarket.item(id) ?: return null
        if(id !in owned) return null
        return if(item.kind==MazeItemKind.VEHICLE) MazeWallet(claimed,owned,id,accessories)
            else MazeWallet(claimed,owned,vehicle,accessories+id)
    }
    fun remove(id:String):MazeWallet? = if(id in accessories) MazeWallet(claimed,owned,vehicle,accessories-id) else null
    fun encode()="2|${claimed.sorted().joinToString(",")}|${MazeMarket.items.filter { it.id in owned }.joinToString(",") { it.id }}|$vehicle|${accessories.sorted().joinToString(",")}"
    companion object {
        fun decode(raw:String):MazeWallet {
            val fields=if(raw.length<=8192) raw.split('|') else emptyList()
            if(fields.size!=5 || fields[0] !in listOf("1","2")) return MazeWallet(emptySet(),MazeMarket.freeIds,"scout",emptySet())
            val claims=fields[1].split(',').take(280).mapNotNull { it.toIntOrNull() }.filter(MazeMarket::validKey).toSet()
            val requested=fields[2].split(',').take(32).toSet()
            var budget=claims.size
            val owned=MazeMarket.freeIds.toMutableSet()
            for(item in MazeMarket.items.filter { it.price>0 }) if(item.id in requested && item.price<=budget &&
                (fields[0]=="2" || item.kind==MazeItemKind.VEHICLE && item.id in setOf("rally","buggy","hauler"))) {
                owned.add(item.id); budget-=item.price
            }
            val vehicle=fields[3].takeIf { it in owned && MazeMarket.item(it)?.kind==MazeItemKind.VEHICLE } ?: "scout"
            val accessories=if(fields[0]=="2") fields[4].split(',').take(32).filter {
                it in owned && MazeMarket.item(it)?.kind==MazeItemKind.ACCESSORY }.toSet() else emptySet()
            return MazeWallet(claims,owned,vehicle,accessories)
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

