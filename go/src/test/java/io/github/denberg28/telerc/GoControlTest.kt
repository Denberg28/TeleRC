package io.github.denberg28.telerc
import org.junit.Test
import org.junit.Assert.*
class GoControlTest {
 @Test fun noArmingBeforeFreshSuccessfulNeutralFrames(){val g=GoControl();assertFalse(g.enable(0));g.heartbeat(false,1);assertTrue(g.enable(1));assertFalse(g.armReady(400));for(n in 1L..401L step 50)g.sent(1500,1500,n);assertTrue(g.armReady(401));g.sent(1700,1500,410);assertFalse(g.armReady(411))}
 @Test fun lossRecoveryAndStopCancelOldArm(){val g=GoControl();g.heartbeat(false,1);g.enable(1);for(n in 1L..401L step 50)g.sent(1500,1500,n);val epoch=g.epoch;assertTrue(g.maySendArm(epoch,400,410));g.stop();g.heartbeat(false,500);assertFalse(g.enabled);assertFalse(g.maySendArm(epoch,400,510));g.enable(510);g.heartbeat(false,2200);assertFalse(g.enabled);assertFalse(g.armReady(2200))}
 @Test fun stalePauseClockRollbackAndStopAreLocked(){val g=GoControl();g.heartbeat(false,1000);g.enable(1000);assertTrue(g.expire(2500));assertFalse(g.enabled);g.heartbeat(false,2600);g.receiverStop(true);assertFalse(g.enable(2600));g.receiverStop(false);assertTrue(g.enable(2600));assertFalse(g.fresh(100));g.disconnect();assertFalse(g.fresh(2700));g.heartbeat(false,2800);assertTrue(g.enable(2800))}
 @Test fun commandsExpireAndDoNotForceOrTouchOtherChannels(){val bytes=GoProtocol.rc(7,1,1500,1900);assertEquals(26,bytes.size);for(i in 10..21)assertEquals(255,bytes[i].toInt()and 255);assertEquals(1,bytes[22].toInt());assertEquals(1,bytes[23].toInt());val arm=Mavlink.armDisarm(8,1,1,true);for(i in 10..33)assertEquals(0,arm[i].toInt());val g=GoControl();g.heartbeat(false,1);g.enable(1);for(n in 1L..401L step 50)g.sent(1500,1500,n);assertFalse(g.maySendArm(g.epoch,1,501))}
 @Test fun holdIsNormalTargetedRoverModeCommand(){val f=GoProtocol.hold(9,42);assertEquals(1,Mavlink.frames(f).size);val p=f.copyOfRange(6,39);val b=java.nio.ByteBuffer.wrap(p).order(java.nio.ByteOrder.LITTLE_ENDIAN);assertEquals(1f,b.float,0f);assertEquals(4f,b.float,0f);for(i in 8..27)assertEquals(0,p[i].toInt());assertEquals(176,p[28].toInt()and 255);assertEquals(0,p[29].toInt());assertEquals(42,p[30].toInt());assertEquals(1,p[31].toInt())}
 @Test fun profileValidationKeepsMatchedFirmwareAndBackpackReadOnly(){assertTrue(GoEndpoint(GoProfile.WIFI).valid());assertFalse(GoEndpoint(GoProfile.WIFI,port=14551).valid());assertFalse(GoEndpoint(GoProfile.WIFI,system=2).valid());assertFalse(GoEndpoint(GoProfile.FC_WIFI,host="example.com").valid());assertFalse(GoEndpoint(GoProfile.FC_WIFI,host="224.0.0.1").valid());assertTrue(GoProfile.BACKPACK.handsetOnly)}
}
class GoElrsTest {
 @Test fun acceptsExactlyTenCharacterNamedValueField(){
  val p=ByteArray(18);p[4]=1;p[5]=1;"TELERC_OWN".toByteArray().copyInto(p,8)
  val f=byteArrayOf(254.toByte(),18,9,1,1,252.toByte())+p
  var crc=65535
  for(b in f.drop(1).map{it.toInt()and 255}+44){var t=(b xor(crc and 255));t=(t xor(t shl 4))and 255;crc=((crc ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
  val packet=f+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
  assertEquals(GoElrs.Status(1,true,false),GoElrs.status(packet,1));assertNull(GoElrs.status(packet,2));packet[14]=0;assertNull(GoElrs.status(packet,1))
 }
}

class GoInteropTest {
 @Test fun emitFramesForActualSketchDecoder(){
  val root=java.io.File("build/go-interop");root.mkdirs()
  val packets=mapOf("neutral" to GoProtocol.rc(1,1,1500,1500),"drive" to GoProtocol.rc(2,1,1700,1800),"release" to GoProtocol.rc(3,1,0,0),"arm" to Mavlink.armDisarm(4,1,1,true),"disarm" to Mavlink.armDisarm(5,1,1,false),"hold" to GoProtocol.hold(6,1))
  for((name,bytes) in packets)java.io.File(root,"$name.bin").writeBytes(bytes)
 }
}
