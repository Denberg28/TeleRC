package io.github.denberg28.telerc

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.app.PendingIntent
import android.hardware.usb.UsbManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private enum class Page { SETUP, CONTROLS, TEST_DRIVE, LORA }
    private var page = Page.SETUP
    @Volatile private var socket: LinkTransport? = null
    private val usbMode get() = getSharedPreferences("link", MODE_PRIVATE).getString("transport", "wifi") == "lora_usb"
    private var loraStatus: TextView? = null
    private var loraInfo = "Attach one T3-S3 by native USB, connect, then read board settings."
    private val loraExchange = LoRaSetupExchange()
    private var loraDraft: LoRaSetupExchange.Draft? = null
    private var loraRead: Button? = null
    private var loraSave: Button? = null
    private var loraOperate: Button? = null
    private var loraLinkStatus: TextView? = null
    private var loraKey: EditText? = null
    private var loraReveal: Button? = null
    private var loraFingerprint: TextView? = null
    private val loraTimeout = Runnable {
        if (loraExchange.pending != null) {
            loraExchange.timeout()
            loraInfo = "No board reply after 3 seconds. Disconnect USB, reconnect and read settings before retrying."
            loraStatus?.text = loraInfo
            refreshUi()
        }
    }
    private var usbPermissionPending = false
    private var usbPermissionReceiver: BroadcastReceiver? = null
    private val connected = AtomicBoolean(false)
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var wantsLink = false
    private val reconnect = object : Runnable {
        override fun run() {
            if (!resumed || !wantsLink) return
            if (!connected.get()) start()
            reconnectHandler.postDelayed(this, 2000)
        }
    }
    private val controlEnabled = LiveControlGate()
    private val commandLock = Any()
    private var commandEpoch = 0L // Guarded by commandLock; cancels queued ARM after stop/lifecycle loss.
    private val txExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "telerc-tx").apply { isDaemon = true }
    }
    @Volatile private var heartbeatAt = 0L
    @Volatile private var bridgeStatusAt = 0L
    @Volatile private var bridgeRxBytes = 0L
    @Volatile private var bridgeFrames = 0L
    @Volatile private var bridgeAccepted = -1L
    @Volatile private var bridgeRejected = -1L
    @Volatile private var bridgeCommandBytes = -1L
    @Volatile private var target = 0
    @Volatile private var vehicleArmed: Boolean? = null
    @Volatile private var pendingArmState: Boolean? = null
    @Volatile private var lastArmAck = ""
    @Volatile private var lastLinkError = ""
    @Volatile private var steering = 1500
    @Volatile private var drive = 1500
    private var status: TextView? = null
    private var connect: Button? = null
    private var enable: Button? = null
    private var armButton: Button? = null
    private val functionButtons = mutableMapOf<String, Button>()
    private var steeringStick: JoystickView? = null
    private var driveStick: JoystickView? = null
    private var testCourse: TestDriveView? = null
    private val route = RouteSession()
    private var routeMap: RouteMapView? = null
    private var routeStatus: TextView? = null
    private var mapBadge: TextView? = null
    private var musicButton: Button? = null
    private var controlsPlaceholder: View? = null
    private var controlsLocate: Button? = null
    private var controlsEstimate: TextView? = null
    private val deadReckoning = DeadReckoning()
    private var mapActive = false
    private var showTestMap = true
    private var locationPermissionRequested = false
    private var pendingPhoneLocate = false
    private var started = false
    @Volatile private var resumed = false
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val phoneListener = LocationListener { location: Location ->
        if (!mapActive || !location.hasAccuracy() ||
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos !in 0L..10_000_000_000L) return@LocationListener
        if (route.addPhone(TrackPoint(location.latitude, location.longitude, location.time), location.accuracy)) {
            if (routeMap?.anchorToHomeIfWaiting() == true) deadReckoning.reset()
            updateRoute()
        }
        if (pendingPhoneLocate && location.accuracy.isFinite() && location.accuracy in 0f..50f &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
            (location.latitude != 0.0 || location.longitude != 0.0)) {
            pendingPhoneLocate = false
            routeMap?.locatePhoneFix(location.latitude, location.longitude)
        }
    }
    private var host: EditText? = null
    private var port: EditText? = null
    private var updateStatus: TextView? = null
    private lateinit var updater: AppUpdater
    private var endpoint: InetAddress? = null
    private var endpointPort = 0
    private val darkTheme get() = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
    private val ink get() = if (darkTheme) Color.rgb(232, 239, 246) else Color.rgb(27, 38, 49)
    private val muted get() = if (darkTheme) Color.rgb(164, 181, 196) else Color.rgb(96, 114, 131)
    private val accent get() = AppColors.accent(this)
    private val pale get() = if (darkTheme) Color.rgb(12, 20, 28) else Color.rgb(245, 248, 251)
    private val surface get() = if (darkTheme) Color.rgb(22, 34, 45) else Color.WHITE
    private val softAccent get() = AppColors.softAccent(this)


    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
    }
    private fun button(value: String, filled: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 15f
        minimumWidth = 0; minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0)
        setTextColor(if (filled) Color.WHITE else accent)
        background = shape(if (filled) accent else softAccent, 16)
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape(surface); elevation = dp(2).toFloat()
    }
    private fun LinearLayout.addCard(view: View) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (darkTheme) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        try {
            val session = getFileStreamPath("route-session.csv")
            if (session.length() <= 16_000_000L)
                route.decode(openFileInput("route-session.csv").bufferedReader().use { it.readText() })
        } catch (_: Exception) {}
        updater = AppUpdater(this) { updateStatus?.text = it }
        // Dedicated ARM/DISARM replaces the former Servo button. F1-F3 remain
        // exclusively available for external servo output assignments.
        val servoPrefs = getSharedPreferences("servo_assignments", MODE_PRIVATE)
        servoPrefs.edit().apply {
            remove("Servo")
            for (label in listOf("F1", "F2", "F3")) {
                if (servoPrefs.getInt(label, 0) < 0) putInt(label, 0)
            }
        }.apply()
        wantsLink = getSharedPreferences("link", MODE_PRIVATE).getBoolean("auto_connect", false)
        page = when (savedInstanceState?.getString("page")) {
            "CONTROLS" -> Page.CONTROLS
            "TEST_DRIVE" -> Page.TEST_DRIVE
            "LORA" -> Page.LORA
            else -> Page.SETUP
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name); super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); disableControl(); testCourse?.stop(); releaseMap(); render()
    }
    private fun switchTo(next: Page) {
        if (page == next) return
        disableControl()
        testCourse?.stop(); testCourse = null; releaseMap()
        page = next
        val orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        requestedOrientation = orientation
        render()
    }
    private fun shell(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(pale)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnApplyWindowInsetsListener { view, insets ->
            val left: Int; val top: Int; val right: Int; val bottom: Int
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom
            } else {
                left = insets.systemWindowInsetLeft; top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight; bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(dp(12) + left, dp(8) + top, dp(12) + right, dp(8) + bottom)
            insets
        }
    }
    private fun pageDropdown(label: String): Button {
        lateinit var anchor: Button
        anchor = button("$label ▾", true) {
            PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Setup")
                menu.add(0, 2, 1, "Controls")
                menu.add(0, 3, 2, "Test drive")
                menu.add(0, 4, 3, "LoRa setup")
                setOnMenuItemClickListener { item ->
                    switchTo(when (item.itemId) { 1 -> Page.SETUP; 2 -> Page.CONTROLS; 4 -> Page.LORA; else -> Page.TEST_DRIVE })
                    true
                }
            }.show()
        }
        return anchor
    }
    private fun controlColumn(top: View, joystick: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(top, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })
        addView(joystick, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun render() {
        hideLoRaKey()
        loraStatus = null
        loraRead = null; loraSave = null; loraOperate = null; loraLinkStatus = null
        loraKey = null; loraReveal = null; loraFingerprint = null
        armButton = null
        functionButtons.clear()
        window.statusBarColor = pale
        window.navigationBarColor = pale
        window.decorView.systemUiVisibility = if (darkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        when (page) {
            Page.SETUP -> renderSetup()
            Page.CONTROLS -> renderControls()
            Page.TEST_DRIVE -> renderTestDrive()
            Page.LORA -> renderLoRa()
        }
        refreshUi()
    }
    private fun armCaption(): String = when (vehicleArmed) {
        true -> "DISARM"
        false -> "ARM"
        null -> "ARM / DISARM"
    }

    private fun armDisarmButton(): Button = button(armCaption()) {
        toggleArmDisarm()
    }.apply {
        textSize = 13f
        armButton = this
        contentDescription = "${armCaption()} rover"
    }

    private fun functionCaption(label: String): String {
        val assignment = getSharedPreferences("servo_assignments", MODE_PRIVATE).getInt(label, 0)
        return if (assignment == 0) label else "$label · $assignment"
    }

    private fun refreshFunctionButtons() {
        armButton?.apply {
            text = armCaption()
            contentDescription = "${armCaption()} rover"
        }
        functionButtons.forEach { (label, control) ->
            control.text = functionCaption(label)
            control.contentDescription = "Assign external servo output to $label"
        }
    }
    private fun sendControlHandoverBlocking(
        udp: LinkTransport,
        remote: InetAddress,
        system: Int,
        remotePort: Int,
        releaseToReceiver: Boolean
    ) {
        synchronized(commandLock) {
            repeat(3) { sequence ->
                val neutral = Mavlink.override(sequence, system, 1, 1500, 1500, 1500, 1500)
                udp.send(DatagramPacket(neutral, neutral.size, remote, remotePort))
            }
            if (releaseToReceiver) {
                repeat(3) { sequence ->
                    val release = Mavlink.release(100 + sequence, system, 1)
                    udp.send(DatagramPacket(release, release.size, remote, remotePort))
                }
            }
        }
    }

    private fun disableControlState() {
        synchronized(commandLock) {
            commandEpoch++
            controlEnabled.disable()
            steering = 1500
            drive = 1500
        }
        if (page == Page.CONTROLS && mapActive) {
            deadReckoning.hold()
            controlsEstimate?.text = "RC STOPPED · last estimate frozen"
            updateRoute()
        }
        steeringStick?.isEnabled = false
        driveStick?.isEnabled = false
        steeringStick?.reset()
        driveStick?.reset()
        refreshUi()
    }

    private fun toggleArmDisarm() {
        val armed = vehicleArmed
        val udp = socket
        val remote = endpoint
        val system = target
        val remotePort = endpointPort
        if (!linkFresh() || armed == null || udp == null || remote == null || system == 0) {
            android.widget.Toast.makeText(this, "ARM state unavailable · wait for a fresh rover heartbeat",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Keep the live control session intact. Neutralize both axes before sending
        // ARM/DISARM so a stale joystick value cannot resume motion after the command.
        steering = 1500
        drive = 1500
        steeringStick?.reset()
        driveStick?.reset()
        pendingArmState = !armed
        lastArmAck = "WAITING FOR ACK"
        val epoch = synchronized(commandLock) { commandEpoch }
        val requestedAt = SystemClock.elapsedRealtime()

        txExecutor.execute {
            try {
                // ARM/DISARM must not switch RC source. Hold neutral but keep the
                // current MAVLink override/session alive; STOP/DISCONNECT own handover.
                repeat(3) { attempt ->
                    synchronized(commandLock) {
                        if (socket !== udp || !connected.get() || target != system ||
                            (!armed && (commandEpoch != epoch || !resumed || !linkFresh() ||
                                SystemClock.elapsedRealtime() - requestedAt >= 500))) {
                            runOnUiThread {
                                if (socket === udp) { pendingArmState = null; lastArmAck = "COMMAND CANCELLED"; refreshUi() }
                            }
                            return@execute
                        }
                        if (attempt == 0) sendControlHandoverBlocking(udp, remote, system, remotePort, releaseToReceiver = false)
                        // USB neutral writes can consume the remaining request lifetime.
                        if (!armed && (commandEpoch != epoch || !resumed || !linkFresh() ||
                                SystemClock.elapsedRealtime() - requestedAt >= 500)) {
                            runOnUiThread {
                                if (socket === udp) { pendingArmState = null; lastArmAck = "COMMAND CANCELLED"; refreshUi() }
                            }
                            return@execute
                        }
                        val command = Mavlink.armDisarm(200 + attempt, system, 1, !armed)
                        udp.send(DatagramPacket(command, command.size, remote, remotePort))
                    }
                    // Let lifecycle/STOP revoke the request between retries.
                    if (attempt < 2) Thread.sleep(40)
                }
                runOnUiThread {
                    android.widget.Toast.makeText(this@MainActivity,
                        if (armed) "DISARM command sent · awaiting rover confirmation"
                        else "ARM command sent · awaiting rover confirmation",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                lastArmAck = "SEND FAILED: " + e.javaClass.simpleName
                runOnUiThread {
                    android.widget.Toast.makeText(this@MainActivity,
                        "ARM/DISARM send failed: " + e.javaClass.simpleName,
                        android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun servoAssignmentButton(label: String): Button {
        require(label in listOf("F1", "F2", "F3"))
        val prefs = getSharedPreferences("servo_assignments", MODE_PRIVATE)
        lateinit var control: Button
        fun showAssignment() {
            val labels = listOf("F1", "F2", "F3")
            val available = listOf(0) + (1..16).filter { output ->
                output !in 5..8 && labels.none { it != label && prefs.getInt(it, 0) == output }
            }
            val names = available.map { if (it == 0) "Unassigned" else "Servo output $it" }.toTypedArray()
            val current = prefs.getInt(label, 0).takeIf { it in available } ?: 0
            AlertDialog.Builder(this).setTitle("$label external servo assignment")
                .setSingleChoiceItems(names, available.indexOf(current)) { dialog, index ->
                    prefs.edit().putInt(label, available[index]).apply()
                    refreshFunctionButtons()
                    dialog.dismiss()
                }
                .setView(text("External servo only · M5–M8 reserved for rover drive outputs", 12f, muted)
                    .apply { setPadding(dp(16), dp(8), dp(16), dp(8)) })
                .setNegativeButton("Close", null).show()
        }
        control = button(functionCaption(label)) { showAssignment() }
        control.textSize = 11f
        functionButtons[label] = control
        refreshFunctionButtons()
        return control
    }
    private fun hideLoRaKey() {
        loraKey?.transformationMethod = PasswordTransformationMethod.getInstance()
        loraReveal?.text = "Show"
        loraReveal?.contentDescription = "Show pairing key"
    }

    private fun sendLoRaAdmin(message: String) {
        val link = socket
        if (!usbMode || link == null || !connected.get()) {
            loraStatus?.text = "Connect a T3-S3 via native USB first."; return
        }
        disableControl()
        loraInfo = if (loraExchange.pending == "save") "Waiting for the board save confirmation…" else "Reading local board settings…"
        loraStatus?.text = loraInfo
        reconnectHandler.removeCallbacks(loraTimeout)
        reconnectHandler.postDelayed(loraTimeout, 3000)
        refreshUi()
        txExecutor.execute {
            try {
                synchronized(commandLock) {
                    if (socket !== link) return@execute
                    val bytes = message.toByteArray(Charsets.US_ASCII)
                    link.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), 14550))
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (socket === link) {
                        reconnectHandler.removeCallbacks(loraTimeout); loraExchange.timeout()
                        loraInfo = "USB request failed. Disconnect, reconnect and read settings before retrying."
                        loraStatus?.text = loraInfo; refreshUi()
                    }
                }
            }
        }
    }

    private fun renderLoRa() {
        host = null; port = null; enable = null; steeringStick = null; driveStick = null
        val root = shell()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(pageDropdown("LoRa setup"), LinearLayout.LayoutParams(dp(160), dp(48)))
        status = text("DISCONNECTED", 12f, accent, true)
        header.addView(status, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })

        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val wide = resources.configuration.screenWidthDp >= 640
        val body = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        center.addView(body, LinearLayout.LayoutParams(if (wide) minOf(dp(920), resources.displayMetrics.widthPixels - dp(64)) else -1, -2))
        scroll.addView(center)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(left, LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f).apply { if (wide) rightMargin = dp(8) })
        body.addView(right, LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f))
        fun section(column: LinearLayout, title: String) = card().apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(text(title, 13f, accent, true), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            column.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        fun LinearLayout.action(view: View) {
            addView(view, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
        fun LinearLayout.actions(first: View, second: View) {
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(first, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
            row.addView(second, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(row, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
        fun label(value: String) = text(value, 12f, muted).apply { gravity = Gravity.START }
        fun field(label: String, value: String, type: Int) = EditText(this).apply {
            hint = label; contentDescription = label; setSingleLine(); inputType = type
            setTextColor(ink); setHintTextColor(muted); textSize = 14f
            background = shape(pale, 12); setPadding(dp(10), 0, dp(10), 0); setText(value)
            // The key must not be retained by framework view-state/autofill.
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val device = section(left, "1 · DEVICE CONNECTION")
        device.addView(text("Attach one T3-S3 with a USB OTG data cable. Grant USB permission; close other serial apps.", 13f, ink))
        connect = button("Connect USB board") {
            hideLoRaKey()
            if (wantsLink) {
                wantsLink = false
                getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", false).apply()
                reconnectHandler.removeCallbacks(reconnect); stop(explicitDisconnect = true)
            } else {
                getSharedPreferences("link", MODE_PRIVATE).edit().putString("transport", "lora_usb").putBoolean("auto_connect", true).apply()
                wantsLink = true; start(); scheduleReconnect()
            }
            refreshUi()
        }
        loraRead = button("Read board settings", false) {
            if (loraExchange.beginRead()) sendLoRaAdmin("TELERC_LORA_GET_V1")
        }
        device.actions(connect!!, loraRead!!)
        loraStatus = text(loraInfo, 12f, ink)
        device.addView(loraStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val check = section(left, "3 · LINK CHECK")
        loraLinkStatus = text("Connect and read the local board first.", 13f, ink)
        check.addView(loraLinkStatus)
        loraOperate = button("Open Controls") {
            hideLoRaKey(); disableControl(); page = Page.CONTROLS; render()
        }
        check.action(loraOperate!!)
        check.addView(text("Enable control and ARM explicitly at neutral.", 12f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val pairing = section(right, "2 · RADIO & PAIRING")
        pairing.addView(text("Fixed control profile · SF7 / 500 kHz / CR4:5", 12f, muted))
        val prefs = getSharedPreferences("lora_pair", MODE_PRIVATE)
        val draft = loraDraft ?: LoRaSetupExchange.Draft(prefs.getString("mhz", "").orEmpty(),
            prefs.getString("power", "2").orEmpty(), prefs.getString("key", "").orEmpty()).also { loraDraft = it }
        val radioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun radioField(title: String, value: String, type: Int): EditText {
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val edit = field(title, value, type)
            column.addView(label(title)); column.addView(edit, LinearLayout.LayoutParams(-1, dp(48)))
            radioRow.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply { if (radioRow.childCount == 1) rightMargin = dp(8) })
            return edit
        }
        val frequency = radioField("Frequency (MHz)", draft.mhz, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val power = radioField("Power (2–17 dBm)", draft.power, InputType.TYPE_CLASS_NUMBER)
        pairing.addView(radioRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        pairing.addView(label("Shared pairing key · 64 hex digits"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val key = field("Pairing key", draft.key, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        loraKey = key
        loraReveal = button("Show", false) {
            val caret = key.selectionStart.coerceAtLeast(0)
            val reveal = key.transformationMethod != null
            key.transformationMethod = if (reveal) null else PasswordTransformationMethod.getInstance()
            key.setSelection(caret.coerceAtMost(key.length()))
            loraReveal?.text = if (reveal) "Hide" else "Show"
            loraReveal?.contentDescription = if (reveal) "Hide pairing key" else "Show pairing key"
        }.apply { contentDescription = "Show pairing key"; textSize = 13f }
        keyRow.addView(key, LinearLayout.LayoutParams(0, dp(48), 1f))
        keyRow.addView(loraReveal, LinearLayout.LayoutParams(dp(64), dp(48)).apply { leftMargin = dp(6) })
        pairing.addView(keyRow)
        loraFingerprint = text("", 12f, muted)
        pairing.addView(loraFingerprint)
        fun captureDraft() {
            loraDraft = LoRaSetupExchange.Draft(frequency.text.toString(), power.text.toString(), key.text.toString())
            loraFingerprint?.text = LoRaSetup.fingerprint(key.text.toString().trim())?.let { "Draft key fingerprint · $it (comparison hint)" }
                ?: "Enter or generate a 64-digit hex key"
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { captureDraft() }
            override fun afterTextChanged(s: Editable?) {}
        }
        frequency.addTextChangedListener(watcher); power.addTextChangedListener(watcher); key.addTextChangedListener(watcher)
        captureDraft()
        val generateKey = button("Generate key", false) {
            fun generate() {
                hideLoRaKey()
                val bytes = ByteArray(32); java.security.SecureRandom().nextBytes(bytes)
                key.setText(bytes.joinToString("") { "%02x".format(it.toInt() and 255) })
            }
            if (key.text.isNotEmpty()) AlertDialog.Builder(this).setTitle("Replace draft pairing key?")
                .setMessage("Both boards need the same key. This changes only the draft; the board stays unchanged until Save succeeds.")
                .setPositiveButton("Replace") { _, _ -> generate() }.setNegativeButton("Cancel", null).show()
            else generate()
        }
        loraSave = button("Save pairing") {
            hideLoRaKey()
            if (vehicleArmed == true) { loraStatus?.text = "Disarm and disconnect motor power before setup."; return@button }
            val value = LoRaSetupExchange.Draft(frequency.text.toString().trim(), power.text.toString().trim(), key.text.toString().trim())
            if (LoRaSetup.request(value.mhz, value.power, value.key) == null) {
                loraStatus?.text = "Enter 150–960 MHz with up to 3 decimal places, 2–17 dBm, and a nonzero 64-digit hex key."; return@button
            }
            val request = loraExchange.beginSave(value) ?: return@button
            sendLoRaAdmin(request)
        }
        pairing.actions(generateKey, loraSave!!)
        pairing.addView(text("Match both inactive boards. Save → restart → read back. Local frequency rules apply.", 12f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val help = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(help, LinearLayout.LayoutParams(-1, -2))
        val details = text("Keep motor power disconnected while pairing. Read BASE and ROVER separately; compare frequency, power and key fingerprint. An active radio cannot be edited here: follow the NVS reset/reflash guide to re-pair. The key is retained in private phone settings only after the board confirms Save.\n\nNative USB CDC only; BLE, USB-UART adapters, mesh routing and Meshtastic channel QR import are unavailable. The shared key authenticates direct radio packets; payloads are not encrypted. No Wi-Fi password is needed for this USB setup.", 12f, muted).apply { visibility = View.GONE }
        help.action(button("Setup details ▾", false) {
            details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE
        })
        help.addView(details, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        setContentView(root)
    }

    private fun renderSetup() {
        enable = null; steeringStick = null; driveStick = null
        val root = shell()
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(pageDropdown("Setup"), LinearLayout.LayoutParams(dp(120), dp(44)))
        header.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(button(if (darkTheme) "☀" else "☾", false) {
            disableControl()
            appearance.edit().putBoolean("dark", !darkTheme).apply()
            recreate()
        }.apply { contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme" },
            LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val connection = card().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(button(if (usbMode) "LoRa USB ▾" else "Wi-Fi UDP ▾", true) {
                if (!wantsLink) AlertDialog.Builder(this@MainActivity).setTitle("Control link")
                    .setItems(arrayOf("LoRa via USB base", "Legacy Wi-Fi UDP")) { _, choice ->
                        getSharedPreferences("link", MODE_PRIVATE).edit()
                            .putString("transport", if (choice == 0) "lora_usb" else "wifi").apply()
                        render()
                    }.show()
            }, LinearLayout.LayoutParams(-1, dp(30)))
            host = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "Bridge IPv4"; setTextColor(ink); setHintTextColor(muted)
                inputType = InputType.TYPE_CLASS_TEXT
                background = shape(pale, 12); setPadding(dp(12), 0, dp(12), 0)
                setText(if (connected.get()) endpoint?.hostAddress else
                    getSharedPreferences("link", MODE_PRIVATE).getString("host", "192.168.4.1"))
                isEnabled = !connected.get(); textSize = 15f; contentDescription = "Bridge IPv4 address"
            }
            port = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "UDP port"; setTextColor(ink); setHintTextColor(muted)
                inputType = InputType.TYPE_CLASS_NUMBER
                background = shape(pale, 12); setPadding(dp(12), 0, dp(12), 0)
                setText((if (connected.get()) endpointPort else
                    getSharedPreferences("link", MODE_PRIVATE).getInt("port", 14550)).toString())
                isEnabled = !connected.get(); textSize = 15f; contentDescription = "UDP port"
            }
            val endpointRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            endpointRow.addView(host, LinearLayout.LayoutParams(0, dp(36), 2f).apply { rightMargin = dp(6) })
            endpointRow.addView(port, LinearLayout.LayoutParams(0, dp(36), 1f))
            if (usbMode) addView(text("USB OTG → T3-S3 base → LoRa", 12f, muted),
                LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
            else addView(endpointRow, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
            connect = button("Connect") {
                if (wantsLink) {
                    wantsLink = false
                    getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", false).apply()
                    reconnectHandler.removeCallbacks(reconnect)
                    stop(explicitDisconnect = true)
                } else {
                    wantsLink = true
                    getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", true).apply()
                    start(); scheduleReconnect()
                }
            }
            addView(connect, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
        }
        left.addView(connection, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(8) })

        val link = card().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(text("LINK STATUS", 12f, accent, true))
            status = text("DISCONNECTED", 15f, ink, true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(status)
            addView(button("Diagnose link", false) {
                AlertDialog.Builder(this@MainActivity).setTitle("Link diagnostics")
                    .setMessage(linkDiagnosis()).setPositiveButton("OK", null).show()
            }, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })
        }
        left.addView(link, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(left, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })

        val theme = card().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(text("THEME & CONTROL FEEL", 12f, accent, true))
            val choices = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val selected = appearance.getString("accent", "Blue")
            AppColors.names.forEach { name ->
                choices.addView(button(if (selected == name) "✓ $name" else name, selected == name) {
                    appearance.edit().putString("accent", name).apply(); render()
                }.apply { textSize = 12f },
                    LinearLayout.LayoutParams(0, dp(34), 1f).apply { rightMargin = dp(4) })
            }
            addView(choices, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(4) })
            val sensitivityLabel = text("Sensitivity · ${appearance.getInt("sensitivity", 100)}%", 11f, muted)
            addView(SeekBar(this@MainActivity).apply {
                max = 75; progress = appearance.getInt("sensitivity", 100).coerceIn(25, 100) - 25
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
                thumbTintList = android.content.res.ColorStateList.valueOf(accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        sensitivityLabel.text = "Sensitivity · ${value + 25}%"
                        if (fromUser) appearance.edit().putInt("sensitivity", value + 25).apply()
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }, LinearLayout.LayoutParams(-1, dp(28)).apply { topMargin = dp(2) })
            addView(sensitivityLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1) })
        }
        right.addView(theme, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(8) })
        val updates = card().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(text("APP UPDATE", 12f, accent, true))
            addView(text("TeleRC ${BuildConfig.VERSION_NAME}", 14f, ink, true))
            addView(button("Check for updates", false) { updater.check() },
                LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })
            updateStatus = text("Signed APK · manual check", 11f, muted).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(updateStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        right.addView(updates, LinearLayout.LayoutParams(-1, 0, 1f))

        body.addView(right, LinearLayout.LayoutParams(0, -1, 1f))

        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderControls() {
        host = null; port = null; connect = null
        val root = shell()
        status = text("DISCONNECTED", 12f, accent, true).apply { gravity = Gravity.CENTER }
        lateinit var pages: Button
        pages = button("Controls ▾", true) {
            PopupMenu(this, pages).apply {
                menu.add(0, 1, 0, "Setup")
                menu.add(0, 2, 1, "Controls")
                menu.add(0, 3, 2, "Test drive")
                menu.add(0, 4, 3, "LoRa setup")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> switchTo(Page.SETUP)
                        2 -> Unit
                        3 -> switchTo(Page.TEST_DRIVE)
                        4 -> switchTo(Page.LORA)
                    }
                    true
                }
            }.show()
        }
        val arm = armDisarmButton()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun control(title: String, hint: String, vertical: Boolean, changed: (Int) -> Unit): Pair<LinearLayout, JoystickView> {
            lateinit var stick: JoystickView
            val panel = card().apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(text(title, 13f, ink, true).apply { gravity = Gravity.CENTER })
                val feedback = text("$hint  ·  1500", 11f, muted).apply { gravity = Gravity.CENTER }
                addView(feedback)
                stick = JoystickView(this@MainActivity, vertical) { value ->
                    if (controlEnabled.get() || value == 1500) {
                        changed(value)
                        feedback.text = "$hint  ·  $value"
                    }
                }
                addView(stick, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            return panel to stick
        }
        val (steerPanel, steerInput) = control("STEER", "CH1 · left / right", false) { steering = it }
        val (drivePanel, driveInput) = control("DRIVE", "CH2 · forward / reverse", true) { drive = it }
        steeringStick = steerInput; driveStick = driveInput
        val actions = card().apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val scene = FrameLayout(this@MainActivity)
            controlsPlaceholder = text("Rover · CH1 / CH2\n\nEnable control to show recovery map", 12f, muted).apply {
                gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            scene.addView(controlsPlaceholder, FrameLayout.LayoutParams(-1, -1))
            val map = RouteMapView(this@MainActivity, route)
            map.maxSpeedMetersPerSecond = getSharedPreferences("test_vehicle", MODE_PRIVATE)
                .getFloat("max_m_s", 2.8f).coerceIn(.1f, 30f).toDouble()
            routeMap = map
            map.view.visibility = View.GONE
            scene.addView(map.view, FrameLayout.LayoutParams(-1, -1))
            val badge = text("CYAN · ESTIMATE FROM SENT RC", 10f, ink, true).apply {
                background = shape(surface, 8); setPadding(dp(5), dp(2), dp(5), dp(2))
                visibility = View.GONE
            }
            mapBadge = badge
            scene.addView(badge, FrameLayout.LayoutParams(-2, dp(25), Gravity.TOP or Gravity.LEFT)
                .apply { leftMargin = dp(4); topMargin = dp(4) })
            controlsLocate = button("⌖", false) { locatePhone() }.apply {
                contentDescription = "Get phone GPS and center map on current phone position"; visibility = View.GONE
            }
            scene.addView(controlsLocate, FrameLayout.LayoutParams(dp(36), dp(34), Gravity.TOP or Gravity.RIGHT)
                .apply { rightMargin = dp(4); topMargin = dp(4) })
            controlsEstimate = text("EST 0 m · H 0° · 0 m/s", 10f, ink, true).apply {
                background = shape(surface, 8); setPadding(dp(5), dp(2), dp(5), dp(2))
                visibility = View.GONE
            }
            scene.addView(controlsEstimate, FrameLayout.LayoutParams(-2, dp(25), Gravity.BOTTOM or Gravity.RIGHT)
                .apply { rightMargin = dp(4); bottomMargin = dp(4) })
            if (started) map.onStart()
            if (resumed) map.onResume()
            addView(scene, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        enable = button("Enable control") {
            if (controlEnabled.get()) disableControl(releaseToReceiver = true) else if (synchronized(commandLock) {
                val allowed = controlEnabled.enable(target, heartbeatAt, SystemClock.elapsedRealtime())
                if (allowed) commandEpoch++
                allowed
            }) {
                steeringStick?.isEnabled = true; driveStick?.isEnabled = true
                if (!mapActive) setControlsMapVisible(true) else {
                    deadReckoning.hold()
                    if (routeMap?.livePreviewActive != true) routeMap?.beginLivePreview()
                    controlsEstimate?.text = "RC ACTIVE · estimate resumes with sent frames"
                }
                refreshUi()
            } else {
                android.widget.Toast.makeText(this,
                    "Waiting for rover heartbeat. Check bridge Wi-Fi, UART RX/TX and SERIAL3 settings.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
        row.addView(controlColumn(pages, steerPanel), LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
        row.addView(actions, LinearLayout.LayoutParams(0, -1, 5f).apply { rightMargin = dp(8) })
        row.addView(controlColumn(arm, drivePanel), LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))

        // Three-zone footer keeps link state centered and prevents control buttons from
        // entering its space on narrow landscape screens.
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val leftSlot = LinearLayout(this).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        enable?.textSize = 13f
        leftSlot.addView(enable, LinearLayout.LayoutParams(dp(156), -1))
        toolbar.addView(leftSlot, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
        status?.apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        toolbar.addView(status, LinearLayout.LayoutParams(dp(156), -1))
        val rightSlot = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        for (label in listOf("F1", "F2", "F3")) {
            rightSlot.addView(servoAssignmentButton(label),
                LinearLayout.LayoutParams(dp(46), -1).apply { leftMargin = dp(5) })
        }
        toolbar.addView(rightSlot, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        setContentView(root)
        if (route.rover.isNotEmpty() || route.estimated.isNotEmpty()) showRecoveryMap()
        updateRoute()
        // Ask while controls are still disabled; a permission dialog must never interrupt driving.
        if (!locationPermissionRequested && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequested = true
            root.post {
                if (page == Page.CONTROLS && !controlEnabled.get())
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
            }
        }
    }
    private fun setControlsMapVisible(visible: Boolean) {
        if (page != Page.CONTROLS) return
        mapActive = visible
        routeMap?.view?.visibility = if (visible) View.VISIBLE else View.GONE
        controlsPlaceholder?.visibility = if (visible) View.GONE else View.VISIBLE
        controlsLocate?.visibility = if (visible) View.VISIBLE else View.GONE
        controlsEstimate?.visibility = if (visible) View.VISIBLE else View.GONE
        mapBadge?.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            deadReckoning.reset()
            controlsEstimate?.text = "DISP 0 m · H 0° · 0 m/s"
            deadReckoning.turnDegreesPerSecond = getSharedPreferences("test_vehicle", MODE_PRIVATE)
                .getFloat("turn_deg_s", 220f).coerceIn(10f, 360f)
            routeMap?.beginLivePreview()
            startPhoneLocation()
            routeMap?.view?.post { routeMap?.draw() }
            updateRoute()
        } else {
            stopPhoneLocation(); deadReckoning.reset(); routeMap?.endLivePreview()
        }
    }

    private fun showRecoveryMap() {
        mapActive = true
        routeMap?.view?.visibility = View.VISIBLE
        controlsPlaceholder?.visibility = View.GONE
        controlsLocate?.visibility = View.VISIBLE
        controlsEstimate?.visibility = View.VISIBLE
        controlsEstimate?.text = "RC STOPPED · last estimate frozen"
        mapBadge?.visibility = View.VISIBLE
        startPhoneLocation()
        routeMap?.view?.post { routeMap?.draw() }
    }
    private fun renderTestDrive() {
        host = null; port = null; connect = null; enable = null
        steeringStick = null; driveStick = null
        val root = shell()
        lateinit var pages: Button
        pages = button("Test drive ▾", true) {
            PopupMenu(this, pages).apply {
                menu.add(0, 1, 0, "Setup")
                menu.add(0, 2, 1, "Controls")
                menu.add(0, 3, 2, "Test drive")
                menu.add(0, 4, 3, "LoRa setup")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> switchTo(Page.SETUP)
                        2 -> switchTo(Page.CONTROLS)
                        4 -> switchTo(Page.LORA)
                    }
                    true
                }
            }.show()
        }
        status = text("DISCONNECTED", 11f, accent, true).apply { gravity = Gravity.CENTER }
        lateinit var bottom: LinearLayout
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val course = TestDriveView(this)
        testCourse = course
        val vehiclePrefs = getSharedPreferences("test_vehicle", MODE_PRIVATE)
        course.maxYawRateDegrees = vehiclePrefs.getFloat("turn_deg_s", 220f).coerceIn(10f, 360f)
        val left = card().apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(text("STEER", 16f, ink, true).apply { gravity = Gravity.CENTER })
            addView(text("CH1  ·  left / right", 11f, muted).apply { gravity = Gravity.CENTER })
            val stick = JoystickView(this@MainActivity, false) { course.setSteering(it) }
            stick.isEnabled = true
            addView(stick, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        row.addView(controlColumn(pages, left), LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
        val middle = card().apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val scene = FrameLayout(this@MainActivity)
            scene.addView(course, FrameLayout.LayoutParams(-1, -1))
            val map = RouteMapView(this@MainActivity, route)
            map.maxSpeedMetersPerSecond = vehiclePrefs.getFloat("max_m_s", 2.8f).coerceIn(.1f, 30f).toDouble()
            routeMap = map
            course.onTestPose = { pose -> if (mapActive) map.updatePreview(pose) }
            map.view.visibility = View.GONE
            scene.addView(map.view, FrameLayout.LayoutParams(-1, -1))
            mapBadge = text("CYAN ROVER · OFFLINE PREVIEW", 10f, ink, true).apply {
                background = shape(surface, 8)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                visibility = View.GONE
                contentDescription = "Vehicle settings: name, estimated maximum speed and turn rate"
                setOnClickListener { editTestVehicle(course, map) }
            }
            scene.addView(mapBadge, FrameLayout.LayoutParams(-2, dp(25), Gravity.TOP or Gravity.LEFT).apply {
                leftMargin = dp(6); topMargin = dp(6)
            })
            if (started) map.onStart()
            if (resumed) map.onResume()
            addView(scene, LinearLayout.LayoutParams(-1, 0, 1f))
            bottom = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            var testMode = false
            val modeButton = button("Test", false) { }
            modeButton.contentDescription = "Open Test mode; tap again to return to Game"
            modeButton.textSize = 12f
            bottom.addView(modeButton, LinearLayout.LayoutParams(dp(62), dp(36)).apply { rightMargin = dp(4) })
            routeStatus = text("", 11f, muted)
            val locate = button("⌖", false) { locatePhone() }.apply {
                contentDescription = "Get phone GPS and center map on current phone position"; visibility = View.GONE
            }
            lateinit var viewMode: Button
            viewMode = button(if (showTestMap) "SIM" else "MAP", false) {
                showTestMap = !showTestMap
                mapActive = showTestMap
                course.visibility = if (showTestMap) View.GONE else View.VISIBLE
                map.view.visibility = if (showTestMap) View.VISIBLE else View.GONE
                mapBadge?.visibility = if (showTestMap) View.VISIBLE else View.GONE
                if (showTestMap) { startPhoneLocation(); map.view.post { map.draw() } } else stopPhoneLocation()
                locate.visibility = if (showTestMap) View.VISIBLE else View.GONE
                viewMode.text = if (showTestMap) "SIM" else "MAP"
            }.apply { visibility = View.GONE }
            // The switch also exposes the offline simulator without sending commands.
            viewMode.textSize = 12f
            bottom.addView(viewMode, LinearLayout.LayoutParams(dp(52), dp(36)).apply { rightMargin = dp(4) })
            val reset = button("Reset", false) {
                if (mapActive) {
                    route.reset(); map.reset(); updateRoute(); locatePhone()
                } else course.resetCourse()
            }.apply { contentDescription = "Clear route and choose new Home from next GPS fix"; visibility = View.GONE }
            bottom.addView(locate, LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(4) })
            reset.textSize = 12f
            bottom.addView(reset, LinearLayout.LayoutParams(dp(58), dp(36)).apply { rightMargin = dp(4) })
            val export = button("CSV", false) {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "telerc-route.csv")
                }
                startActivityForResult(intent, 43)
            }.apply { contentDescription = "Export recorded route and transmitted control commands as CSV"; visibility = View.GONE }
            export.textSize = 12f
            bottom.addView(export, LinearLayout.LayoutParams(dp(48), dp(36)))
            val music = button("♫", false) { }.apply {
                contentDescription = "Turn game music on or off; uses media volume"
                setOnClickListener {
                    isSelected = course.setMusic(!isSelected)
                    text = if (isSelected) "♫ ON" else "♫"
                }
            }
            musicButton = music
            modeButton.setOnClickListener {
                    testMode = !testMode
                    val checked = testMode
                    course.setMode(if (checked) TestDriveView.Mode.TEST else TestDriveView.Mode.GAME)
                    modeButton.text = if (checked) "Game" else "Test"
                    modeButton.contentDescription = if (checked) "Return to Game" else "Open Test"
                    music.visibility = if (checked) View.GONE else View.VISIBLE
                    if (checked) { music.isSelected = false; music.text = "♫" }
                    mapActive = checked && showTestMap
                    course.visibility = if (mapActive) View.GONE else View.VISIBLE
                    map.view.visibility = if (mapActive) View.VISIBLE else View.GONE
                    mapBadge?.visibility = if (mapActive) View.VISIBLE else View.GONE
                    locate.visibility = if (mapActive) View.VISIBLE else View.GONE
                    reset.visibility = if (checked) View.VISIBLE else View.GONE
                    export.visibility = if (checked) View.VISIBLE else View.GONE
                    viewMode.visibility = if (checked) View.VISIBLE else View.GONE
                    routeStatus?.visibility = if (checked) View.VISIBLE else View.GONE
                    if (mapActive) { startPhoneLocation(); map.view.post { map.draw() } } else stopPhoneLocation()
            }
            music.textSize = 12f
            bottom.addView(music, LinearLayout.LayoutParams(dp(52), dp(36)).apply { leftMargin = dp(4) })

            routeStatus?.visibility = View.GONE

        }
        row.addView(middle, LinearLayout.LayoutParams(0, -1, 5f).apply { rightMargin = dp(8) })
        val right = card().apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))

            addView(text("DRIVE", 16f, ink, true).apply { gravity = Gravity.CENTER })
            addView(text("CH2  ·  forward / reverse", 11f, muted).apply { gravity = Gravity.CENTER })
            val stick = JoystickView(this@MainActivity, true) { course.setDrive(it) }
            stick.isEnabled = true
            addView(stick, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val arm = armDisarmButton()
        row.addView(controlColumn(arm, right), LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        // Match Controls: equal side zones + a fixed center status. The left tool
        // strip may scroll only on unusually narrow displays; it never overlaps status.
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tools = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(bottom)
        }
        toolbar.addView(tools, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
        status?.apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        toolbar.addView(status, LinearLayout.LayoutParams(dp(156), -1))
        val rightSlot = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        for (label in listOf("F1", "F2", "F3")) {
            rightSlot.addView(servoAssignmentButton(label),
                LinearLayout.LayoutParams(dp(46), -1).apply { leftMargin = dp(5) })
        }
        toolbar.addView(rightSlot, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        setContentView(root)
        updateRoute()
    }
    private fun updateRoute() {
        routeMap?.draw()
        val prefs = getSharedPreferences("test_vehicle", MODE_PRIVATE)
        val vehicle = prefs.getString("name", "Rover") ?: "Rover"
        val maxSpeed = prefs.getFloat("max_m_s", 2.8f)
        val lastGps = route.rover.lastOrNull()?.timeMs
        val gpsAge = lastGps?.let {
            val seconds = ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 1000L)
            if (seconds < 60) "${seconds}s" else "${seconds / 60}m"
        }
        mapBadge?.text = when {
            page == Page.CONTROLS -> "${if (gpsAge == null) "NO GPS · ${if (route.home == null) "SAMPLE" else "PHONE HOME"}" else "LAST GPS $gpsAge AGO"} · " +
                if (controlEnabled.get()) "CYAN RC ESTIMATE" else "CYAN ESTIMATE FROZEN"
            route.rover.isEmpty() -> "$vehicle · SIM ${"%.1f".format(maxSpeed)} m/s · TAP TO EDIT"
            else -> "$vehicle · LIVE ROVER · TAP TO EDIT SIM"
        }
        routeStatus?.text = "HOME ${if (route.home == null) "GPS pending · SAMPLE MAP" else "GPS fixed"}  ·  " +
            "PHONE ${route.phone.size}  ·  ROVER ${route.rover.size}" +
            (route.rover.lastOrNull()?.headingDegrees?.let { " H ${it.toInt()}°" } ?: "") +
            "  ·  SENT ${route.commands.size}"
    }

    private fun editTestVehicle(course: TestDriveView, map: RouteMapView) {
        val prefs = getSharedPreferences("test_vehicle", MODE_PRIVATE)
        fun field(label: String, value: String, decimal: Boolean): EditText = EditText(this).apply {
            hint = label; setSingleLine(); setText(value); textSize = 15f
            inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val name = field("Vehicle name", prefs.getString("name", "Rover") ?: "Rover", false)
        val speed = field("Maximum speed (m/s)", prefs.getFloat("max_m_s", 2.8f).toString(), true)
        val turn = field("Pivot turn rate (°/s)", prefs.getFloat("turn_deg_s", 220f).toString(), true)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(4), dp(22), 0)
            addView(name); addView(speed); addView(turn)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Offline vehicle estimate")
            .setMessage("Simulated map movement only. No values are sent to the rover.")
            .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val label = name.text.toString().trim()
                val metersPerSecond = speed.text.toString().toFloatOrNull()
                val degreesPerSecond = turn.text.toString().toFloatOrNull()
                when {
                    label.isBlank() || label.length > 24 -> name.error = "Enter a name up to 24 characters"
                    metersPerSecond == null || !metersPerSecond.isFinite() || metersPerSecond !in .1f..30f ->
                        speed.error = "Enter 0.1–30 m/s"
                    degreesPerSecond == null || !degreesPerSecond.isFinite() || degreesPerSecond !in 10f..360f ->
                        turn.error = "Enter 10–360 °/s"
                    else -> {
                        prefs.edit().putString("name", label).putFloat("max_m_s", metersPerSecond)
                            .putFloat("turn_deg_s", degreesPerSecond).apply()
                        map.maxSpeedMetersPerSecond = metersPerSecond.toDouble()
                        course.maxYawRateDegrees = degreesPerSecond
                        map.resetPreview(); updateRoute(); dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun locatePhone() {
        if (!mapActive || !resumed) return
        if (controlEnabled.get() && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Stop control before requesting location permission", Toast.LENGTH_LONG).show()
            return
        }
        pendingPhoneLocate = true
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequested = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            if (controlEnabled.get()) {
                pendingPhoneLocate = false
                Toast.makeText(this, "Stop control before opening location settings", Toast.LENGTH_LONG).show()
                return
            }
            AlertDialog.Builder(this).setTitle("Phone location is off")
                .setMessage("Enable location services to center the map on your phone. Return to TeleRC after enabling it.")
                .setNegativeButton("Cancel") { _, _ -> pendingPhoneLocate = false }
                .setOnCancelListener { pendingPhoneLocate = false }
                .setPositiveButton("Open location settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.show()
            return
        }
        routeStatus?.text = "Acquiring current phone GPS fix…"
        Toast.makeText(this, "Acquiring current phone GPS fix", Toast.LENGTH_SHORT).show()
        startPhoneLocation()
    }
    private fun startPhoneLocation() {
        if (!resumed || !mapActive) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!locationPermissionRequested && page != Page.CONTROLS) {
                locationPermissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
            }
            routeStatus?.text = "Precise phone location required for Home"
            return
        }
        var subscribed = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, phoneListener)
                    locationManager.getLastKnownLocation(provider)?.let(phoneListener::onLocationChanged)
                    subscribed = true
                }
            } catch (_: Exception) { /* Keep the other location provider available. */ }
        }
        if (!subscribed) routeStatus?.text = "Enable phone location; map uses a sample start until GPS Home is set"
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (pendingPhoneLocate) locatePhone() else startPhoneLocation()
        } else if (requestCode == 42) {
            pendingPhoneLocate = false
            routeStatus?.text = "Precise GPS permission required for phone track"
            Toast.makeText(this, "Precise location permission is required", Toast.LENGTH_LONG).show()
        }
    }
    @Deprecated("Activity result used for the Android document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 43 && resultCode == RESULT_OK) {
            try {
                val uri = data?.data ?: return
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(route.encode()) }
                routeStatus?.text = "Route exported"
            } catch (_: Exception) { routeStatus?.text = "Could not export route" }
        }
    }
    private fun stopPhoneLocation() { try { locationManager.removeUpdates(phoneListener) } catch (_: Exception) {} }
    private fun releaseMap() {
        pendingPhoneLocate = false
        stopPhoneLocation(); mapActive = false
        routeMap?.let { if (resumed) it.onPause(); if (started) it.onStop(); it.onDestroy() }
        routeMap = null; routeStatus = null; mapBadge = null; musicButton = null
        controlsPlaceholder = null; controlsLocate = null; controlsEstimate = null; deadReckoning.reset()
    }
    private fun saveRoute() {
        try { openFileOutput("route-session.csv", MODE_PRIVATE).bufferedWriter().use { it.write(route.encode()) } }
        catch (_: Exception) { routeStatus?.text = "Could not save route locally" }
    }
    private fun linkFresh() = HeartbeatHealth.isFresh(target, heartbeatAt, SystemClock.elapsedRealtime())
    private fun linkDiagnosis(): String {
        if (!connected.get()) return if (lastLinkError.isNotBlank())
            "Link closed after $lastLinkError. Reconnect, then diagnose again if it repeats."
        else "Tap Connect first, wait a few seconds, then diagnose again."
        if (linkFresh()) {
            val commands = if (bridgeAccepted >= 0)
                " Bridge: accepted $bridgeAccepted, rejected $bridgeRejected, UART command TX $bridgeCommandBytes bytes."
            else " Bridge command counters unavailable; upload the current matching bridge sketch."
            val ack = if (lastArmAck.isNotBlank()) " Last ARM/DISARM result: $lastArmAck." else ""
            return "Verified rover heartbeat received. Link active; live driving still requires Enable Control." +
                commands + ack + " A forwarded command does not prove motor output or successful arming."
        }
        if (bridgeStatusAt == 0L || SystemClock.elapsedRealtime() - bridgeStatusAt > 7000)
            return "No recent ESP32 bridge status. Verify the matching bridge sketch, rover Wi-Fi, " +
                "bridge address/port, and the bridge USB Serial Monitor."
        if (bridgeRxBytes == 0L)
            return "ESP32 is reachable but receives zero F405 UART bytes. Check T3 → ESP GPIO18, shared GND, " +
                "SERIAL3_PROTOCOL=2 and SERIAL3_BAUD=115, then reboot the F405."
        if (bridgeFrames == 0L)
            return "ESP32 receives UART bytes ($bridgeRxBytes) but no complete MAVLink frames. Check 115200 baud and UART wiring."
        val age = if (heartbeatAt > 0) "Last verified heartbeat ${SystemClock.elapsedRealtime() - heartbeatAt} ms ago. "
            else "No verified autopilot heartbeat yet. "
        return age + "ESP32 UART RX: $bridgeRxBytes bytes / $bridgeFrames frames; accepted commands: " +
            "$bridgeAccepted, rejected: $bridgeRejected, UART command TX: $bridgeCommandBytes bytes." +
            (if (lastArmAck.isNotBlank()) " Last ARM/DISARM result: $lastArmAck." else "")
    }
    private fun refreshUi() {
        val now = SystemClock.elapsedRealtime()
        status?.text = when {
            page == Page.LORA && usbPermissionPending -> "USB PERMISSION PENDING"
            !connected.get() && wantsLink -> if (usbMode) "CONNECT USB LoRa BASE" else "RECONNECTING · CHECK ROVER WI-FI"
            !connected.get() -> "DISCONNECTED"
            page == Page.LORA && usbMode && target == 0 -> "USB CONNECTED · LOCAL SETUP"
            target == 0 -> "WAITING FOR HEARTBEAT"
            controlEnabled.get() && !HeartbeatHealth.isFresh(target, heartbeatAt, now) ->
                "SYSTEM $target  •  CONTROL PAUSED"
            !HeartbeatHealth.isRecentlySeen(target, heartbeatAt, now) -> "SYSTEM $target  •  HEARTBEAT LOST"
            controlEnabled.get() -> "SYSTEM $target  •  CONTROL ON"
            else -> "SYSTEM $target  •  LINK ACTIVE"
        }
        connect?.text = if (page == Page.LORA) {
            if (wantsLink) { if (usbMode) "Disconnect USB" else "Disconnect Wi-Fi" } else "Connect USB board"
        } else if (wantsLink) "Disconnect" else "Connect"
        enable?.isEnabled = connected.get() && (controlEnabled.get() || linkFresh())
        enable?.text = if (controlEnabled.get()) "STOP CONTROL" else "ENABLE CONTROL"
        val localUsb = usbMode && connected.get()
        loraRead?.isEnabled = localUsb && loraExchange.pending == null && !loraExchange.blocked
        loraSave?.isEnabled = localUsb && loraExchange.canSave && vehicleArmed != true
        val board = loraExchange.board
        loraOperate?.isEnabled = localUsb && board?.role == "BASE" && board.active &&
            linkFresh() && loraExchange.pending == null && !loraExchange.blocked && !loraExchange.restartRequired
        loraLinkStatus?.text = when {
            !localUsb && usbMode && wantsLink -> lastLinkError.ifBlank { "Waiting for USB permission or a native T3-S3 USB board." }
            !localUsb -> "Connect a local USB board first."
            loraExchange.blocked -> "Setup reply missing. Reconnect USB before retrying."
            loraExchange.restartRequired -> "Saved · restart this board, then provision or check its partner."
            loraExchange.pending != null -> "Waiting for local board reply…"
            board == null -> "USB connected · read board settings. Rover readiness is not confirmed."
            board.role == "ROVER" -> "ROVER attached · local setup only. Attach BASE to this phone for driving."
            !board.active -> "BASE radio inactive · save pairing values, then restart."
            !linkFresh() -> "BASE radio active · waiting for a fresh rover heartbeat. Check partner settings, UART and motor MCU."
            else -> "BASE connected · rover heartbeat fresh. Control remains disabled until you enable it."
        }
        refreshFunctionButtons()
    }
    private fun disableControl(releaseToReceiver: Boolean = false) {
        // State changes are immediate; UDP safety/handover packets run on the dedicated TX thread.
        val system = target
        val udp = socket
        val remote = endpoint
        val remotePort = endpointPort
        disableControlState()
        val epoch = synchronized(commandLock) { commandEpoch }
        if (system != 0 && udp != null && remote != null) {
            txExecutor.execute {
                try {
                    synchronized(commandLock) {
                        if (socket === udp && commandEpoch == epoch) {
                            sendControlHandoverBlocking(udp, remote, system, remotePort, releaseToReceiver)
                        }
                    }
                } catch (_: Exception) {
                    // ESP32 500 ms watchdog remains the final safety layer if phone TX fails.
                }
            }
        }
    }
    private fun requestUsbPermission(manager: UsbManager, device: android.hardware.usb.UsbDevice) {
        if (usbPermissionPending) return
        usbPermissionPending = true
        val action = "$packageName.USB_LORA_PERMISSION"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != action) return
                unregisterReceiver(this); usbPermissionReceiver = null; usbPermissionPending = false
                if (!wantsLink || !usbMode) return
                if (manager.hasPermission(device)) start()
                else {
                    lastLinkError = "USB permission denied. Tap Connect to request it again."
                    wantsLink = false
                    getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", false).apply()
                    refreshUi(); status?.text = "USB PERMISSION DENIED"
                }
            }
        }
        usbPermissionReceiver = receiver
        // API 26+ flag-bearing overload; the PendingIntent targets our own package.
        registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try {
            manager.requestPermission(device, PendingIntent.getBroadcast(this, 0,
                Intent(action).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE))
        } catch (error: Exception) {
            unregisterReceiver(receiver); usbPermissionReceiver = null; usbPermissionPending = false
            lastLinkError = "USB permission request failed. Reconnect the USB OTG cable and retry."
            status?.text = "USB PERMISSION REQUEST FAILED"
        }
    }
    private fun start() {
        if (connected.get()) return
        val saved = getSharedPreferences("link", MODE_PRIVATE)
        val address = if (usbMode) "127.0.0.1" else
            (host?.text?.toString() ?: saved.getString("host", "192.168.4.1")).orEmpty().trim()
        val valid = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) &&
            address.split('.').all { it.toInt() in 0..255 }
        val number = if (usbMode) 14550 else
            (port?.text?.toString() ?: saved.getInt("port", 14550).toString()).toIntOrNull()
        if (!valid || number == null || number !in 1..65535) {
            wantsLink = false
            saved.edit().putBoolean("auto_connect", false).apply()
            refreshUi(); status?.text = "INVALID ADDRESS OR PORT"; return
        }
        val remote = InetAddress.getByName(address)
        val udp: LinkTransport = if (usbMode) {
            val manager = getSystemService(USB_SERVICE) as UsbManager
            val devices = UsbLoRaTransport.candidates(manager)
            if (devices.size != 1) {
                lastLinkError = if (devices.isEmpty()) "No native T3-S3 USB board detected. Check OTG, data cable and firmware USB CDC mode."
                    else "Multiple USB boards detected. Connect only one T3-S3 for setup."
                refreshUi()
                status?.text = if (devices.isEmpty()) "CONNECT NATIVE T3-S3 USB OTG BOARD" else "CONNECT ONLY ONE LoRa USB BOARD"
                return
            }
            val device = devices.single()
            if (!manager.hasPermission(device)) { requestUsbPermission(manager, device); return }
            try { UsbLoRaTransport.open(manager, device) }
            catch (error: Exception) { lastLinkError = "USB open failed. Close other serial apps and reconnect the data cable."; refreshUi(); status?.text = "USB LoRa OPEN FAILED"; return }
        } else {
            saved.edit().putString("host", address).putInt("port", number).apply()
            val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifi = connectivity.allNetworks.firstOrNull {
                connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifi == null) { status?.text = "CONNECT PHONE TO ROVER WI-FI"; return }
            try {
                UdpTransport(DatagramSocket(number).also { transport ->
                    try { wifi.bindSocket(transport); transport.soTimeout = 50 }
                    catch (error: Exception) { transport.close(); throw error }
                })
            } catch (error: Exception) { status?.text = "WI-FI UDP PORT UNAVAILABLE"; return }
        }
        socket = udp; endpoint = remote; endpointPort = number
        reconnectHandler.removeCallbacks(loraTimeout); loraExchange.reset()
        loraInfo = if (usbMode) "USB connected. Read settings to identify BASE or ROVER." else "Disconnect Wi-Fi, then connect a USB board for setup."
        loraStatus?.text = loraInfo
        target = 0; vehicleArmed = null; lastArmAck = ""; lastLinkError = ""; heartbeatAt = 0; bridgeStatusAt = 0; bridgeRxBytes = 0; bridgeFrames = 0
        bridgeAccepted = -1; bridgeRejected = -1; bridgeCommandBytes = -1
        disableControl(); connected.set(true)
        host?.isEnabled = false; port?.isEnabled = false; refreshUi()
        if (usbMode && page == Page.LORA && loraExchange.beginRead()) sendLoRaAdmin("TELERC_LORA_GET_V1")
        thread(name = "telerc-link") {
            val input = ByteArray(512); var sequence = 0; var lastSend = 0L; var lastDiscovery = 0L; var lastUiRefresh = 0L
            val discovery = "TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII)
            while (connected.get() && socket === udp) {
                revokeExpiredControl(udp)
                // Do not tear down the socket because Android temporarily changes
                // capabilities on the captured Network object. The ESP32 data path
                // is authoritative: only an actual UDP failure or explicit
                // Disconnect tears down the transport.
                val discoveryNow = SystemClock.elapsedRealtime()
                if (discoveryNow - lastDiscovery >= 1000) {
                    try {
                        udp.send(DatagramPacket(discovery, discovery.size, remote, number))
                        lastDiscovery = discoveryNow
                    } catch (e: Exception) {
                        lastLinkError = "discovery send " + e.javaClass.simpleName
                        break
                    }
                }
                try {
                    val packet = DatagramPacket(input, input.size)
                    udp.receive(packet)
                    if (socket !== udp || !connected.get()) break
                    // Inspect the old heartbeat before accepting a recovery heartbeat.
                    revokeExpiredControl(udp)
                    if (packet.address == remote && packet.port == number) {
                        val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                        if (usbMode && payload.size <= 280 && String(payload, Charsets.US_ASCII).startsWith("TELERC_LORA_")) {
                            val reply = String(payload, Charsets.US_ASCII)
                            runOnUiThread {
                                if (socket === udp) {
                                    val wasPending = loraExchange.pending
                                    if (wasPending != null && !loraExchange.blocked) {
                                        val saved = loraExchange.receive(reply)
                                        if (loraExchange.pending == null) {
                                            reconnectHandler.removeCallbacks(loraTimeout)
                                            saved?.let {
                                                getSharedPreferences("lora_pair", MODE_PRIVATE).edit()
                                                    .putString("mhz", it.mhz).putString("power", it.power).putString("key", it.key).apply()
                                            }
                                            loraInfo = LoRaSetup.describe(reply)
                                            loraStatus?.text = loraInfo; refreshUi()
                                        }
                                    }
                                }
                            }
                        } else if (payload.size in 20..160 && payload.take(17).toByteArray()
                                .contentEquals("TELERC_STATUS_V1,".toByteArray(Charsets.US_ASCII))) {
                            val fields = String(payload, Charsets.US_ASCII).split(',')
                            val bytes = fields.getOrNull(1)?.toLongOrNull()
                            val frames = fields.getOrNull(2)?.toLongOrNull()
                            if (fields.size in listOf(3, 4, 7) && bytes != null && frames != null &&
                                bytes >= 0 && frames >= 0 && frames <= bytes) {
                                bridgeRxBytes = bytes; bridgeFrames = frames
                                bridgeStatusAt = SystemClock.elapsedRealtime()
                                if (fields.size == 7) {
                                    val accepted = fields[3].toLongOrNull()
                                    val rejected = fields[4].toLongOrNull()
                                    val txBytes = fields[5].toLongOrNull()
                                    val armCount = fields[6].toLongOrNull()
                                    if (listOf(accepted, rejected, txBytes, armCount).all { it != null && it >= 0 }) {
                                        bridgeAccepted = accepted!!; bridgeRejected = rejected!!; bridgeCommandBytes = txBytes!!
                                    }
                                }
                            }
                        } else for (frame in Mavlink.frames(payload)) {
                            val heartbeat = Mavlink.heartbeat(frame)
                            if (heartbeat != null && (target == 0 || target == heartbeat.system)) {
                                val previousSystem = target
                                val previousArmed = vehicleArmed
                                target = heartbeat.system
                                vehicleArmed = heartbeat.armed
                                heartbeatAt = SystemClock.elapsedRealtime()
                                val requestedArmState = pendingArmState
                                val armStateConfirmed = requestedArmState != null && heartbeat.armed == requestedArmState
                                if (armStateConfirmed) {
                                    pendingArmState = null
                                    lastArmAck = if (heartbeat.armed) "ARMED CONFIRMED" else "DISARMED CONFIRMED"
                                }
                                // Heartbeat is authoritative for the final armed state.
                                if (previousSystem != heartbeat.system || previousArmed != heartbeat.armed || armStateConfirmed) {
                                    runOnUiThread {
                                        if (socket === udp) {
                                            if (armStateConfirmed) {
                                                android.widget.Toast.makeText(
                                                    this@MainActivity,
                                                    if (heartbeat.armed) "Rover ARMED · confirmed by heartbeat"
                                                    else "Rover DISARMED · confirmed by heartbeat",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            refreshUi()
                                        }
                                    }
                                }
                            }
                            val ack = Mavlink.commandAck(frame)
                            if (ack != null && ack.system == target && ack.command == 400) {
                                lastArmAck = ack.resultText
                                runOnUiThread {
                                    if (socket === udp) {
                                        val message = if (ack.result == 0)
                                            "ARM/DISARM command accepted · waiting for rover state"
                                        else
                                            "ARM/DISARM " + ack.resultText
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            message,
                                            if (ack.result == 0) android.widget.Toast.LENGTH_SHORT
                                            else android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        refreshUi()
                                    }
                                }
                            }
                            val position = Mavlink.globalPosition(frame)
                            if (position != null && position.system == target && target != 0 && linkFresh()) {
                                runOnUiThread {
                                    if (socket === udp) {
                                        val point = TrackPoint(position.latitude, position.longitude,
                                            System.currentTimeMillis(), position.headingDegrees)
                                        if (route.addRover(point)) {
                                            if (routeMap?.anchorToRoverIfWaiting(point) == true) deadReckoning.reset()
                                            updateRoute()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    lastLinkError = "UDP receive " + e.javaClass.simpleName
                    break
                }
                val now = SystemClock.elapsedRealtime()
                // Never convert packet silence into a disconnect. Android may suspend
                // this Activity/thread while another app is foreground, making elapsed
                // time jump when TeleRC resumes. Keep the bound UDP socket and discovery
                // loop alive; only an actual socket/send/receive failure or explicit
                // Disconnect is allowed to tear down the transport.
                val fresh = HeartbeatHealth.isFresh(target, heartbeatAt, now)
                if (now - lastSend >= 100 && fresh && controlEnabled.get()) {
                    try {
                        val rc = synchronized(commandLock) {
                            if (!controlEnabled.get() || !linkFresh()) null else {
                                val channels = RoverControls.channels(steering, drive)
                                val bytes = Mavlink.override(sequence++, target, 1,
                                    channels.one, channels.two, channels.three, channels.four)
                                udp.send(DatagramPacket(bytes, bytes.size, remote, number))
                                lastSend = now
                                channels
                            }
                        } ?: continue
                        val sent = ControlSample(System.currentTimeMillis(), rc.one, rc.two)
                        runOnUiThread {
                            if (socket === udp) {
                                route.addCommand(sent)
                                if (page == Page.CONTROLS && mapActive && controlEnabled.get()) {
                                    val pose = deadReckoning.accept(now, rc.one, rc.two)
                                    routeMap?.updatePreview(pose)
                                    val metersPerUnit = (routeMap?.maxSpeedMetersPerSecond ?: 2.8) / .7
                                    val distance = kotlin.math.hypot((pose.x - .5f).toDouble(),
                                        (pose.y - .76f).toDouble()) * metersPerUnit
                                    val heading = (Math.toDegrees(pose.heading.toDouble()) + 360.0) % 360.0
                                    controlsEstimate?.text = "DISP ${"%.1f".format(distance)} m · H ${heading.toInt()}° · " +
                                        "${"%.1f".format(kotlin.math.abs(pose.speed) * metersPerUnit)} m/s"
                                }
                                // A sent frame changes only the cyan estimate; avoid redrawing GPS layers at 10 Hz.
                            }
                        }
                    } catch (e: Exception) {
                        lastLinkError = "control send " + e.javaClass.simpleName
                        break
                    }
                }
                if (now - lastUiRefresh >= 250) {
                    lastUiRefresh = now
                    runOnUiThread {
                        if (socket === udp && connected.get()) {
                            refreshUi()
                        }
                    }
                }
            }
            runOnUiThread { if (socket === udp) { stop(); scheduleReconnect() } }
        }
    }
    private fun scheduleReconnect() {
        reconnectHandler.removeCallbacks(reconnect)
        if (resumed && wantsLink) reconnectHandler.postDelayed(reconnect, 2000)
    }
    private fun revokeExpiredControl(transport: LinkTransport) {
        val expired = synchronized(commandLock) {
            if (socket !== transport || !controlEnabled.expire(target, heartbeatAt, SystemClock.elapsedRealtime())) false
            else {
                commandEpoch++
                steering = 1500
                drive = 1500
                true
            }
        }
        if (expired) runOnUiThread {
            if (socket === transport && !controlEnabled.get()) {
                disableControl()
                controlsEstimate?.text = "RC STOPPED · heartbeat lost · enable again when ready"
            }
        }
    }
    private fun stop(explicitDisconnect: Boolean = false) {
        val udp = socket
        val remote = endpoint
        val system = target
        val remotePort = endpointPort
        disableControlState()
        connected.set(false)
        socket = null
        hideLoRaKey()
        reconnectHandler.removeCallbacks(loraTimeout); loraExchange.reset()
        loraInfo = "Attach one T3-S3 by native USB, connect, then read board settings."
        loraStatus?.text = loraInfo
        endpoint = null
        target = 0
        vehicleArmed = null
        pendingArmState = null
        lastArmAck = ""
        heartbeatAt = 0
        bridgeStatusAt = 0
        bridgeRxBytes = 0
        bridgeFrames = 0
        bridgeAccepted = -1
        bridgeRejected = -1
        bridgeCommandBytes = -1
        host?.isEnabled = true
        port?.isEnabled = true
        refreshUi()

        if (explicitDisconnect && udp != null && remote != null && system != 0) {
            txExecutor.execute {
                try {
                    // Neutralize first. The bridge owns the actual receiver-release
                    // transition when it receives TELERC_DISCONNECT_V1, avoiding two
                    // independent handover sequences racing each other.
                    sendControlHandoverBlocking(udp, remote, system, remotePort, releaseToReceiver = false)
                    val message = "TELERC_DISCONNECT_V1".toByteArray(Charsets.US_ASCII)
                    repeat(3) { udp.send(DatagramPacket(message, message.size, remote, remotePort)) }
                } catch (_: Exception) {
                    // If the explicit message cannot be delivered, the bridge watchdog
                    // remains in neutral hold instead of guessing that handover succeeded.
                } finally {
                    udp.close()
                }
            }
        } else {
            udp?.close()
        }
    }
    override fun onPause() { hideLoRaKey(); resumed = false; reconnectHandler.removeCallbacks(reconnect); disableControl(); stopPhoneLocation(); routeMap?.onPause(); testCourse?.stop(); musicButton?.apply { isSelected = false; text = "♫" }; saveRoute(); super.onPause() }
    override fun onResume() { super.onResume(); resumed = true; routeMap?.onResume(); startPhoneLocation(); testCourse?.resume(); scheduleReconnect(); if (::updater.isInitialized) updater.resumePendingInstall() }
    override fun onStart() { super.onStart(); started = true; routeMap?.onStart() }
    override fun onStop() { started = false; routeMap?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); routeMap?.onLowMemory() }
    override fun onDestroy() { usbPermissionReceiver?.let { unregisterReceiver(it) }; usbPermissionReceiver = null; usbPermissionPending = false; wantsLink = false; reconnectHandler.removeCallbacks(reconnect); releaseMap(); stop(explicitDisconnect = true); saveRoute(); updater.close(); txExecutor.shutdown(); super.onDestroy() }
}
