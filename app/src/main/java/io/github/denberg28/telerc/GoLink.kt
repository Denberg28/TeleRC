package io.github.denberg28.telerc

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

// Mission — Upload gate. Only a disarmed, CH7-permitted ArduRover on the foreground Mission tab may upload.
internal object GoMissionGate {
    fun ready(
        connected: Boolean,
        peerVerified: Boolean,
        fc: Boolean,
        missionForeground: Boolean,
        heartbeatFresh: Boolean,
        armed: Boolean,
        drivingEnabled: Boolean,
        armPending: Boolean,
        switchFresh: Boolean,
        radioBlocked: Boolean
    ): Boolean = connected && peerVerified && fc && missionForeground && heartbeatFresh &&
        !armed && !drivingEnabled && !armPending && switchFresh && !radioBlocked
}

// Connection — Wi-Fi UDP transport adopted from TeleRC Go.
// A single worker thread owns the bound Wi-Fi socket, 20 ms RC loop, ARM transaction,
// rover GPS telemetry parsing and acknowledged ArduRover mission uploads.
internal class GoLink(
    context: Context,
    private val update: () -> Unit,
    private val onRoverPosition: (Double, Double) -> Unit = { _, _ -> }
) : AutoCloseable {
    data class Snapshot(
        val connected: Boolean,
        val verified: Boolean,
        val fresh: Boolean,
        val armed: Boolean,
        val enabled: Boolean,
        val armReady: Boolean,
        val handset: Boolean,
        val stopped: Boolean,
        val receiver: Boolean,
        val text: String,
        val rx: Long,
        val tx: Long,
        val age: Long,
        val pending: Boolean,
        val resume: Boolean = false,
        val driveReady: Boolean = false,
        val fc: Boolean = false,
        val targetSystem: Int = 1,
        val missionReady: Boolean = false,
        val missionActive: Boolean = false,
        val missionStatus: String = "Plan saved on phone",
        val stepReady: Boolean = false,
        val stepActive: Boolean = false,
        val stepStatus: String = "Step test idle"
    )

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val gate = GoControl()
    private val armRequest = GoArmRequest()
    private val missionTransfer = MissionTransfer()
    private val stepRunner = StepRunner()
    private val lock = Any()
    @Volatile private var desired: GoEndpoint? = null
    @Volatile private var active = false
    @Volatile private var closed = false
    private var connected = false
    private var verified = false
    private var peerVerified = false
    private var fc = false
    private var balancing = false
    private var explicitDisarm = false
    private var ownersSeen = false
    private var capable = false
    private var owner = 0
    private var ownerAt = 0L
    private var radioBlocked = false
    private var switchAt = 0L
    private var receiver = false
    private var takeRequested = false
    private var awaitingArmUntil = 0L
    private var stopPending = false
    private var missionForeground = false
    private var message = "Join rover Wi-Fi, then connect"
    private var steer = 1500
    private var drive = 1500
    private var rx = 0L
    private var tx = 0L
    private var sequence = 0
    private fun now() = SystemClock.elapsedRealtime()
    private val worker = thread(name = "telerc-go-link", isDaemon = true) { run() }

    private fun ownerFresh(n: Long) = ownerAt > 0 && n >= ownerAt && n - ownerAt < 750
    private fun switchFresh(n: Long) = switchAt > 0 && n >= switchAt && n - switchAt < 750
    private fun mayUsePhone(n: Long) = !radioBlocked && (!fc || switchFresh(n)) && (!ownersSeen || ownerFresh(n) && owner == 1)

    private fun missionReadyLocked(n: Long): Boolean = !stepRunner.active && GoMissionGate.ready(
        connected = connected,
        peerVerified = peerVerified,
        fc = fc,
        missionForeground = missionForeground,
        heartbeatFresh = gate.fresh(n),
        armed = gate.armed,
        drivingEnabled = gate.enabled,
        armPending = armRequest.active() || awaitingArmUntil > n,
        switchFresh = switchFresh(n),
        radioBlocked = radioBlocked
    )

    private fun stepReadyLocked(n: Long): Boolean = GoStepGate.ready(
        connected = connected,
        peerVerified = peerVerified,
        missionForeground = missionForeground,
        heartbeatFresh = gate.fresh(n),
        armed = gate.armed,
        stopped = gate.stopped,
        mayUsePhone = mayUsePhone(n),
        missionTransferActive = missionTransfer.active
    )

    private fun stepStatusLocked(n: Long): String {
        if (stepRunner.active) return stepRunner.status
        return when {
            !connected || !peerVerified -> "Connect Wi-Fi to test 1m steps"
            !gate.fresh(n) -> "Waiting for fresh rover heartbeat"
            radioBlocked || (ownersSeen && owner == 2) -> "Transmitter control active"
            gate.stopped -> if (fc) "Select MANUAL mode for step test" else "Rover stop/fault active"
            armRequest.active() || awaitingArmUntil > n -> "Arming · checking neutral…"
            !gate.armed -> "Disarmed · tap ARM to test 1m steps"
            !mayUsePhone(n) -> "Waiting for phone permission (CH7 / ownership)"
            else -> if (stepRunner.status == "Step test idle") "ARMED · ready for 1m open-loop step test" else stepRunner.status
        }
    }

    private fun missionStatusLocked(n: Long): String {
        if (missionTransfer.active) return missionTransfer.status
        val base = missionTransfer.status
        if (missionReadyLocked(n)) return "$base · Wi-Fi upload ready"
        return when {
            !connected || !peerVerified -> "$base · connect ArduRover Wi-Fi to upload"
            !fc -> "$base · standalone rover (local plan only)"
            !gate.fresh(n) -> "$base · waiting for fresh FC heartbeat"
            gate.armed -> "$base · disarm rover before uploading"
            radioBlocked -> "$base · Switch A (CH7) set to transmitter"
            !switchFresh(n) -> "$base · waiting for CH7 permission telemetry"
            else -> base
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        val n = now()
        val mReady = missionReadyLocked(n)
        val sReady = stepReadyLocked(n)
        Snapshot(
            connected = connected,
            verified = verified && (if (fc) switchFresh(n) else capable && ownersSeen && ownerFresh(n)),
            fresh = gate.fresh(n),
            armed = gate.armed,
            enabled = gate.enabled,
            armReady = gate.armReady(n),
            handset = radioBlocked || ownersSeen && owner == 2,
            stopped = gate.stopped,
            receiver = receiver,
            text = message,
            rx = rx,
            tx = tx,
            age = gate.heartbeatAt?.let { n - it } ?: -1,
            pending = armRequest.active() || awaitingArmUntil > n,
            resume = balancing && gate.armed && !gate.enabled,
            driveReady = gate.mayDrive(n),
            fc = fc,
            targetSystem = desired?.system ?: 1,
            missionReady = mReady,
            missionActive = missionTransfer.active,
            missionStatus = missionStatusLocked(n),
            stepReady = sReady,
            stepActive = stepRunner.active,
            stepStatus = stepStatusLocked(n)
        )
    }

    fun connect(value: GoEndpoint) {
        require(value.valid())
        disconnect()
        synchronized(lock) {
            desired = value
            active = true
            message = "Connecting…"
        }
        update()
    }

    fun foreground(value: Boolean) {
        active = value
        if (!value) {
            synchronized(lock) {
                missionTransfer.cancel("App backgrounded; upload cancelled")
                stepRunner.cancel("App backgrounded · stopped")
            }
            stop(true)
        }
    }

    fun setMissionForeground(value: Boolean) {
        synchronized(lock) {
            missionForeground = value
            if (!value) {
                missionTransfer.cancel("Screen changed; upload cancelled")
                stepRunner.cancel("Screen changed · stopped")
            }
        }
        if (value) stop(true)
        update()
    }

    fun beginStepPlan(steps: List<StepCommand>, calibration: StepCalibration): Boolean {
        val started = synchronized(lock) {
            val n = now()
            if (!stepReadyLocked(n) || stepRunner.active || steps.size !in 1..StepRunner.MAX_STEPS) {
                return@synchronized false
            }
            steer = 1500; drive = 1500
            stepRunner.begin(steps, calibration, n)
            true
        }
        update()
        return started
    }

    fun beginCalibrationPulse(turnOnly: Boolean, durationMs: Long, calibration: StepCalibration): Boolean {
        val started = synchronized(lock) {
            val n = now()
            if (!stepReadyLocked(n) || stepRunner.active) return@synchronized false
            steer = 1500; drive = 1500
            stepRunner.beginCalibrationPulse(turnOnly, durationMs, calibration, n)
            true
        }
        update()
        return started
    }

    fun stopStepPlan(reason: String = "Stopped by operator") {
        synchronized(lock) {
            val wasActive = stepRunner.active
            stepRunner.cancel(reason)
            if (wasActive || gate.enabled) {
                steer = 1500; drive = 1500
                stopPending = true
            }
        }
        stop(true)
        update()
    }

    fun beginMissionUpload(plan: List<Waypoint>): Boolean {
        val started = synchronized(lock) {
            val n = now()
            val target = desired?.system ?: return@synchronized false
            if (!missionReadyLocked(n) || missionTransfer.active || plan.size !in 1..MissionPlan.MAX_WAYPOINTS) {
                return@synchronized false
            }
            missionTransfer.begin(plan, target, n)
            true
        }
        update()
        return started
    }

    fun cancelMissionUpload(reason: String) {
        synchronized(lock) { missionTransfer.cancel(reason) }
        update()
    }

    fun missionEdited() {
        synchronized(lock) { missionTransfer.edited() }
        update()
    }

    fun axes(s: Int, d: Int) = synchronized(lock) {
        steer = s.coerceIn(1100, 1900)
        drive = d.coerceIn(1100, 1900)
    }

    private fun revoke(text: String, sendStop: Boolean) {
        stepRunner.cancel("$text · stopped")
        gate.stop(); armRequest.cancel(); awaitingArmUntil = 0; takeRequested = false; steer = 1500; drive = 1500
        if (sendStop) stopPending = true
        message = text
    }

    fun toggleArm() = synchronized(lock) {
        val n = now()
        if (missionTransfer.active) return@synchronized
        if (radioBlocked || ownersSeen && owner == 2) return@synchronized
        if (balancing && gate.armed && !gate.enabled && active && connected && verified && mayUsePhone(n)) {
            steer = 1500; drive = 1500
            if (gate.resumeBalancing(n)) message = "Resuming · checking neutral"
            return@synchronized
        }
        if (stepRunner.active || gate.armed || gate.enabled || armRequest.active() || awaitingArmUntil != 0L) {
            explicitDisarm = true; revoke("Disarming…", true); return@synchronized
        }
        if (!active || !connected || !verified || !gate.fresh(n) || gate.stopped) return@synchronized
        if (fc && !switchFresh(n)) { message = "Waiting for radio switch telemetry"; return@synchronized }
        if (!fc && (!ownersSeen || !capable)) { message = "Update Go rover firmware"; return@synchronized }
        if (ownersSeen && !ownerFresh(n)) { message = "Waiting for control ownership"; return@synchronized }
        steer = 1500; drive = 1500; armRequest.begin(gate.epoch, n)
        if (ownersSeen && owner != 1) takeRequested = true else gate.enable(n)
        message = "Arming · checking neutral"
    }

    fun stop(controlOnly: Boolean = false) = synchronized(lock) {
        val controlled = stepRunner.active || gate.enabled || armRequest.active() || awaitingArmUntil != 0L
        revoke(
            if (balancing && gate.armed) "Travel paused · local balance active" else "Control stopped",
            controlled || !controlOnly && gate.armed && !radioBlocked
        )
    }

    fun disconnect() {
        stop(true)
        synchronized(lock) {
            missionTransfer.disconnected()
            stepRunner.cancel("Disconnected · stopped")
            desired = null
            gate.disconnect()
            connected = false
            verified = false
            peerVerified = false
            message = "Disconnected"
        }
        update()
    }

    override fun close() {
        disconnect()
        closed = true
        worker.interrupt()
    }

    private fun wifi(): Network? =
        cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }

    private fun run() {
        while (!closed) {
            val config = desired
            if (config == null || !active) {
                try { Thread.sleep(50) } catch (_: InterruptedException) {}
                continue
            }
            var udp: DatagramSocket? = null
            var localPeerVerified = false
            try {
                val network = wifi() ?: throw java.net.SocketException("Join rover Wi-Fi")
                val remote = InetAddress.getByName(config.host)
                // Assign before bind so every failed bind is closed cleanly.
                var fallback = false
                var channel = DatagramSocket(null); udp = channel
                try {
                    channel.bind(InetSocketAddress(config.localPort))
                } catch (e: java.net.BindException) {
                    channel.close()
                    if (config.localPort == 0) throw e
                    channel = DatagramSocket(null); udp = channel; channel.bind(InetSocketAddress(0)); fallback = true
                }
                channel.soTimeout = 5
                network.bindSocket(channel)
                if (desired !== config) { channel.close(); continue }
                synchronized(lock) {
                    connected = true; verified = false; peerVerified = false; fc = false; balancing = false
                    explicitDisarm = false; gate.disconnect(); armRequest.cancel(); awaitingArmUntil = 0
                    ownersSeen = false; capable = false; owner = 0; ownerAt = 0; switchAt = 0
                    radioBlocked = false; receiver = false; stopPending = false; takeRequested = false
                    message = "Waiting for rover"
                }
                var discover = 0L; var sent = 0L; var uiAt = 0L; var networkAt = 0L; var gcsAt = 0L
                var boot: Long? = null; var kind: Int? = null; var requestedStreams = false; var lastFcBoot: Long? = null
                val opened = now()
                fun send(bytes: ByteArray) { channel.send(DatagramPacket(bytes, bytes.size, remote, config.port)); tx++ }
                fun stopFrames(releasing: Boolean) {
                    val n = now()
                    if (!localPeerVerified || !mayUsePhone(n)) { explicitDisarm = false; return }
                    repeat(3) {
                        send(GoProtocol.rc(sequence++, config.system, 1500, 1500))
                        if (fc && explicitDisarm) send(GoProtocol.hold(sequence++, config.system))
                        if (GoStopPolicy.disarm(balancing, explicitDisarm, fc)) {
                            send(Mavlink.armDisarm(sequence++, config.system, 1, false))
                        }
                        if (!fc || explicitDisarm || releasing) send(GoProtocol.rc(sequence++, config.system, 0, 0))
                    }
                    if (fc && releasing) send("TELERC_DISCONNECT_V1".toByteArray(Charsets.US_ASCII))
                    explicitDisarm = false
                }

                val input = ByteArray(8193)
                while (!closed && desired === config && active) {
                    val n = now()
                    if (n - networkAt >= 500) {
                        networkAt = n
                        if (network !in cm.allNetworks || wifi() != network) throw java.net.SocketException("Wi-Fi changed")
                    }
                    synchronized(lock) {
                        if (gate.expire(n)) {
                            missionTransfer.cancel("Heartbeat lost; upload cancelled")
                            stopPending = true
                            revoke("Telemetry lost · control paused", true)
                        }
                        if ((stepRunner.active || gate.enabled || armRequest.active()) && ((ownersSeen && !ownerFresh(n)) || (fc && !switchFresh(n)) || radioBlocked)) {
                            revoke(if (radioBlocked) "Transmitter control" else "Control telemetry lost", false)
                            stopPending = false
                        }
                        if (armRequest.active() && !armRequest.valid(gate.epoch, n)) revoke("Arming expired · tap ARM", true)
                        if (awaitingArmUntil != 0L && n >= awaitingArmUntil) revoke("ARM not confirmed · tap ARM", true)
                        if (stopPending) { stopFrames(false); stopPending = false }
                        if (n - discover >= 500) { discover = n; send("TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII)) }
                        if (fc && localPeerVerified && !requestedStreams) {
                            send(GoProtocol.requestRcChannels(sequence++, config.system))
                            send(GoProtocol.requestGlobalPosition(sequence++, config.system))
                            requestedStreams = true
                        }
                        if (takeRequested && armRequest.valid(gate.epoch, n) && !radioBlocked) {
                            send(GoElrs.take(sequence++, config.system)); takeRequested = false
                        }
                        if (fc && (gate.enabled || missionTransfer.active || stepRunner.active) && gate.fresh(n) && n - gcsAt >= 1000) {
                            send(GoProtocol.heartbeat(sequence++)); gcsAt = n
                        }
                        if (missionTransfer.active) {
                            if (!missionReadyLocked(n)) {
                                missionTransfer.cancel("Link or permission changed; upload cancelled")
                            } else {
                                missionTransfer.poll(n, sequence++)?.let(::send)
                            }
                        }
                        if (stepRunner.active) {
                            if (!stepReadyLocked(n)) {
                                stepRunner.cancel("Link or permission changed · stopped")
                                stopPending = true
                            } else if (n - sent >= 20) {
                                val out = stepRunner.poll(n)
                                send(GoProtocol.rc(sequence++, config.system, out.steer, out.drive))
                                sent = n
                                if (!stepRunner.active) {
                                    // Guarantee neutral frames arrive cleanly at end of step sequence.
                                    repeat(2) { send(GoProtocol.rc(sequence++, config.system, 1500, 1500)) }
                                }
                            }
                        } else if (gate.enabled && gate.fresh(n) && mayUsePhone(n) && n - sent >= 20) {
                            val s = if (!missionForeground && gate.mayDrive(n)) steer else 1500
                            val d = if (!missionForeground && gate.mayDrive(n)) drive else 1500
                            send(GoProtocol.rc(sequence++, config.system, s, d))
                            gate.sent(s, d, n); sent = n
                        }
                        if (armRequest.valid(gate.epoch, n) && mayUsePhone(n) && gate.armReady(n)) {
                            send(Mavlink.armDisarm(sequence++, config.system, 1, true))
                            armRequest.cancel(); awaitingArmUntil = n + 3000; message = "Confirming ARM…"
                        }
                        if (!localPeerVerified && fallback && n - opened > 3000) message = "No rover reply · close other controller"
                    }
                    try {
                        val packet = DatagramPacket(input, input.size)
                        channel.receive(packet)
                        if (packet.address != remote || packet.port != config.port || packet.length > 8192) continue
                        var gpsFix: Pair<Double, Double>? = null
                        synchronized(lock) {
                            rx++
                            for (frame in Mavlink.frames(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))) {
                                val hb = Mavlink.heartbeat(frame)
                                if (hb != null && hb.system == config.system) {
                                    val offset = if (frame[0] == 0xfe.toByte()) 6 else 10
                                    val autopilot = frame[offset + 5].toInt() and 255
                                    if (frame[offset + 4].toInt() != 10 || autopilot !in listOf(0, 3) || kind != null && kind != autopilot) {
                                        revoke("Unsupported rover", true); verified = false; peerVerified = false; localPeerVerified = false; continue
                                    }
                                    var mode = 0L; for (i in 0..3) mode = mode or ((frame[offset + i].toLong() and 255) shl (8 * i))
                                    if (autopilot == 0 && mode == 0L) {
                                        revoke("Matched Go firmware required", true); verified = false; peerVerified = false; localPeerVerified = false; continue
                                    }
                                    kind = autopilot; fc = autopilot == 3; localPeerVerified = true; peerVerified = true; verified = true
                                    if (!fc && boot != null && boot != mode) {
                                        revoke("Rover restarted · tap ARM", false)
                                        ownersSeen = false; capable = false; ownerAt = 0; owner = 0; radioBlocked = false; stopPending = false
                                    }
                                    if (!fc) boot = mode
                                    if (fc && mode != 0L) {
                                        revoke(if (mode == 4L) "HOLD · select MANUAL" else "Select MANUAL", false)
                                        gate.receiverStop(true)
                                    } else if (fc || !ownersSeen) {
                                        gate.receiverStop(false)
                                    }
                                    val recovering = gate.heartbeatAt != null && !gate.fresh(now())
                                    if (recovering) {
                                        missionTransfer.cancel("Heartbeat recovered; upload cancelled")
                                        revoke("Link restored · tap ARM", false)
                                    }
                                    val wasArmed = gate.armed
                                    gate.heartbeat(hb.armed, now())
                                    if (hb.armed) missionTransfer.cancel("Rover armed; upload cancelled")
                                    if (!hb.armed && stepRunner.active) stepRunner.cancel("Rover disarmed · stopped")
                                    if (hb.armed && gate.enabled) {
                                        awaitingArmUntil = 0; message = "ARMED"
                                    } else if (wasArmed && !hb.armed && gate.enabled) {
                                        revoke("Disarmed", false)
                                    } else if (!gate.enabled && !armRequest.active()) {
                                        message = when {
                                            radioBlocked || ownersSeen && owner == 2 -> "Transmitter control"
                                            gate.stopped -> if (fc && mode == 4L) "HOLD · select MANUAL" else "Select MANUAL"
                                            hb.armed -> "Armed elsewhere"
                                            !fc && !capable -> if (now() - opened > 1500) "Update Go rover firmware" else "Checking rover firmware"
                                            else -> "Connected · tap ARM"
                                        }
                                    }
                                }
                                if (localPeerVerified && !fc) GoElrs.status(frame, config.system)?.let { s ->
                                    ownersSeen = true; capable = s.capable; balancing = s.balancing; owner = s.owner
                                    ownerAt = now(); receiver = s.receiver; radioBlocked = s.takeover || s.owner == 2
                                    gate.receiverStop(s.stopped)
                                    if (!capable) {
                                        revoke("Update Go rover firmware", false); stopPending = false
                                    } else if (radioBlocked || s.stopped) {
                                        revoke(if (s.stopped) "Rover stop/fault active" else "Transmitter control", false); stopPending = false
                                    } else if (armRequest.valid(gate.epoch, now()) && owner == 1) {
                                        gate.enable(now())
                                    } else if (gate.enabled && owner != 1) {
                                        revoke("Phone authority released", false); stopPending = false
                                    }
                                }
                                if (localPeerVerified && fc) {
                                    GoRadioTelemetry.sample(frame, config.system)?.let { s ->
                                        val previousBoot = lastFcBoot
                                        if (previousBoot != null && s.bootMs < previousBoot) {
                                            missionTransfer.cancel("FC restarted; upload cancelled")
                                            requestedStreams = false
                                            revoke("FC restarted · tap ARM", false); stopPending = false
                                        }
                                        lastFcBoot = s.bootMs; switchAt = now(); radioBlocked = !s.phoneAllowed
                                        if (radioBlocked) {
                                            missionTransfer.cancel("Transmitter selected; upload cancelled")
                                            revoke("Transmitter control", false); stopPending = false
                                        }
                                    }
                                    Mavlink.globalPosition(frame)?.takeIf { it.system == config.system }?.let { pos ->
                                        gpsFix = pos.latitude to pos.longitude
                                    }
                                    if (missionTransfer.active) {
                                        missionTransfer.receive(frame)
                                        if (missionTransfer.active && missionReadyLocked(now())) {
                                            missionTransfer.poll(now(), sequence++)?.let(::send)
                                        }
                                    }
                                }
                                Mavlink.commandAck(frame)?.takeIf { it.system == config.system && it.command == 400 }?.let {
                                    if (!GoStopPolicy.armReplyPending(it.result)) revoke("ARM ${it.resultText}", true)
                                }
                            }
                        }
                        gpsFix?.let { (lat, lon) -> onRoverPosition(lat, lon) }
                    } catch (_: SocketTimeoutException) {}
                    if (n - uiAt >= 100) { uiAt = n; update() }
                }
                synchronized(lock) {
                    val releasing = desired == null
                    if (releasing || stopPending || stepRunner.active || gate.enabled || armRequest.active() || awaitingArmUntil != 0L) {
                        stepRunner.cancel("Link stopped · neutral")
                        stopFrames(releasing)
                    }
                }
            } catch (e: Exception) {
                synchronized(lock) {
                    missionTransfer.cancel("Wi-Fi link interrupted; upload cancelled")
                    revoke(
                        when {
                            desired == null -> "Disconnected"
                            e is java.net.BindException -> "Phone port busy · close other controller"
                            else -> "${e.message?.take(48) ?: "Wi-Fi lost"} · reconnecting"
                        },
                        false
                    )
                    connected = false; verified = false; peerVerified = false
                }
                update()
            } finally {
                udp?.close()
                synchronized(lock) {
                    missionTransfer.cancel("Link closed; upload cancelled")
                    stepRunner.cancel("Link closed · stopped")
                    connected = false; verified = false; peerVerified = false
                    gate.disconnect(); armRequest.cancel(); awaitingArmUntil = 0; stopPending = false
                }
            }
            val until = now() + 1000
            while (!closed && desired === config && now() < until) {
                try { Thread.sleep(50) } catch (_: InterruptedException) {}
            }
        }
    }
}
