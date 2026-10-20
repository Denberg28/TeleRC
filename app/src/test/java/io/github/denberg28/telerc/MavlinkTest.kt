package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class MavlinkTest {
    @Test fun roverNeutralAndMapping() {
        assertEquals(RcChannels(1500, 1500, 1500, 1500), RoverControls.neutral)
        assertEquals(RcChannels(1700, 1200, 1500, 1500), RoverControls.channels(1700, 1200))
        assertFalse(CraftProfile.ROCKET.available)
    }
    @Test fun overrideBoundsAndFrame() {
        val frame = Mavlink.override(7, 1, 1, 1500, 1500, 1000, 1500)
        assertEquals(26, frame.size)
        assertEquals(0xFE, frame[0].toInt() and 255)
        assertEquals(70, frame[5].toInt())
        assertEquals(0xDC, frame[6].toInt() and 255)
        assertEquals(0x05, frame[7].toInt() and 255)
        // Independent MAVLink v1 reference vector: RC_CHANNELS_OVERRIDE CRC extra 124.
        assertEquals(0x65, frame[24].toInt() and 255)
        assertEquals(0x28, frame[25].toInt() and 255)
        assertThrows(IllegalArgumentException::class.java) { Mavlink.override(0, 1, 1, 999, 1500, 1000, 1500) }
    }
    @Test fun armDisarmCommandLongFrame() {
        val arm = Mavlink.armDisarm(9, 1, 1, true)
        assertEquals(41, arm.size)
        assertEquals(0xFE, arm[0].toInt() and 255)
        assertEquals(76, arm[5].toInt() and 255)
        assertEquals(0x80, arm[8].toInt() and 255)
        assertEquals(0x3F, arm[9].toInt() and 255)
        assertEquals(0x90, arm[34].toInt() and 255)
        assertEquals(0x01, arm[35].toInt() and 255)
        assertEquals(1, arm[36].toInt() and 255)
        assertEquals(1, arm[37].toInt() and 255)
        assertEquals(0, arm[38].toInt() and 255)
        val disarm = Mavlink.armDisarm(10, 1, 1, false)
        assertTrue((6..9).all { disarm[it].toInt() == 0 })
    }
    @Test fun heartbeatReportsArmedState() {
        fun heartbeat(armed: Boolean): ByteArray {
            val payload = ByteArray(9)
            payload[6] = if (armed) 0x80.toByte() else 0
            val header = byteArrayOf(0xFE.toByte(), 9, 0, 1, 1, 0)
            val content = header + payload
            var crc = 0xffff
            for (value in content.drop(1).map { it.toInt() and 255 } + 50) {
                var tmp = (value xor (crc and 255)) and 255
                tmp = (tmp xor (tmp shl 4)) and 255
                crc = ((crc ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 65535
            }
            return content + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
        }
        assertFalse(Mavlink.heartbeat(heartbeat(false))!!.armed)
        assertTrue(Mavlink.heartbeat(heartbeat(true))!!.armed)
    }
    @Test fun releaseClearsOverrides() {
        val frame = Mavlink.release(1, 1, 1)
        assertTrue((6..13).all { frame[it].toInt() == 0 })
    }
    @Test fun forgedHeartbeatIgnored() {
        val packet = byteArrayOf(0xFE.toByte(), 9, 0, 1, 1, 0) + ByteArray(9) + byteArrayOf(0, 0)
        assertNull(Mavlink.heartbeatSystem(packet))
    }
    @Test fun truncatedV2HeartbeatNeverCrashes() {
        val truncated = byteArrayOf(0xFD.toByte(), 9, 0, 0, 0, 1, 1, 0, 0, 0) + ByteArray(9)
        for (length in 0 until truncated.size) {
            assertNull(Mavlink.heartbeatSystem(truncated.copyOf(length)))
        }
        assertNull(Mavlink.heartbeatSystem(truncated))
    }
    @Test fun malformedHeartbeatIgnored() { assertNull(Mavlink.heartbeatSystem(byteArrayOf(0xFE.toByte(), 9))) }
    @Test fun heartbeatOnlyFromUnsignedAutopilotComponent() {
        fun heartbeat(component: Int, signed: Boolean): ByteArray {
            val header = byteArrayOf(0xFD.toByte(), 9, if (signed) 1 else 0, 0, 1, 1,
                component.toByte(), 0, 0, 0)
            val content = header + ByteArray(9)
            var crc = 0xffff
            for (value in content.drop(1).map { it.toInt() and 255 } + 50) {
                var tmp = (value xor (crc and 255)) and 255
                tmp = (tmp xor (tmp shl 4)) and 255
                crc = ((crc ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 65535
            }
            return content + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
        }
        assertEquals(1, Mavlink.heartbeatSystem(heartbeat(1, false)))
        assertNull(Mavlink.heartbeatSystem(heartbeat(190, false)))
        assertNull(Mavlink.heartbeatSystem(heartbeat(1, true)))
    }
    @Test fun globalPositionRequiresValidFrame() {
        val payload = ByteArray(28)
        fun put32(offset: Int, value: Int) { for (i in 0..3) payload[offset + i] = (value ushr (8 * i)).toByte() }
        put32(4, 145_995_000); put32(8, 1_209_842_000)
        payload[26] = 0x28; payload[27] = 0x23 // 90 degrees in centidegrees
        val header = byteArrayOf(0xFE.toByte(), 28, 1, 1, 1, 33)
        fun frame(): ByteArray {
            var crc = 0xffff
            fun accumulate(value: Int) {
                var tmp = (value xor (crc and 255)) and 255
                tmp = (tmp xor (tmp shl 4)) and 255
                crc = ((crc ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 65535
            }
            (header.drop(1) + payload.toList()).forEach { accumulate(it.toInt() and 255) }
            accumulate(104)
            return header + payload + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
        }
        val packet = frame()
        val position = Mavlink.globalPosition(packet)
        assertEquals(1, position?.system)
        assertEquals(14.5995, position!!.latitude, 0.000001)
        assertEquals(120.9842, position.longitude, 0.000001)
        assertEquals(90.0, position.headingDegrees!!, 0.000001)
        assertNull(Mavlink.globalPosition(packet.copyOf(packet.size - 1)))
        packet[10] = (packet[10].toInt() xor 1).toByte()
        assertNull(Mavlink.globalPosition(packet))
        payload[26] = 0xff.toByte(); payload[27] = 0xff.toByte()
        assertNull(Mavlink.globalPosition(frame())?.headingDegrees)
    }
    private fun incoming(id:Int,payload:ByteArray,v2:Boolean=true,component:Int=1,flags:Int=0):ByteArray {
        val header=if(v2) byteArrayOf(253.toByte(),payload.size.toByte(),flags.toByte(),0,1,1,component.toByte(),id.toByte(),0,0)
            else byteArrayOf(254.toByte(),payload.size.toByte(),1,1,component.toByte(),id.toByte())
        var crc=65535
        for(value in (header+payload).drop(1).map{it.toInt() and 255}+mapOf(0 to 50,77 to 143,33 to 104).getValue(id)) {
            var t=(value xor (crc and 255)) and 255;t=(t xor (t shl 4)) and 255
            crc=((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
        }
        return header+payload+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
    }
    @Test fun unsupportedFlagsAndOtherComponentsCannotSupplyTelemetry() {
        val ack=byteArrayOf(0x90.toByte(),1,0)
        val position=ByteArray(28).apply {this[4]=1}
        for(flags in listOf(1,2,4,128,255)) {
            assertNull(Mavlink.heartbeat(incoming(0,ByteArray(9),flags=flags)))
            assertNull(Mavlink.commandAck(incoming(77,ack,flags=flags)))
            assertNull(Mavlink.globalPosition(incoming(33,position,flags=flags)))
        }
        for(v2 in listOf(false,true)) {
            assertNotNull(Mavlink.commandAck(incoming(77,ack,v2)))
            assertNotNull(Mavlink.globalPosition(incoming(33,position,v2)))
            assertNull(Mavlink.commandAck(incoming(77,ack,v2,component=190)))
            assertNull(Mavlink.globalPosition(incoming(33,position,v2,component=190)))
            assertNull(Mavlink.heartbeat(incoming(0,ByteArray(9),v2)+byteArrayOf(0)))
            assertNull(Mavlink.commandAck(incoming(77,ack,v2)+byteArrayOf(0)))
            assertNull(Mavlink.globalPosition(incoming(33,position,v2)+byteArrayOf(0)))
        }
    }
    @Test fun v2TrailingZerosAreRestoredButInvalidLengthsAndForeignAckTargetsFail() {
        val ack=Mavlink.commandAck(incoming(77,byteArrayOf(0x90.toByte(),1)))!!
        assertEquals(400,ack.command);assertEquals(0,ack.result)
        val position=ByteArray(12).apply {this[4]=1}
        assertEquals(0.0,Mavlink.globalPosition(incoming(33,position))!!.headingDegrees!!,0.0)
        assertNull(Mavlink.globalPosition(incoming(33,position,v2=false)))
        assertNull(Mavlink.globalPosition(incoming(33,ByteArray(29))))
        assertNull(Mavlink.commandAck(incoming(77,ByteArray(11))))
        for(target in listOf(1,254)) {
            assertNull(Mavlink.commandAck(incoming(77,ByteArray(10).apply {this[8]=target.toByte()})))
            assertNull(Mavlink.commandAck(incoming(77,ByteArray(10).apply {this[9]=target.toByte()})))
        }
    }
}
