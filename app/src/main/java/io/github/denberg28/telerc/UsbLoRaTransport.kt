package io.github.denberg28.telerc

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Native ESP32-S3 USB CDC ACM only; external CH340/CP210x UART adapters are not supported. */
class UsbLoRaTransport private constructor(
    private val manager: UsbManager,
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val control: UsbInterface,
    private val data: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint
) : LinkTransport {
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()
    private val parser = SerialDatagram.Parser()
    private val pending = ArrayDeque<ByteArray>()
    private val loopback = InetAddress.getByName("127.0.0.1")

    override fun send(packet: DatagramPacket) {
        synchronized(writeLock) {
            if (closed.get()) throw SocketException("USB link closed")
            val wire = SerialDatagram.encode(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
            // Fail closed on a partial write; never silently queue or retry an old throttle packet.
            val sent = connection.bulkTransfer(output, wire, wire.size, 100)
            if (sent != wire.size) { close(); throw SocketException("Incomplete USB command write") }
        }
    }
    override fun receive(packet: DatagramPacket) {
        if (closed.get()) throw SocketException("USB link closed")
        if (pending.isEmpty()) {
            val bytes = ByteArray(512)
            val count = connection.bulkTransfer(input, bytes, bytes.size, 50)
            if (closed.get() || manager.deviceList.values.none { it.deviceName == device.deviceName })
                throw SocketException("LoRa USB base detached")
            if (count > 0) pending.addAll(parser.feed(bytes, count, SystemClock.elapsedRealtime()))
        }
        val payload = pending.pollFirst() ?: throw SocketTimeoutException("No rover frame")
        if (payload.size > packet.length) throw SocketException("USB telemetry exceeds receive capacity")
        payload.copyInto(packet.data, packet.offset)
        packet.length = payload.size; packet.address = loopback; packet.port = 14550
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            connection.releaseInterface(data); connection.releaseInterface(control); connection.close()
        }
    }
    companion object {
        private data class Interfaces(val control: UsbInterface, val data: UsbInterface, val input: UsbEndpoint, val output: UsbEndpoint)
        private fun interfaces(device: UsbDevice): Interfaces? {
            val all = (0 until device.interfaceCount).map(device::getInterface)
            val control = all.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_COMM && it.interfaceSubclass == 2 } ?: return null
            val data = all.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA } ?: return null
            val endpoints = (0 until data.endpointCount).map(data::getEndpoint).filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            val input = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN } ?: return null
            val output = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT } ?: return null
            return Interfaces(control, data, input, output)
        }
        fun candidates(manager: UsbManager): List<UsbDevice> = manager.deviceList.values.filter {
            it.vendorId == 0x303a && interfaces(it) != null
        }
        fun open(manager: UsbManager, device: UsbDevice): UsbLoRaTransport {
            val parts = interfaces(device) ?: throw SocketException("Native ESP32-S3 CDC port required")
            val connection = manager.openDevice(device) ?: throw SocketException("USB permission unavailable")
            try {
                check(connection.claimInterface(parts.control, true) && connection.claimInterface(parts.data, true)) { "USB interfaces unavailable" }
                val lineCoding = byteArrayOf(0x00, 0xc2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08) // 115200, 8N1
                check(connection.controlTransfer(0x21, 0x20, 0, parts.control.id, lineCoding, lineCoding.size, 200) == lineCoding.size) { "USB line coding failed" }
                check(connection.controlTransfer(0x21, 0x22, 3, parts.control.id, null, 0, 200) >= 0) { "USB DTR/RTS failed" }
                return UsbLoRaTransport(manager, device, connection, parts.control, parts.data, parts.input, parts.output)
            } catch (error: Exception) {
                connection.releaseInterface(parts.data); connection.releaseInterface(parts.control); connection.close()
                throw error
            }
        }
    }
}
