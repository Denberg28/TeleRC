package io.github.denberg28.telerc

data class WaypointFunction(val name:String,val output:Int,val off:Int=1100,val on:Int=1900) {
    val valid get()=name in WaypointFunctions.NAMES && output in 1..16 && output !in 5..8 &&
        off in 1000..2000 && on in 1000..2000 && off!=on
}
data class WaypointAction(val name:String,val on:Boolean) {
    fun label()="$name ${if(on) "ON" else "OFF"}"
}

/** Bounded actions tied to the exact coordinate draft; malformed saves lock execution. */
object WaypointFunctions {
    val NAMES=listOf("F1","F2","F3")
    fun encode(points:List<Waypoint>,actions:List<List<WaypointAction>>):String {
        require(points.size<=WaypointPlan.MAX_POINTS && points.all { it.valid } && actions.size==points.size && actions.all { row -> row.size<=3 && row.map { it.name }.distinct().size==row.size && row.all { it.name in NAMES } })
        return "1|${WaypointPlan.encode(points)}|"+actions.mapIndexed { index,row ->
            row.joinToString(";") { "$index,${it.name},${if(it.on) 1 else 0}" }
        }.filter { it.isNotEmpty() }.joinToString(";")
    }
    fun decode(raw:String,points:List<Waypoint>):List<List<WaypointAction>>? {
        if(raw.isEmpty()) return List(points.size) { emptyList() }
        if(raw.length>6000) return null
        val fields=raw.split('|')
        if(fields.size!=3 || fields[0]!="1" || fields[1]!=WaypointPlan.encode(points)) return null
        val rows=List(points.size) { mutableListOf<WaypointAction>() }
        if(fields[2].isNotEmpty()) for(token in fields[2].split(';')) {
            val parts=token.split(','); if(parts.size!=3) return null
            val index=parts[0].toIntOrNull() ?: return null
            if(index !in rows.indices || parts[1] !in NAMES || parts[2] !in listOf("0","1") || rows[index].any { it.name==parts[1] }) return null
            rows[index].add(WaypointAction(parts[1],parts[2]=="1"))
        }
        return rows.map { it.toList() }
    }
    fun problem(points:List<Waypoint>,actions:List<List<WaypointAction>>,bindings:List<WaypointFunction>):String? {
        if(actions.size!=points.size || actions.any { it.size>3 || it.map { a -> a.name }.distinct().size!=it.size || it.any { a -> a.name !in NAMES } }) return "Invalid function sequence"
        val used=actions.flatten().map { it.name }.toSet()
        if(bindings.size>3 || bindings.map { it.name }.distinct().size!=bindings.size || bindings.map { it.name }.toSet()!=used || bindings.any { !it.valid } ||
            bindings.map { it.output }.distinct().size!=bindings.size) return "Assign each used F1–F3 to a separate non-motor output with distinct ON/OFF PWM values"
        for(name in used) if(actions.flatten().last { it.name==name }.on) return "$name needs an OFF action at a later point"
        return null
    }
}

/** Serialized absolute PWM writes. ACK alone never proves the output changed. */
internal class WaypointAuxiliary(private val emit:(WaypointFunction,Int,Int,Boolean,Long)->Unit) {
    private var bindings=emptyList<WaypointFunction>()
    private var todo=emptyList<WaypointAction>()
    private var cursor=0
    private var sentAt=0L
    private var lastSent=0L
    private var attempts=0
    private var acknowledged=false
    private var observed=false
    private var cleanup=false
    private val errors=mutableListOf<String>()
    private var wireAt=-1L
    var generation=0L; private set
    var failure:String?=null; private set
    val busy get()=cursor<todo.size && failure==null
    val label get()=todo.getOrNull(cursor)?.label().orEmpty()
    fun begin(actions:List<WaypointAction>,functions:List<WaypointFunction>,now:Long,offOnly:Boolean=false) {
        bindings=functions.toList(); todo=actions.toList(); cursor=0; cleanup=offOnly; failure=null; errors.clear()
        beginNext(now)
    }
    fun cancel() { todo=emptyList(); cursor=0; failure=null; generation++; wireAt=-1 }
    fun transmitted(token:Long,now:Long) { if(token==generation && busy && wireAt<0) wireAt=now }
    private fun current()=bindings.single { it.name==todo[cursor].name }
    private fun pwm()=current().let { if(todo[cursor].on) it.on else it.off }
    private fun beginNext(now:Long) {
        if(!busy) { if(errors.isNotEmpty()) failure=errors.joinToString("; "); return }
        generation++; wireAt=-1
        acknowledged=false; observed=false; attempts=1; sentAt=now; lastSent=now
        emit(current(),pwm(),0,cleanup,generation)
    }
    fun ack(result:Int,now:Long) {
        if(!busy || wireAt<0 || now<wireAt) return
        if(result==0) acknowledged=true else if(result!=5) fail("$label rejected ($result)",now)
        advance(now)
    }
    fun output(output:Int,value:Int,now:Long) {
        if(!busy || wireAt<0 || now<=wireAt) return
        if(output==current().output) observed=value==pwm()
        advance(now)
    }
    private fun advance(now:Long) {
        if(busy && acknowledged && observed) { cursor++; beginNext(now) }
    }
    private fun fail(reason:String,now:Long) {
        if(cleanup) { errors.add(reason); cursor++; beginNext(now) } else failure=reason
    }
    fun tick(now:Long) {
        if(!busy) return
        if(now-sentAt>=4000) { fail("$label output unconfirmed",now); return }
        if(now-lastSent>=1000 && attempts<3) {
            emit(current(),pwm(),attempts++,cleanup,generation); lastSent=now
        }
    }
}
