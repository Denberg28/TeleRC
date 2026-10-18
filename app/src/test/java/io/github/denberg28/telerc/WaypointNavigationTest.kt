package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaypointNavigationTest {
    private val start=Waypoint(13.6218,123.1948)
    private val destination=Waypoint(13.6219,123.1948)
    private fun payload(n:Int,fill:ByteBuffer.()->Unit)=ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()
    private fun frame(id:Int,data:ByteArray,system:Int=1)=WaypointProtocol.Frame(id,system,1,data)
    private fun heartbeat(mode:Int=4,armed:Boolean=true)=frame(0,payload(9) {
        putInt(mode); put(10); put(3); put(if(armed) 129.toByte() else 1.toByte()); put(4); put(3)
    })
    private fun ack(command:Int,result:Int=0)=frame(77,payload(3) { putShort(command.toShort()); put(result.toByte()) })
    private fun position(p:Waypoint=start,time:Long=1000)=frame(33,payload(28) {
        putInt(time.toInt()); putInt((p.latitude*1e7).toInt()); putInt((p.longitude*1e7).toInt()); position(26); putShort(0)
    })
    private fun echo(p:Waypoint=destination)=frame(87,payload(51) {
        position(4); putInt((p.latitude*1e7).toInt()); putInt((p.longitude*1e7).toInt()); position(50); put(6)
    })
    private fun param(name:String,value:Float)=frame(22,payload(25) { putFloat(value); position(8); put(name.toByteArray()); position(24); put(9) })
    private fun telemetry(n:WaypointNavigation,time:Long,mode:Int=4,p:Waypoint=start,armed:Boolean=true,battery:Int=80) {
        n.receive(heartbeat(mode,armed),time,1)
        n.receive(position(p,time),time,1)
        n.receive(frame(24,payload(30) { putLong(time*1000); position(20); putShort(100); position(28); put(3); put(12) }),time,1)
        n.receive(frame(193,payload(22) { putFloat(.1f); putFloat(.1f); putFloat(.1f); putFloat(.1f); position(20); putShort(19) }),time,1)
        n.receive(frame(1,payload(31) { val mask=(1 shl 2) or (1 shl 5) or (1 shl 21); putInt(mask); putInt(mask); putInt(mask); position(30); put(battery.toByte()) }),time,1)
        n.receive(frame(242,payload(52) { putInt((start.latitude*1e7).toInt()); putInt((start.longitude*1e7).toInt()) }),time,1)
    }
    private fun ready(armed:Boolean=true,functions:List<WaypointFunction> = emptyList()):WaypointNavigation {
        val n=WaypointNavigation(); telemetry(n,1000,armed=armed); n.check(1,1000,functions); n.drain()
        for((key,value) in mapOf("SYSID_MYGCS" to 255f,"FS_GCS_ENABLE" to 1f,"FS_ACTION" to 2f,
            "FS_EKF_ACTION" to 1f,"FS_GCS_TIMEOUT" to 2f,"FS_TIMEOUT" to 1f,"WP_SPEED" to .5f)) n.receive(param(key,value),1000,1)
        for(f in functions) for((suffix,value) in mapOf("FUNCTION" to 0f,"MIN" to 1000f,"MAX" to 2000f))
            n.receive(param("SERVO${f.output}_$suffix",value),1000,1)
        n.receive(ack(176),1000,1); n.receive(heartbeat(armed=armed),1100,1); n.tick(1100,true,true); n.drain()
        assertEquals(WaypointNavigation.State.READY,n.state); return n
    }
    private fun running(points:List<Waypoint> = listOf(destination)):WaypointNavigation {
        val n=ready(); assertNull(n.start(points,1200)); n.drain()
        n.receive(ack(192),1300,1); n.receive(heartbeat(15),1300,1); n.receive(echo(),1300,1)
        n.tick(1300,true,true); n.drain(); assertEquals(WaypointNavigation.State.RUNNING,n.state); return n
    }
    @Test fun draftIsBoundedAndNeverRestoresAuthority() {
        assertEquals(listOf(start,destination),WaypointPlan.decode(WaypointPlan.encode(listOf(start,destination))))
        assertTrue(WaypointPlan.decode("a".repeat(1601)).isEmpty())
        assertTrue(WaypointPlan.decode("NaN,0;91,1;0,0;1,Infinity").isEmpty())
        assertEquals(20,WaypointPlan.decode((1..40).joinToString(";") { "13.0,123.0" }).size)
        assertNotNull(WaypointPlan.problem(listOf(Waypoint(14.0,123.0)),start))
        assertNotNull(WaypointPlan.problem(emptyList(),start))
        assertNotNull(WaypointPlan.problem(listOf(start),null))
    }
    @Test fun cannotNavigateWithoutChecksArmOrWithinWrongRadius() {
        val n=WaypointNavigation(); assertNotNull(n.start(listOf(destination),0)); assertTrue(n.drain().isEmpty())
        assertNotNull(ready(false).start(listOf(destination),1200))
        val checked=ready(); assertNotNull(checked.start(listOf(Waypoint(14.0,123.0)),1200))
        checked.receive(param("FS_ACTION",0f),1200,1)
        assertNotNull(checked.start(listOf(destination),1200))
        assertFalse(checked.drain().any { it.motion })
    }
    @Test fun checkNeedsBothHoldAckAndHeartbeatAndTimesOutOnFilteredBridge() {
        val n=WaypointNavigation(); telemetry(n,1000); n.check(1,1000); n.drain()
        n.tick(10000,true,true)
        assertEquals(WaypointNavigation.State.ERROR,n.state)
        assertTrue(n.message.contains("gateway")); assertFalse(n.drain().any { it.motion })
    }
    @Test fun ackAloneDoesNotProveDestinationOrMovement() {
        val n=ready(); assertNull(n.start(listOf(destination),1200)); n.drain()
        n.receive(ack(192),1300,1); n.tick(1300,true,true)
        assertEquals(WaypointNavigation.State.STARTING,n.state)
        telemetry(n,2500); n.tick(4300,true,true)
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
    }
    @Test fun stopInvalidatesQueuedMotionAndRequiresHoldConfirmation() {
        val n=ready(); n.start(listOf(destination),1200); val queued=n.drain().single { it.motion }
        assertTrue(n.permits(queued,1200)); n.stop("Operator",1250)
        assertFalse(n.permits(queued,1200)); assertEquals(WaypointNavigation.State.HOLDING,n.state)
        assertFalse(n.drain().any { it.motion })
        n.receive(ack(176),1300,1); assertEquals(WaypointNavigation.State.HOLDING,n.state)
        n.receive(heartbeat(),1400,1); assertEquals(WaypointNavigation.State.STOPPED,n.state)
        assertNotNull(n.start(listOf(destination),1400))
    }
    @Test fun confirmedHoldKeepsManualGcsLivenessButUnconfirmedOrBackgroundDoesNot() {
        val n=running(); n.stop("Leave",1400); n.drain()
        n.tick(2400,true,true,false)
        assertFalse(n.drain().any { it.bytes[5]==0.toByte() })
        n.receive(ack(176),2450,1); n.receive(heartbeat(),2500,1)
        n.tick(2600,true,true,false)
        assertTrue(n.gcsSessionActive)
        assertTrue(n.drain().any { it.bytes[5]==0.toByte() })
        assertFalse(n.moving)
        n.receive(heartbeat(0),2800,1); n.tick(3700,true,true,false)
        val beacon=n.drain().single { it.gcsHeartbeat }
        assertFalse(beacon.motion); assertTrue(n.permits(beacon,3700))
        n.tick(4800,true,false,false)
        assertFalse(n.drain().any { it.bytes[5]==0.toByte() })
        assertFalse(n.permits(beacon,4800))
        n.disconnected(); assertFalse(n.gcsSessionActive)
    }
    @Test fun finalDispatchRechecksReadinessAndCancelsAtomically() {
        val n=ready(); n.start(listOf(destination),1200); val queued=n.drain().single { it.motion }
        n.receive(param("FS_ACTION",0f),1250,1)
        var sends=0
        assertFalse(n.transmit(queued,1250) { sends++ }); assertEquals(0,sends)
        val stale=ready(); stale.start(listOf(destination),1200); val old=stale.drain().single { it.motion }
        assertFalse(stale.transmit(old,3501) { sends++ }); assertEquals(0,sends)
        n.stop("Operator",1300)
        assertFalse(n.transmit(queued,1300) { sends++ }); assertEquals(0,sends)
    }
    @Test fun heartbeatRecoveryDoesNotResumeAndNoHeartbeatContinuesAfterStop() {
        val n=running(); n.receive(heartbeat(15),3501,1)
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
        assertFalse(n.drain().any { it.motion || it.bytes[5]==0.toByte() })
        telemetry(n,3600,15); n.tick(3600,true,true)
        assertFalse(n.moving)
    }
    @Test fun gpsRecoveryAfterStaleGapStopsBeforeItCanRefreshReadiness() {
        val n=running(); n.receive(heartbeat(15),2500,1); n.receive(position(),3501,1)
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
    }
    @Test fun repeatedOrReorderedRemotePositionDoesNotRefreshOrAdvance() {
        val n=running()
        // A replay carries the original FC timestamp even if it arrives later.
        n.receive(position(start,1000),2000,1)
        n.receive(heartbeat(15),2500,1)
        n.receive(position(start,1000),3101,1)
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
        val reset=running(); reset.receive(position(start,999),1400,1)
        assertEquals(WaypointNavigation.State.HOLDING,reset.state)
    }
    @Test fun lowBatteryGpsJumpPilotModeAndBackgroundRequestHold() {
        val battery=running(); telemetry(battery,1400,15,battery=19); battery.tick(1400,true,true)
        assertEquals(WaypointNavigation.State.HOLDING,battery.state)
        val jump=running(); jump.receive(position(Waypoint(13.6228,123.1948),1400),1400,1)
        assertEquals(WaypointNavigation.State.HOLDING,jump.state)
        val pilot=running(); pilot.receive(heartbeat(0),1400,1)
        assertEquals(WaypointNavigation.State.HOLDING,pilot.state)
        val background=running(); background.tick(1400,true,false)
        assertEquals(WaypointNavigation.State.HOLDING,background.state)
    }
    @Test fun disconnectInvalidatesOldSessionAndNeverResumes() {
        val n=running(); n.disconnected(); telemetry(n,1500,15); n.tick(1500,true,true)
        assertFalse(n.moving); assertEquals(WaypointNavigation.State.ERROR,n.state)
        assertFalse(n.drain().any { it.motion })
    }
    @Test fun wrongSourceBadSettingUnknownBatteryAndTargetMismatchBlock() {
        val n=ready(); n.receive(frame(22,param("FS_ACTION",0f).data,2),1200,1)
        assertNull(n.blocker(1200))
        n.receive(frame(1,payload(31) { position(30); put((-1).toByte()) }),1200,1)
        assertNotNull(n.blocker(1200))
        val active=running(); active.receive(echo(start),1400,1)
        assertEquals(WaypointNavigation.State.HOLDING,active.state)
    }
    @Test fun arrivalNeedsThreeFreshSamplesThenHoldCompletion() {
        val n=running()
        for(t in listOf(1800L,2300L,2800L,3300L)) {
            telemetry(n,t,15,destination); n.receive(echo(),t,1); n.tick(t,true,true)
        }
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
        n.receive(ack(176),3400,1); n.receive(heartbeat(),3500,1)
        assertEquals(WaypointNavigation.State.COMPLETE,n.state); assertFalse(n.moving)
    }
    @Test fun multiPointProgressSendsOnlyNextConfirmedDestination() {
        val second=Waypoint(13.6220,123.1948); val n=running(listOf(destination,second))
        for(t in listOf(1800L,2300L,2800L,3300L)) {
            telemetry(n,t,15,destination); n.receive(echo(),t,1); n.tick(t,true,true)
        }
        assertEquals(1,n.index); assertEquals(WaypointNavigation.State.STARTING,n.state)
        assertEquals(1,n.drain().count { it.motion })
        n.receive(ack(192,2),3400,1)
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
    }
    private val function=WaypointFunction("F1",1)
    private val withActions=listOf(listOf(WaypointAction("F1",true)),listOf(WaypointAction("F1",false)))
    private fun output(pwm:Int,time:Long)=frame(36,payload(21) { putInt((time*1000).toInt()); putShort(pwm.toShort()); position(20); put(0) })
    private fun send(n:WaypointNavigation,time:Long)=n.drain().filter { n.transmit(it,time) {} }
    private fun initialized():WaypointNavigation {
        val n=ready(functions=listOf(function))
        assertNull(n.start(listOf(destination,Waypoint(13.6220,123.1948)),1200,withActions,listOf(function)))
        assertEquals(WaypointNavigation.State.ACTION_HOLD,n.state)
        assertFalse(n.drain().any { it.function!=null || it.motion })
        n.receive(ack(176),1250,1)
        telemetry(n,1300); n.tick(1300,true,true)
        assertEquals(WaypointNavigation.State.ACTION_HOLD,n.state)
        telemetry(n,1700); n.tick(1700,true,true)
        assertEquals(WaypointNavigation.State.ACTIONS,n.state)
        assertEquals(1100,send(n,1700).single { it.function!=null }.pwm)
        n.receive(ack(183),1710,1); n.receive(output(1100,1720),1720,1); n.tick(1720,true,true)
        assertEquals(WaypointNavigation.State.STARTING,n.state)
        send(n,1720)
        n.receive(ack(192),1800,1); telemetry(n,1800,15); n.receive(echo(),1800,1); n.tick(1800,true,true)
        assertEquals(WaypointNavigation.State.RUNNING,n.state)
        return n
    }
    private fun atAction():WaypointNavigation {
        val n=initialized()
        for(t in listOf(2300L,2800L,3300L,3800L)) {
            telemetry(n,t,15,destination); n.receive(echo(),t,1); n.tick(t,true,true)
        }
        assertEquals(WaypointNavigation.State.ACTION_HOLD,n.state)
        assertFalse(n.drain().any { it.function!=null })
        n.receive(ack(176),3850,1)
        telemetry(n,4000,4,destination); n.tick(4000,true,true)
        telemetry(n,4400,4,destination); n.tick(4400,true,true)
        assertEquals(WaypointNavigation.State.ACTIONS,n.state)
        return n
    }
    @Test fun functionsRequireStationaryHoldAndBothAckAndOutputBeforeNextDestination() {
        val n=atAction()
        val on=send(n,4400).single { it.function!=null }
        assertEquals(1900,on.pwm)
        n.receive(ack(183),4410,1); n.tick(4410,true,true)
        assertEquals(WaypointNavigation.State.ACTIONS,n.state)
        n.receive(output(1900,4420),4420,1); n.tick(4420,true,true)
        assertEquals(WaypointNavigation.State.STARTING,n.state); assertEquals(1,n.index)
        val destination=n.drain().single { it.motion }
        n.stop("Operator",4450)
        assertFalse(n.permits(on,4451)); assertFalse(n.permits(destination,4451))
        val off=send(n,4450).single { it.function!=null }
        assertEquals(1100,off.pwm); assertTrue(off.cleanup); assertFalse(off.motion)
        n.receive(ack(176),4460,1); n.receive(heartbeat(),4465,1)
        assertEquals(WaypointNavigation.State.HOLDING,n.state) // HOLD alone is insufficient.
        n.receive(ack(183),4470,1); n.receive(output(1100,4480),4480,1)
        assertEquals(WaypointNavigation.State.STOPPED,n.state); assertFalse(n.functionsPendingOff)
    }
    @Test fun functionOutputAndOldSessionRepliesCannotAutoResumeAfterDisconnect() {
        val n=atAction(); val on=send(n,4400).single { it.function!=null }
        n.disconnected(); assertFalse(n.permits(on,4410)); assertTrue(n.functionsPendingOff)
        n.receive(heartbeat(),4500,1); n.receive(ack(183),4501,1); n.receive(output(1900,4502),4502,1)
        n.tick(4502,true,true); assertTrue(n.drain().isEmpty())
        n.check(1,4503); assertEquals(WaypointNavigation.State.ERROR,n.state)
        assertTrue(n.message.contains("OFF unconfirmed"))
        n.stop("Retry OFF",4600)
        for((suffix,value) in mapOf("FUNCTION" to 0f,"MIN" to 1000f,"MAX" to 2000f)) n.receive(param("SERVO1_$suffix",value),4601,1)
        val off=send(n,4602).single { it.function!=null }
        assertFalse(off.motion); assertEquals(1100,off.pwm)
        n.receive(ack(176),4610,1); n.receive(heartbeat(),4620,1)
        n.receive(ack(183),4630,1); n.receive(output(1100,4640),4640,1)
        assertEquals(WaypointNavigation.State.STOPPED,n.state)
        assertFalse(n.functionsPendingOff); assertFalse(n.drain().any { it.motion })
    }
    @Test fun motorFunctionOrMissingOffCannotStartAndExpiredServoWritesAreRejected() {
        val n=ready(functions=listOf(function))
        n.receive(param("SERVO1_FUNCTION",73f),1150,1)
        assertNotNull(n.start(listOf(destination,Waypoint(13.6220,123.1948)),1200,withActions,listOf(function)))
        assertTrue(n.drain().isEmpty())
        val active=atAction(); val on=active.drain().single { it.function!=null }
        assertFalse(active.permits(on,5001))
        active.stop("STOP",5002); assertFalse(active.permits(on,5003))
    }
    @Test fun unconfirmedHoldStopsRetriesAndNeverSaysStopped() {
        val n=running(); n.stop("Operator",1400); n.drain()
        for(t in listOf(2400L,3400L,4400L)) { n.tick(t,true,true); assertFalse(n.drain().any { it.motion }) }
        assertEquals(WaypointNavigation.State.ERROR,n.state); assertTrue(n.message.contains("unconfirmed"))
    }
    @Test fun missingHoldReplyDoesNotCancelBoundedOffCleanup() {
        val n=atAction(); send(n,4400)
        n.stop("Operator",4450); send(n,4450)
        for(t in listOf(5450L,6450L,7450L)) { n.tick(t,true,true); send(n,t) }
        assertEquals(WaypointNavigation.State.HOLDING,n.state)
        assertTrue(n.functionsPendingOff)
        n.tick(8450,true,true)
        assertEquals(WaypointNavigation.State.ERROR,n.state)
        assertTrue(n.message.contains("OFF unconfirmed"))
    }
    @Test fun protocolDestinationHasBoundedSpeedAtomicModeFlagAndNoArming() {
        val packet=WaypointProtocol.destination(1,1,destination)
        assertEquals(75,packet[5].toInt()); val b=ByteBuffer.wrap(packet,6,35).slice().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(.5f,b.getFloat(0),0f); assertEquals(1f,b.getFloat(4),0f)
        assertEquals(192,b.getShort(28).toInt()); assertEquals(6,b.get(32).toInt())
        assertEquals((destination.latitude*1e7).toInt(),b.getInt(16))
        assertEquals(176,ByteBuffer.wrap(WaypointProtocol.hold(2,1)).order(ByteOrder.LITTLE_ENDIAN).getShort(34).toInt())
    }
    @Test fun navigationParserRejectsCorruptionOtherComponentsAndUnsignedFlags() {
        // Independently serialized HEARTBEAT fixture from an autopilot; checksum calculated below.
        val packet=byteArrayOf(254.toByte(),9,1,1,1,0)+heartbeat().data
        fun checksum(bytes:ByteArray):ByteArray {
            var crc=0xffff
            for(v in bytes.drop(1).map { it.toInt() and 255 } + 50) {
                var t=v xor (crc and 255); t=t xor ((t shl 4) and 255)
                crc=((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
            }
            return bytes+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
        }
        val valid=checksum(packet); assertNotNull(WaypointProtocol.decode(valid))
        assertNull(WaypointProtocol.decode(valid.copyOf().apply { this[8]=(this[8].toInt() xor 1).toByte() }))
        assertNull(WaypointProtocol.decode(checksum(packet.copyOf().apply { this[4]=2 })))
        assertNull(WaypointProtocol.decode(valid.dropLast(1).toByteArray()))
        assertNull(WaypointProtocol.decode(valid+0))
        val v2=byteArrayOf(253.toByte(),9,2,0,1,1,1,0,0,0)+heartbeat().data
        assertNull(WaypointProtocol.decode(checksum(v2)))
    }
}
