package io.github.denberg28.telerc

import java.net.DatagramPacket
import java.net.DatagramSocket

/** Datagram semantics shared by legacy UDP and the framed USB LoRa base link. */
interface LinkTransport {
    fun send(packet: DatagramPacket)
    fun receive(packet: DatagramPacket)
    fun close()
}

class UdpTransport(private val socket: DatagramSocket) : LinkTransport {
    override fun send(packet: DatagramPacket) = socket.send(packet)
    override fun receive(packet: DatagramPacket) = socket.receive(packet)
    override fun close() = socket.close()
}
