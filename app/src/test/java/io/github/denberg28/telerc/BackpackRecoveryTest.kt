package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket

class BackpackRecoveryTest {
    @Test fun longTelemetryOutageDoesNotPreventRegistrationOrFloodTheLink() {
        val recovery = BackpackRecovery()
        var checks = 0
        val present = { checks++; true }
        assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(0, true, true, present))
        for (time in 1L..999L) assertEquals(BackpackRecovery.Action.WAIT, recovery.poll(time, true, true, present))
        assertEquals(1, checks)
        for (time in listOf(1000L, 5000L, 60000L, 3600000L))
            assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(time, true, true, present))
        assertEquals(5, checks)
    }

    @Test fun backgroundAndDisconnectDoNotRegisterOrInspectNetworks() {
        val recovery = BackpackRecovery()
        val forbidden = { error("Inactive session inspected a network") }
        assertEquals(BackpackRecovery.Action.WAIT, recovery.poll(0, false, true, forbidden))
        assertEquals(BackpackRecovery.Action.WAIT, recovery.poll(5000, true, false, forbidden))
        assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(6000, true, true) { true })
    }

    @Test fun removedBoundNetworkRequestsRebindAndNewSessionCanRegister() {
        val recovery = BackpackRecovery()
        assertEquals(BackpackRecovery.Action.REGISTER, recovery.poll(100, true, true) { true })
        assertEquals(BackpackRecovery.Action.REBIND, recovery.poll(1100, true, true) { false })
        // A replacement socket has a new recovery policy; no stale retry timestamp is inherited.
        assertEquals(BackpackRecovery.Action.REGISTER, BackpackRecovery().poll(1101, true, true) { true })
    }

    @Test fun ordinaryWifiRebindNeverRestoresDrivingAuthority() {
        val recovery=BackpackRecovery()
        val control=LiveControlGate()
        assertTrue(control.enable(1,100,100))
        assertEquals(BackpackRecovery.Action.REBIND,recovery.poll(1100,true,true) { false })
        control.disable() // Existing session teardown revokes authority.
        assertEquals(BackpackRecovery.Action.REGISTER,BackpackRecovery().poll(1200,true,true) { true })
        assertFalse(control.get())
    }

    @Test fun repeatedRecoveryStillCannotSendPhoneCommands() {
        val sent = mutableListOf<ByteArray>()
        val transport = FcElrsTelemetryTransport(object : LinkTransport {
            override fun send(packet: DatagramPacket) {
                sent.add(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
            }
            override fun receive(packet: DatagramPacket) {}
            override fun close() {}
        })
        val recovery = BackpackRecovery()
        for ((sequence, time) in listOf(0L, 1000L, 60000L).withIndex()) {
            if (recovery.poll(time, true, true) { true } == BackpackRecovery.Action.REGISTER) {
                val ping = FcElrs.registration(sequence)
                transport.send(DatagramPacket(ping, ping.size))
            }
        }
        for (command in listOf(Mavlink.override(1, 1, 1, 1900, 1900, 1500, 1500),
            Mavlink.release(2, 1, 1), Mavlink.armDisarm(3, 1, 1, true),
            WaypointProtocol.hold(4, 1), WaypointProtocol.heartbeat(5))) {
            assertThrows(java.net.SocketException::class.java) { transport.send(DatagramPacket(command, command.size)) }
        }
        assertEquals(3, sent.size)
        assertTrue(sent.all(FcElrs::isRegistration))
    }
}
