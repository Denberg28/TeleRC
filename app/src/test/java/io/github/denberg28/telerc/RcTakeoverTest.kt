package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class RcTakeoverTest {
    private fun packet(channel:Int,boot:Long=1000,system:Int=1):ByteArray {
        val p=ByteArray(42)
        for(i in 0..3)p[i]=(boot ushr(i*8)).toByte()
        p[16]=channel.toByte();p[17]=(channel ushr 8).toByte();p[40]=7
        val f=byteArrayOf(254.toByte(),42,1,system.toByte(),1,65)+p
        var crc=65535
        for(v in f.drop(1).map{it.toInt() and 255}+118){var t=v xor(crc and 255);t=(t xor(t shl 4))and 255;crc=((crc ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
        return f+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
    }
    @Test fun unknownStaleForeignAndCorruptAuthorityFailClosed() {
        val gate=RcTakeover()
        assertFalse(gate.allowed(1000))
        gate.receive(packet(2000,system=2),1,1000);assertFalse(gate.allowed(1000))
        val bad=packet(2000);bad[16]=0;gate.receive(bad,1,1000);assertFalse(gate.allowed(1000))
        gate.receive(packet(2000),1,1000);assertTrue(gate.allowed(1000))
        assertFalse(gate.allowed(999));assertFalse(gate.allowed(1750))
    }
    @Test fun repeatedSourceTimeAndRecoveryNeverGrantDrivingAuthority() {
        val gate=RcTakeover()
        gate.receive(packet(2000),1,1000)
        gate.receive(packet(2000),1,1600)
        assertFalse(gate.allowed(1750))
        gate.receive(packet(1000,1100),1,1800);assertFalse(gate.allowed(1800))
        gate.receive(packet(2000,1200),1,1900);assertTrue(gate.allowed(1900))
        gate.reset();assertFalse(gate.allowed(1901))
    }
}
