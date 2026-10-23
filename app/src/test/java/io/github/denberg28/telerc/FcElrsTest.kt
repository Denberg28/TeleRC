package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class FcElrsTest {
    @Test fun nativeHandsetCannotCompeteWithPhone() {
        assertFalse(FcElrs.phoneCommandsAllowed(FcElrs.TRANSPORT))
        for (transport in listOf("wifi","lora_usb",MiniRoverWifi.TRANSPORT,ElrsControl.TRANSPORT,null))
            assertTrue(FcElrs.phoneCommandsAllowed(transport))
    }
    @Test fun backpackUsesDistinctPortsAndPinsPeer() {
        assertNotEquals(FcElrs.LOCAL_PORT,FcElrs.REMOTE_PORT)
        assertTrue(FcElrs.acceptsPeer(FcElrs.HOST,14555,FcElrs.HOST,14555))
        assertFalse(FcElrs.acceptsPeer(FcElrs.HOST,14555,FcElrs.HOST,14550))
        assertFalse(FcElrs.acceptsPeer(FcElrs.HOST,14555,"10.0.0.2",14555))
        assertFalse(FcElrs.acceptsPeer(FcElrs.HOST,14555,null,14555))
        assertTrue(FcElrs.acceptsPeer("192.168.1.25",15001,"192.168.1.25",15001))
    }
    @Test fun telemetrySessionRejectsCommandsRegardlessOfUiAuthority() {
        val sent=mutableListOf<ByteArray>()
        val transport=FcElrsTelemetryTransport(object:LinkTransport {
            override fun send(packet:java.net.DatagramPacket) { sent.add(packet.data.copyOfRange(packet.offset,packet.offset+packet.length)) }
            override fun receive(packet:java.net.DatagramPacket) {}
            override fun close() {}
        })
        fun packet(bytes:ByteArray)=java.net.DatagramPacket(bytes,bytes.size)
        for(bytes in listOf(Mavlink.override(1,1,1,1900,1500,1500,1500),Mavlink.release(2,1,1),
            Mavlink.armDisarm(3,1,1,true),WaypointProtocol.hold(4,1),
            "TELERC_DISCOVER_V1".toByteArray(),byteArrayOf(),ByteArray(17),WaypointProtocol.heartbeat(2))) {
            assertThrows(java.net.SocketException::class.java) { transport.send(packet(bytes)) }
        }
        assertTrue(sent.isEmpty())
        for(sequence in listOf(0,127,255)) transport.send(packet(FcElrs.registration(sequence)))
        assertEquals(3,sent.size)
        val withOffset=byteArrayOf(99)+FcElrs.registration(5)+byteArrayOf(98)
        transport.send(java.net.DatagramPacket(withOffset,1,22))
        assertEquals(4,sent.size)
        val corrupt=FcElrs.registration(4); corrupt[8]=5
        assertThrows(java.net.SocketException::class.java) { transport.send(packet(corrupt)) }
        assertEquals(4,sent.size)
    }
    @Test fun backpackRegistrationIsPingRatherThanGcsHeartbeat() {
        for(sequence in listOf(0,255,256,Int.MAX_VALUE,Int.MIN_VALUE)) {
            val p=FcElrs.registration(sequence)
            assertEquals(22,p.size); assertEquals(4,p[5].toInt())
            assertTrue(p.copyOfRange(6,14).all { it==0.toByte() })
            assertEquals(0,p[18].toInt()); assertEquals(0,p[19].toInt())
            assertTrue(FcElrs.isRegistration(p))
            assertNull(Mavlink.heartbeat(p))
        }
        assertFalse(FcElrs.isRegistration(WaypointProtocol.heartbeat(0)))
    }
    private fun autopilotHeartbeat(sequence:Int):ByteArray {
        val frame=byteArrayOf(0xfe.toByte(),9,sequence.toByte(),1,1,0,4,0,0,0,10,3,0,4,3)
        var crc=65535
        for (b in frame.drop(1).map { it.toInt() and 255 } + 50) {
            var t=(b xor (crc and 255)) and 255; t=(t xor (t shl 4)) and 255
            crc=((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
        }
        return frame+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
    }
    @Test fun batchedTelemetrySurvivesBeyondOldReceiveCapacity() {
        // 16 complete, checksum-valid autopilot heartbeats, with leading noise and a malformed tail.
        val heartbeats=(0 until 32).map { sequence ->
            autopilotHeartbeat(sequence)
        }
        val datagram=byteArrayOf(0x12,0x34)+heartbeats.fold(byteArrayOf()) { all,b -> all+b }+byteArrayOf(0xfd.toByte())
        assertTrue(datagram.size>512)
        assertTrue(datagram.size<FcElrs.MAX_DATAGRAM)
        val decoded=Mavlink.frames(datagram).mapNotNull(Mavlink::heartbeat)
        assertEquals(32,decoded.size)
        assertTrue(decoded.all { it.system==1 && !it.armed })
    }
}
