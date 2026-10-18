package io.github.denberg28.telerc

/** Native ExpressLRS MAVLink through TX Backpack. CRSF uses the existing FC Wi-Fi/LoRa links. */
internal object FcElrs {
    const val TRANSPORT = "fc_elrs_backpack"
    const val HOST = "10.0.0.1"
    const val REMOTE_PORT = 14555
    const val LOCAL_PORT = 14550
    const val HOST_KEY = "elrs_host"
    const val PORT_KEY = "elrs_port"
    const val LISTEN_KEY = "elrs_listen"
    const val MAX_DATAGRAM = 8192 // Backpack batches complete MAVLink messages.
    const val HANDSET_ONLY = "ELRS handset controls this rover. Use FC Wi-Fi or LoRa with a CRSF receiver for phone control."
    fun phoneCommandsAllowed(transport: String?) = transport != TRANSPORT
    fun acceptsPeer(expectedHost: String, expectedPort: Int, actualHost: String?, actualPort: Int) =
        actualHost == expectedHost && actualPort == expectedPort && expectedPort in 1..65535

    const val CRSF_SETUP = "1. Receiver TX → F405 R2; receiver RX → T2; GND → GND. Use the receiver's rated supply. Keep Wi-Fi/LoRa on UART3 and GPS on UART6.\n\n" +
        "2. Mission Planner: SERIAL2_PROTOCOL=23, RSSI_TYPE=3. Enable CRSF in RC_PROTOCOLS (bit 9); preserve other required protocols. Set RC_OPTIONS bit 13 (ELRS 420 kbaud) where supported; preserve other bits. Reboot and calibrate RC.\n\n" +
        "3. Bind transmitter and receiver: same band, compatible firmware and binding phrase. Set CH1 steer and CH2 centered forward/reverse. Assign and test a transmitter HOLD mode switch.\n\n" +
        "4. Set a spare RCx_OPTION=46 (RC Override Enable). LOW blocks phone overrides; HIGH permits them. Center the handset before handover. Check SYSID_MYGCS=255 (MAV_GCS_SYSID on newer firmware), finite RC_OVERRIDE_TIME and RC/GCS failsafes. Keep Ignore RC Receiver and Ignore MAVLink Overrides disabled in RC_OPTIONS.\n\n" +
        "5. Phone: ArduRover Wi-Fi or LoRa USB → Connect → Controls → Enable control. STOP CONTROL releases to the handset; center it first. The handset stays bound. Reconnect never enables phone control.\n\n" +
        "Raise wheels to test handover, handset HOLD, phone loss and receiver loss. The override switch only blocks RC overrides; use the mode switch to interrupt Guided navigation."
    const val BACKPACK_SETUP = "1. Use MAVLink-capable ELRS TX + RX and a TX Backpack. Bind matching bands and compatible firmware. Minimum: ELRS 3.5 / Backpack 1.5.\n\n" +
        "2. Receiver TX → F405 R2; receiver RX → T2; GND → GND; rated supply. Set RX serial protocol and TX Link Mode to MAVLink. ELRS v4 sets RX protocol automatically.\n\n" +
        "3. F405: SERIAL2_PROTOCOL=2, SERIAL2_BAUD=460, RSSI_TYPE=5. Match the actual ELRS UART rate. Keep GPS on UART6. Reboot and verify CH1 steer / CH2 centered drive in Mission Planner.\n\n" +
        "4. ELRS Lua → Backpack → Telemetry → WiFi. Join ExpressLRS TX Backpack Wi-Fi (password: expresslrs), or the same router network. Do not use firmware-update Wi-Fi mode.\n\n" +
        "5. Set Backpack IP, Send port = Backpack Listen (default 14555), Listen port = Backpack Send (default 14550). AP IP: 10.0.0.1; use the assigned IP on a router. Connect.\n\n" +
        "Handset drives; TeleRC shows telemetry and FPV. Phone commands and live Waypoint are locked: native ELRS sends handset RC overrides continuously. For phone takeover with the handset linked, use CRSF + FC Wi-Fi/LoRa.\n\n" +
        "The phone needs the ELRS TX module and Backpack. Direct ELRS USB and AirPort are separate protocols and are not implemented here."
}

/** Session-level guard: handset telemetry cannot acquire phone authority through another UI path. */
internal class FcElrsTelemetryTransport(private val delegate: LinkTransport) : LinkTransport {
    override fun send(packet: java.net.DatagramPacket) {
        val payload=packet.data.copyOfRange(packet.offset,packet.offset+packet.length)
        if(payload.size!=17 || !payload.contentEquals(WaypointProtocol.heartbeat(payload[2].toInt() and 255)))
            throw java.net.SocketException("ELRS handset link rejects phone commands")
        delegate.send(packet)
    }
    override fun receive(packet: java.net.DatagramPacket) = delegate.receive(packet)
    override fun close() = delegate.close()
}
