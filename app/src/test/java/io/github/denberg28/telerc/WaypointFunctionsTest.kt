package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaypointFunctionsTest {
    private val points=listOf(Waypoint(13.6218,123.1948),Waypoint(13.6219,123.1948))
    private val f=WaypointFunction("F1",1)
    private val actions=listOf(listOf(WaypointAction("F1",true)),listOf(WaypointAction("F1",false)))
    @Test fun actionsRoundTripAreCoordinateBoundedAndNeverRestoreExecution() {
        val encoded=WaypointFunctions.encode(points,actions)
        assertEquals(actions,WaypointFunctions.decode(encoded,points))
        assertNull(WaypointFunctions.decode(encoded,points.reversed()))
        assertNull(WaypointFunctions.decode("x".repeat(6001),points))
        for(bad in listOf("0,F1,2","2,F1,0","0,F4,1","0,F1,1;0,F1,0","bad"))
            assertNull(WaypointFunctions.decode("1|${WaypointPlan.encode(points)}|$bad",points))
        assertEquals(List(2) { emptyList<WaypointAction>() },WaypointFunctions.decode("",points))
    }
    @Test fun onlyDedicatedUniqueNonMotorBindingsAndBalancedOnOffPlansPass() {
        assertNull(WaypointFunctions.problem(points,actions,listOf(f)))
        assertNotNull(WaypointFunctions.problem(points,actions.take(1),listOf(f)))
        assertNotNull(WaypointFunctions.problem(points,listOf(emptyList(),listOf(WaypointAction("F1",true))),listOf(f)))
        for(output in listOf(0,5,6,7,8,17)) assertFalse(f.copy(output=output).valid)
        assertFalse(f.copy(off=1900).valid); assertFalse(f.copy(on=2001).valid)
        val duplicate=listOf(listOf(WaypointAction("F1",false),WaypointAction("F2",false)),emptyList())
        assertNotNull(WaypointFunctions.problem(points,duplicate,listOf(f,f.copy(name="F2"))))
    }
    @Test fun servoCodecHasAbsolutePwmCorrectTargetAndBoundedRetryConfirmation() {
        val packet=WaypointProtocol.servo(7,4,2,1900,2)
        val b=ByteBuffer.wrap(packet,6,33).slice().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2f,b.getFloat(0),0f); assertEquals(1900f,b.getFloat(4),0f)
        assertEquals(183,b.getShort(28).toInt()); assertEquals(4,b.get(30).toInt())
        assertEquals(1,b.get(31).toInt()); assertEquals(2,b.get(32).toInt())
        assertThrows(IllegalArgumentException::class.java) { WaypointProtocol.servo(0,1,5,1900) }
        assertThrows(IllegalArgumentException::class.java) { WaypointProtocol.servo(0,1,1,999) }
        val raw=WaypointProtocol.frame(36,1,ByteArray(21))
        assertEquals(21,WaypointProtocol.decode(raw)!!.data.size)
        raw[10]=(raw[10].toInt() xor 1).toByte(); assertNull(WaypointProtocol.decode(raw))
    }
    @Test fun ackAndOutputBeforeActualSendCannotCompleteAndRetryIsBounded() {
        data class Sent(val pwm:Int,val retry:Int,val cleanup:Boolean,val token:Long)
        val sent=mutableListOf<Sent>()
        val aux=WaypointAuxiliary { _,pwm,retry,cleanup,token -> sent.add(Sent(pwm,retry,cleanup,token)) }
        aux.begin(listOf(WaypointAction("F1",true)),listOf(f),1000)
        aux.ack(0,1100); aux.output(1,1900,1100); assertTrue(aux.busy)
        aux.transmitted(sent.last().token,1200)
        aux.ack(0,1201); assertTrue(aux.busy)
        aux.output(1,1900,1200); assertTrue(aux.busy) // Same timestamp is not post-send.
        aux.output(1,1100,1250); assertTrue(aux.busy)
        aux.output(1,1900,1300); assertFalse(aux.busy); assertNull(aux.failure)
        aux.begin(listOf(WaypointAction("F1",true)),listOf(f),2000)
        aux.tick(3000); aux.tick(4000); aux.tick(5000); aux.tick(6000)
        assertEquals(listOf(0,0,1,2),sent.map { it.retry }); assertNotNull(aux.failure)
    }
    @Test fun offCleanupCancelsOnAndStillAttemptsOtherOutputsAfterFailure() {
        val sent=mutableListOf<Pair<Int,Long>>()
        val aux=WaypointAuxiliary { binding,_,_,_,token -> sent.add(binding.output to token) }
        val second=WaypointFunction("F2",2)
        aux.begin(listOf(WaypointAction("F1",true)),listOf(f,second),1000)
        val old=sent.last().second
        aux.cancel()
        aux.begin(listOf(WaypointAction("F1",false),WaypointAction("F2",false)),listOf(f,second),1100,true)
        assertNotEquals(old,sent.last().second)
        aux.transmitted(old,1101); aux.ack(0,1102); aux.output(1,1100,1103); assertTrue(aux.busy)
        aux.transmitted(sent.last().second,1200); aux.ack(2,1201)
        assertEquals(2,sent.last().first) // Failure of F1 does not skip the F2 OFF request.
        aux.transmitted(sent.last().second,1300); aux.ack(0,1301); aux.output(2,1100,1302)
        assertFalse(aux.busy); assertNotNull(aux.failure)
    }
}
