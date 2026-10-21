package io.github.denberg28.telerc

/** Versioned balancer-only Wi-Fi admin messages; never generic MAVLink motor commands. */
object BalanceSetup {
    const val PREFIX="TELERC_BALANCE_V1|"
    val query=(PREFIX+"Q").toByteArray(Charsets.US_ASCII)
    val stop=(PREFIX+"S").toByteArray(Charsets.US_ASCII)
    enum class Action(val code:Int) { CALIBRATE(1), LEFT(2), RIGHT(3), BOTH(4), VERIFY(5) }
    data class TestOutput(val valid:Boolean,val left:Int,val right:Int,val duties:List<Int>,val mask:Int,val pwmReady:Boolean)
    data class Status(val boot:Long,val challenge:Long,val sequence:Long,val request:Long,val result:String,
        val mode:Int,val calibrated:Boolean,val store:String,val verified:Boolean,val testDone:Boolean,
        val angle:Float,val rate:Float,val blocker:String,val calibration:String,val reason:String,
        val bias:Float,val testPwm:Int,val testMs:Int,val output:TestOutput?=null) {
        val disarmed get()=mode==0
        val calibrationFault get()=mode==4 && !calibrated && calibration!="CAL_NOT_STARTED"
    }
    /** Consent may follow fresh challenge refreshes, but never another boot/session. */
    data class Consent(val boot:Long,val session:Long,val action:Action) {
        fun allowed(status:Status?,currentSession:Long,receivedAt:Long,now:Long)=
            status?.boot==boot && currentSession==session && BalanceSetup.allowed(status,receivedAt,now,action)
    }
    fun parse(bytes:ByteArray):Status? {
        if(bytes.size !in 40..480 || bytes.any { (it.toInt() and 255) !in 32..126 })return null
        val p=String(bytes,Charsets.US_ASCII).split('|')
        if(p.size !in listOf(20,29) || p[0]!="TELERC_BALANCE_V1" || p[1]!="STATUS")return null
        fun number(i:Int)=p[i].takeIf { it.isNotEmpty() && it.all { c->c in '0'..'9' } }?.toLongOrNull()?.takeIf { it in 0..0xffffffffL }
        fun flag(i:Int)=when(p[i]){"0"->false;"1"->true;else->null}
        fun finite(i:Int)=p[i].toFloatOrNull()?.takeIf { it.isFinite() }
        fun token(i:Int)=p[i].takeIf { it.length in 1..63 }
        val output=if(p.size==29) {
            fun signed(i:Int)=p[i].toIntOrNull()?.takeIf { it in -160..160 }
            val duties=(23..26).map { i->number(i)?.takeIf { it<=255 }?.toInt()?:return null }
            val valid=flag(20)?:return null
            val mask=number(27)?.takeIf { it<=3 }?.toInt()?:return null
            if(valid && mask==0)return null
            TestOutput(valid,signed(21)?:return null,signed(22)?:return null,duties,mask,flag(28)?:return null)
        } else null
        return Status(number(2)?.takeIf{it>0}?:return null,number(3)?:return null,number(4)?:return null,number(5)?:return null,token(6)?:return null,
            p[7].toIntOrNull()?.takeIf { it in 0..4 }?:return null,flag(8)?:return null,token(9)?:return null,
            flag(10)?:return null,flag(11)?:return null,finite(12)?:return null,finite(13)?:return null,
            token(14)?:return null,token(15)?:return null,token(16)?:return null,finite(17)?:return null,
            p[18].toIntOrNull()?.takeIf { it in 1..160 }?:return null,
            p[19].toIntOrNull()?.takeIf { it in 100..500 }?:return null,output)
    }
    fun command(status:Status,id:Long,action:Action):ByteArray {
        require(status.challenge in 1..0xffffffffL && id in 1..0xffffffffL)
        return "$PREFIX${status.challenge}|$id|${action.code}".toByteArray(Charsets.US_ASCII)
    }
    fun newer(old:Status?,incoming:Status):Boolean {
        if(old==null || old.boot!=incoming.boot)return true
        return ((incoming.sequence-old.sequence) and 0xffffffffL) in 1..0x7fffffffL
    }
    /** The caller also binds the request to current transport, page and epoch. */
    fun allowed(status:Status?,receivedAt:Long,now:Long,action:Action):Boolean {
        val s=status?:return false
        if(now-receivedAt !in 0..1250 || s.challenge==0L || s.blocker=="CONTROLLER_STATUS_EXPIRED" || s.result=="RESTARTING_FOR_CALIBRATION")return false
        if(action==Action.CALIBRATE)return s.disarmed || s.calibrationFault
        if(!s.disarmed || !s.calibrated)return false
        if(s.output?.pwmReady==false || s.blocker in listOf("SENSOR_STALE","IMU_NOT_READY","PWM_NOT_READY","RELEASE_STOP_SWITCH","FAULT_RESET_REQUIRED"))return false
        return action!=Action.VERIFY || s.testDone && kotlin.math.abs(s.angle)<=4 && kotlin.math.abs(s.rate)<=10
    }
    fun responseText(result:String)=when(result) {
        "QUEUED"->"Request received; waiting for motor output."
        "TEST_RUNNING"->"PWM test running. Keep wheels raised."
        "TEST_FINISHED"->"Pulse finished. Confirm movement by looking at the wheels."
        "TEST_ABORTED_FAULT"->"Test stopped by a fault. Read the fault reason below."
        "STOPPED","STOP_REQUESTED"->"Motor stop requested. Check DISARMED status."
        "RESTARTING_FOR_CALIBRATION"->"Restarting for calibration. Hold upright and still; wait for calibration ready."
        else->result.replace('_',' ')
    }
}
