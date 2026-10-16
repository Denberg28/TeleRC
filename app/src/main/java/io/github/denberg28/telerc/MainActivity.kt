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
    private enum class Page { SETUP, CONTROLS, TEST }
    private var page = Page.SETUP
    private var mazePractice: MazePractice? = null
    private var mazeView: MazePracticeView? = null
    private var practiceSteer: JoystickView? = null
    private var practiceDrive: JoystickView? = null
    @Volatile private var socket: LinkTransport? = null
    private val usbMode get() = getSharedPreferences("link", MODE_PRIVATE).getString("transport", "wifi") == "lora_usb"
    private val miniRoverMode get() = getSharedPreferences("link", MODE_PRIVATE).getString("transport", "wifi") == MiniRoverWifi.TRANSPORT
    private val miniArmGate = MiniRoverArmGate()
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
            if (!resumed || !wantsLink || page == Page.TEST) return
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
    private val route = RouteSession()
    private var routeMap: RouteMapView? = null
    private var routeStatus: TextView? = null
    private var mapBadge: TextView? = null
    private var fpvCamera: UsbFpvView? = null
    private var fpvSwap: Button? = null
    private var fpvConnect: Button? = null
    private var showingCamera = false
    private var themeBitmap: android.graphics.Bitmap? = null
    private var currentShell: LinearLayout? = null
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private var imageWork = false
    private var pendingFpvPermission = false
    private var connectFpvOnResume = false
    private var controlsPlaceholder: View? = null
    private var controlsLocate: Button? = null
    private var controlsEstimate: TextView? = null
    private val deadReckoning = DeadReckoning()
    private var mapActive = false
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
    private val darkTheme get() = AppColors.theme(this) == BackgroundTheme.DARK
    private val ink get() = AppColors.ink(this)
    private val muted get() = AppColors.muted(this)
    private val accent get() = AppColors.accent(this)
    private val pale get() = AppColors.background(this)
    private val surface get() = AppColors.surface(this)
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
        stateListAnimator = null; elevation = 0f
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf((accent and 0x00ffffff) or 0x24000000),
            shape(if (filled) AppColors.buttonFill(this@MainActivity) else softAccent, 10),
            shape(Color.WHITE, 10))
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape(surface, 14).apply { setStroke(dp(1), AppColors.outline(this@MainActivity)) }; elevation = 0f
    }
    private fun LinearLayout.addCard(view: View) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AppColors.migrate(this)
        setTheme(if (darkTheme) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        ControlResponseStore.migrate(this)
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
            "TEST" -> Page.TEST
            "CONTROLS" -> Page.CONTROLS
            else -> Page.SETUP
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (page == Page.TEST) goOfflineForPractice()
        render()
        imageExecutor.execute {
            val loaded = try { ThemeImageStore.load(applicationContext) } catch (_: Exception) { null }
            runOnUiThread { if (!isDestroyed) { themeBitmap = loaded; applyThemeImage() } }
        }
        handleFpvAttachment(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFpvAttachment(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleFpvAttachment(attachment: Intent?) {
        if (attachment?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val supplied = attachment.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
        // Resolve against the current bus; never trust an exported Activity's extras alone.
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val device = manager.deviceList[supplied.deviceName] ?: return
        if (device.deviceId != supplied.deviceId || !UsbFpvView.isVideoDevice(device)) return
        // Consume this launch so a later theme/process recreation cannot replay it.
        setIntent(Intent(attachment).apply { action = Intent.ACTION_MAIN; removeExtra(UsbManager.EXTRA_DEVICE) })
        disableControl()
        switchTo(Page.CONTROLS)
        showingCamera = true
        updateControlsScene()
        if (resumed) connectFpv() else connectFpvOnResume = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name); super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); disableControl(); releaseMap(); render()
    }
    private fun switchTo(next: Page) {
        if (page == next) return
        disableControl()
        releaseMap()
        page = next
        if (next == Page.TEST) goOfflineForPractice()
        val orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        requestedOrientation = orientation
        render()
    }
    private fun shell(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        currentShell = this
        background = themeBitmap?.let { ThemeImageDrawable(it, pale) } ?: android.graphics.drawable.ColorDrawable(pale)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnApplyWindowInsetsListener { view, insets ->
            val left: Int; val top: Int; val right: Int; val bottom: Int
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom
            } else {
                left = insets.systemWindowInsetLeft; top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight; bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(dp(8) + left, dp(6) + top, dp(8) + right, dp(6) + bottom)
            insets
        }
    }
    private fun pageDropdown(label: String): Button {
        lateinit var anchor: Button
        anchor = button("$label ▾", true) {
            PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Setup")
                menu.add(0, 2, 1, "Controls")
                menu.add(0, 3, 2, "Test")
                setOnMenuItemClickListener { item ->
                    switchTo(when (item.itemId) { 2 -> Page.CONTROLS; 3 -> Page.TEST; else -> Page.SETUP })
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
        mazeView?.pause(); mazeView = null
        practiceSteer?.reset(); practiceDrive?.reset()
        practiceSteer = null; practiceDrive = null
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
            Page.TEST -> renderTest()
        }
        refreshUi()
    }
    private fun armCaption(): String = when (vehicleArmed) {
        true -> "DISARM"
        false -> if (!miniRoverMode) "ARM" else when {
            !controlEnabled.get() -> "ENABLE FIRST"
            !miniArmGate.ready(SystemClock.elapsedRealtime()) -> "ARM · CENTER"
            else -> "ARM · READY"
        }
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
            miniArmGate.reset()
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
        val mini = miniRoverMode
        if (mini && !armed && (!controlEnabled.get() || steering !in 1475..1525 ||
                drive !in 1475..1525 || !miniArmGate.ready(SystemClock.elapsedRealtime()))) {
            android.widget.Toast.makeText(this,
                "Enable control, center both sticks and wait for ARM · READY. Use the matching standalone Wi-Fi mini rover sketch.",
                android.widget.Toast.LENGTH_LONG).show()
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
                                (mini && (!controlEnabled.get() || steering !in 1475..1525 ||
                                    drive !in 1475..1525 || !miniArmGate.ready(SystemClock.elapsedRealtime()))) ||
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
        val expiresAt = SystemClock.elapsedRealtime() + 3000
        refreshUi()
        txExecutor.execute {
            try {
                synchronized(commandLock) {
                    if (socket !== link || SystemClock.elapsedRealtime() >= expiresAt) return@execute
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

    private fun renderSetup() {
        enable = null; steeringStick = null; driveStick = null
        val root = shell()
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        val scale = maxOf(1f, resources.configuration.fontScale)
        fun height(value: Int) = dp((value * scale).toInt())
        val viewport = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val centered = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        centered.addView(panel, LinearLayout.LayoutParams(-1, -2))
        centered.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val width = minOf(dp(740), view.width)
            if (width > 0 && panel.layoutParams.width != width)
                panel.layoutParams = (panel.layoutParams as LinearLayout.LayoutParams).apply { this.width = width }
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(pageDropdown("Setup"), LinearLayout.LayoutParams(dp(112), -1))
        header.addView(text("TeleRC ${BuildConfig.VERSION_NAME}", 12f, muted), LinearLayout.LayoutParams(0, -1, 1f))
        header.addView(button("Controls ›", false) { switchTo(Page.CONTROLS) }, LinearLayout.LayoutParams(dp(112), -1))
        panel.addView(header, LinearLayout.LayoutParams(-1, height(40)).apply { bottomMargin = dp(8) })

        val wide = resources.configuration.screenWidthDp >= 600
        val body = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val connection = card().apply { setPadding(dp(10), dp(10), dp(10), dp(10)); contentDescription = "Connection settings" }
        val preferences = card().apply { setPadding(dp(10), dp(10), dp(10), dp(10)); contentDescription = "Preferences" }
        fun row(column: LinearLayout, view: View, size: Int = 40) {
            column.addView(view, LinearLayout.LayoutParams(-1, height(size)).apply {
                if (column.childCount > 0) topMargin = dp(6)
            })
        }
        row(connection, button(if (usbMode) "LoRa USB ▾" else if (miniRoverMode) "Mini rover Wi-Fi ▾" else "Wi-Fi bridge ▾", false) {
            if (!wantsLink) AlertDialog.Builder(this).setTitle("Control link")
                .setItems(arrayOf("Mini rover Wi-Fi", "Wi-Fi bridge / flight controller", "LoRa via USB base")) { _, choice ->
                    getSharedPreferences("link", MODE_PRIVATE).edit()
                        .putString("transport", when (choice) { 0 -> MiniRoverWifi.TRANSPORT; 2 -> "lora_usb"; else -> "wifi" }).apply()
                    render()
                }.show()
            else Toast.makeText(this, "Disconnect before changing the control link.", Toast.LENGTH_SHORT).show()
        })
        host = EditText(this).apply {
            setSingleLine(); hint = "Bridge IPv4"; setTextColor(ink); setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT; textSize = 14f
            background = shape(pale, 10); setPadding(dp(10), 0, dp(10), 0)
            setText(if (connected.get()) endpoint?.hostAddress else
                getSharedPreferences("link", MODE_PRIVATE).getString("host", "192.168.4.1"))
            isEnabled = !connected.get(); contentDescription = "Bridge IPv4 address"
        }
        port = EditText(this).apply {
            setSingleLine(); hint = "UDP port"; setTextColor(ink); setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER; textSize = 14f
            background = shape(pale, 10); setPadding(dp(10), 0, dp(10), 0)
            setText((if (connected.get()) endpointPort else getSharedPreferences("link", MODE_PRIVATE).getInt("port", 14550)).toString())
            isEnabled = !connected.get(); contentDescription = "UDP port"
        }
        val endpointRow = LinearLayout(this)
        endpointRow.addView(host, LinearLayout.LayoutParams(0, -1, 2f).apply { rightMargin = dp(6) })
        endpointRow.addView(port, LinearLayout.LayoutParams(0, -1, 1f))
        row(connection, when {
            usbMode -> text("USB OTG → T3-S3 base → LoRa", 12f, muted)
            miniRoverMode -> button("Wi-Fi setup", false) { showMiniRoverSetup() }
            else -> endpointRow
        })
        connect = button("Connect") {
            if (wantsLink) {
                wantsLink = false
                getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", false).apply()
                reconnectHandler.removeCallbacks(reconnect); stop(explicitDisconnect = true)
            } else {
                wantsLink = true
                getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", true).apply()
                start(); scheduleReconnect()
            }
        }
        row(connection, connect!!)
        status = text("DISCONNECTED", 12f, ink, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = "Rover link status"
        }
        row(connection, status!!, 32)
        row(connection, button("Diagnose link", false) {
            AlertDialog.Builder(this).setTitle("Link diagnostics")
                .setMessage(linkDiagnosis()).setPositiveButton("OK", null)
                .setNeutralButton("Export route") { _, _ ->
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"
                        putExtra(Intent.EXTRA_TITLE, "telerc-route.csv")
                    }, 43)
                }.show()
        }, 36)

        row(preferences, button("Background · ${if (ThemeImageStore.exists(this)) "Photo / " else ""}${AppColors.theme(this).label} ▾", false) {
            if (imageWork) return@button
            val dialog = AlertDialog.Builder(this).setTitle("Background")
                .setSingleChoiceItems(BackgroundTheme.entries.map { it.label }.toTypedArray(), AppColors.theme(this).ordinal) { d, index ->
                    disableControl()
                    val selectedTheme = BackgroundTheme.entries[index]
                    appearance.edit().putString("background", selectedTheme.name).putBoolean("dark", selectedTheme == BackgroundTheme.DARK).apply()
                    d.dismiss(); recreate()
                }.setNeutralButton("Choose image") { _, _ -> chooseThemeImage() }
                .setNegativeButton("Cancel", null)
            if (ThemeImageStore.exists(this)) dialog.setPositiveButton("Remove image") { _, _ ->
                if (!imageWork) {
                    disableControl(); imageWork = true
                    imageExecutor.execute {
                        ThemeImageStore.remove(applicationContext)
                        runOnUiThread { imageWork = false; if (!isDestroyed) { themeBitmap = null; render() } }
                    }
                }
            }
            dialog.show()
        }.apply { contentDescription = "Choose Light, Sepia or Dark background" })
        val choices = LinearLayout(this)
        val selected = appearance.getString("accent", "Blue")
        AppColors.names.forEachIndexed { index, name ->
            choices.addView(button(if (selected == name) "✓ ${AppColors.label(name)}" else AppColors.label(name), selected == name) {
                appearance.edit().putString("accent", name).apply(); render()
            }.apply { textSize = 12f; contentDescription = "${AppColors.label(name)} accent${if (selected == name) ", selected" else ""}" },
                LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) leftMargin = dp(4) })
        }
        row(preferences, choices)
        row(preferences, button("Control response ›", false) { showControlResponse() })
        val response = ControlResponseStore.read(this)
        row(preferences, text("Steer ${response.steeringPercent}% · Drive ${response.drivePercent}% · Expo ${response.expoPercent}%", 12f, muted), 32)
        row(preferences, button("Check for updates", false) { updater.check() }, 36)
        body.addView(connection, LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f).apply {
            if (wide) rightMargin = dp(8) else bottomMargin = dp(8)
        })
        body.addView(preferences, LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f))
        panel.addView(body, LinearLayout.LayoutParams(-1, -2))
        updateStatus = text("", 11f, muted).apply { maxLines = 2 }
        panel.addView(updateStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        viewport.addView(centered, FrameLayout.LayoutParams(-1, -2))
        root.addView(viewport, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun showControlResponse() {
        // Never edit gains while a live command is held. Setup is the only entry.
        disableControl()
        var draft = ControlResponseStore.read(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun slider(name: String, minimum: Int, maximum: Int, initial: Int, update: (Int) -> Unit) {
            val label = text("$name · $initial%", 14f, ink, true)
            content.addView(label, LinearLayout.LayoutParams(-1, -2))
            content.addView(SeekBar(this).apply {
                max = maximum - minimum; progress = initial - minimum
                contentDescription = "$name percentage"
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
                thumbTintList = android.content.res.ColorStateList.valueOf(accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        label.text = "$name · ${value + minimum}%"
                        if (fromUser) update(value + minimum)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        slider("Steering sensitivity", 25, 100, draft.steeringPercent) { draft = draft.copy(steeringPercent = it) }
        slider("Drive sensitivity", 25, 100, draft.drivePercent) { draft = draft.copy(drivePercent = it) }
        slider("Expo", 0, 100, draft.expoPercent) { draft = draft.copy(expoPercent = it) }
        content.addView(text("Sensitivity limits each axis. Expo softens both axes near center: 0% linear, 100% softest. Full-stick range stays the same. Applies to live controls and offline Test practice.", 12f, muted))
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("Control response").setView(scroll)
            .setPositiveButton("Apply") { _, _ -> ControlResponseStore.save(this, draft); render() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun goOfflineForPractice() {
        wantsLink = false
        reconnectHandler.removeCallbacks(reconnect)
        getSharedPreferences("link", MODE_PRIVATE).edit().putBoolean("auto_connect", false).apply()
        // Existing handover sends neutral and disconnect on the captured session, then closes it.
        // All future connection attempts (including late USB grants) are rejected on TEST.
        stop(explicitDisconnect = true)
    }

    private fun renderTest() {
        host=null; port=null; connect=null; status=null; enable=null
        steeringStick=null; driveStick=null
        val prefs=getSharedPreferences("maze_practice",MODE_PRIVATE)
        val practice=mazePractice ?: MazePractice(MazeLayout.generate(prefs.getInt("seed",kotlin.random.Random.nextInt())))
            .also { mazePractice=it; prefs.edit().putInt("seed",it.maze.seed).apply() }
        val root=shell()
        val scene=MazePracticeView(this,practice)
        mazeView=scene
        val timer=text("",14f,ink,true)
        val detail=text("",10f,muted)
        val start=button("Start practice") {}
        fun update() {
            timer.text="${if (practice.state==MazePractice.State.CLEARED) "CLEARED" else "TIME"} · ${MazeRecords.time(practice.elapsedMillis)}"
            val records=MazeRecords.decode(prefs.getString("records","").orEmpty())
            val best=records.filter { it.seed==practice.maze.seed && it.courseVersion==MazeLayout.COURSE_VERSION }.minOfOrNull { it.millis }
            val heading=((Math.toDegrees(practice.pose.heading.toDouble())+360)%360).toInt()
            detail.text="Maze ${practice.maze.seed.toUInt().toString(16)} · Best ${best?.let { MazeRecords.time(it) } ?: "—"} · H ${heading}°"
            start.text=when(practice.state) {
                MazePractice.State.READY -> "Start practice"
                MazePractice.State.RUNNING -> "Pause"
                MazePractice.State.PAUSED -> "Resume"
                MazePractice.State.CLEARED -> "Cleared ✓"
            }
            start.isEnabled=practice.state!=MazePractice.State.CLEARED
            val running=practice.state==MazePractice.State.RUNNING
            practiceSteer?.isEnabled=running; practiceDrive?.isEnabled=running
        }
        fun resetSticks() { practiceSteer?.reset(); practiceDrive?.reset() }
        scene.onUpdate=::update
        scene.onClear={ millis ->
            val records=MazeRecords.add(MazeRecords.decode(prefs.getString("records","").orEmpty()),
                MazeClear(practice.maze.seed,millis,System.currentTimeMillis(),MazeLayout.COURSE_VERSION))
            prefs.edit().putString("records",MazeRecords.encode(records)).apply()
            update()
        }
        fun control(title:String,hint:String,vertical:Boolean,changed:(Int)->Unit): LinearLayout {
            return card().apply {
                setPadding(dp(8),dp(8),dp(8),dp(8))
                addView(text(title,13f,ink,true))
                val feedback=text("$hint · 1500",11f,muted)
                addView(feedback)
                val stick=JoystickView(this@MainActivity,vertical) { value ->
                    changed(value); feedback.text="$hint · $value"
                }
                if(vertical) practiceDrive=stick else practiceSteer=stick
                addView(stick,LinearLayout.LayoutParams(-1,0,1f))
            }
        }
        val steerPanel=control("STEER","CH1 · left / right",false,practice::steering)
        val drivePanel=control("DRIVE","CH2 · forward / reverse",true,practice::drive)
        val center=card().apply {
            setPadding(dp(8),dp(8),dp(8),dp(8))
            addView(timer,LinearLayout.LayoutParams(-1,dp(22)))
            addView(detail,LinearLayout.LayoutParams(-1,dp(18)))
            addView(scene,LinearLayout.LayoutParams(-1,0,1f))
            val actions=LinearLayout(this@MainActivity)
            actions.addView(button("Shuffle maze",false) {
                scene.pause(); resetSticks()
                var seed=kotlin.random.Random.nextInt()
                while(seed==practice.maze.seed) seed=kotlin.random.Random.nextInt()
                mazePractice=MazePractice(MazeLayout.generate(seed))
                prefs.edit().putInt("seed",seed).apply()
                render()
            }.apply { textSize=12f },LinearLayout.LayoutParams(0,-1,1f))
            actions.addView(button("Retry maze",false) {
                resetSticks(); practice.reset(); scene.changed()
            }.apply { textSize=12f },LinearLayout.LayoutParams(0,-1,1f).apply { leftMargin=dp(4) })
            addView(actions,LinearLayout.LayoutParams(-1,dp(36)).apply { topMargin=dp(4) })
        }
        val offline=text("OFFLINE",13f,accent,true).apply {
            background=shape(softAccent,10)
            contentDescription="Offline practice, rover disconnected"
        }
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        row.addView(controlColumn(pageDropdown("Test"),steerPanel),LinearLayout.LayoutParams(0,-1,1.6f).apply { rightMargin=dp(8) })
        row.addView(center,LinearLayout.LayoutParams(0,-1,3.8f).apply { rightMargin=dp(8) })
        row.addView(controlColumn(offline,drivePanel),LinearLayout.LayoutParams(0,-1,1.6f))
        root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        start.textSize=13f
        start.setOnClickListener {
            resetSticks()
            if(practice.state==MazePractice.State.RUNNING) scene.pause() else practice.start()
            scene.changed()
        }
        footer.addView(start,LinearLayout.LayoutParams(0,-1,1f))
        footer.addView(text("OFFLINE · START → END",11f,muted,true),LinearLayout.LayoutParams(0,-1,1.5f))
        footer.addView(button("Clear times",false) {
            scene.pause(); resetSticks()
            val records=MazeRecords.decode(prefs.getString("records","").orEmpty())
            val message=if(records.isEmpty()) "No clears yet. Drive from START to END to save your time."
                else records.take(20).joinToString("\n\n") { record ->
                    val date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(record.at))
                    "${MazeRecords.time(record.millis)} · Maze ${record.seed.toUInt().toString(16)}${if(record.courseVersion==1) " · Classic" else ""}\n$date"
                }
            AlertDialog.Builder(this).setTitle("Clear times · latest ${records.size}")
                .setMessage("$message\n\nTimes count active practice only. Up to 50 clears are saved; compare best times on the same maze.")
                .setPositiveButton("Done",null).show()
        }.apply { textSize=13f },LinearLayout.LayoutParams(0,-1,1f))
        root.addView(footer,LinearLayout.LayoutParams(-1,dp(42)).apply { topMargin=dp(8) })
        update(); setContentView(root)
    }

    private fun renderControls() {
        host = null; port = null; connect = null
        val root = shell()
        status = text("DISCONNECTED", 12f, accent, true).apply { gravity = Gravity.CENTER }
        val pages = pageDropdown("Controls")
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
            fpvCamera = UsbFpvView(this@MainActivity) { disableControl() }.apply { visibility = View.GONE }
            scene.addView(fpvCamera, FrameLayout.LayoutParams(-1, -1))
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
            val viewButtons = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            fpvSwap = button("Show camera", false) {
                showingCamera = !showingCamera
                if (!showingCamera && !mapActive) showRecoveryMap() else updateControlsScene()
            }.apply { textSize = 12f; contentDescription = "Swap map and FPV camera view" }
            fpvConnect = button("Connect FPV", false) { connectFpv() }.apply {
                textSize = 12f
                contentDescription = "Connect FPV; hold for USB receiver diagnostics"
                setOnLongClickListener { showFpvOptions(); true }
            }
            viewButtons.addView(fpvSwap, LinearLayout.LayoutParams(0, -1, 1f))
            viewButtons.addView(fpvConnect, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(4) })
            addView(viewButtons, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
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
                    if (miniRoverMode) "Join TeleRC-MiniRover Wi-Fi, tap Connect and wait for the rover heartbeat."
                    else "Waiting for rover heartbeat. Check bridge Wi-Fi, UART RX/TX and SERIAL3 settings.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
        row.addView(controlColumn(pages, steerPanel), LinearLayout.LayoutParams(0, -1, 1.6f).apply { rightMargin = dp(8) })
        row.addView(actions, LinearLayout.LayoutParams(0, -1, 3.8f).apply { rightMargin = dp(8) })
        row.addView(controlColumn(arm, drivePanel), LinearLayout.LayoutParams(0, -1, 1.6f))
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
        updateControlsScene()
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
    private fun updateControlsScene() {
        if (page != Page.CONTROLS) return
        val mapVisibility = if (mapActive && !showingCamera) View.VISIBLE else View.GONE
        routeMap?.view?.visibility = mapVisibility
        controlsLocate?.visibility = mapVisibility
        controlsEstimate?.visibility = mapVisibility
        mapBadge?.visibility = mapVisibility
        controlsPlaceholder?.visibility = if (!mapActive && !showingCamera) View.VISIBLE else View.GONE
        fpvCamera?.visibility = if (showingCamera) View.VISIBLE else View.GONE
        fpvCamera?.setVisiblePreview(showingCamera)
        fpvSwap?.text = if (showingCamera) "Show map" else "Show camera"
    }

    private fun applyThemeImage() {
        currentShell?.background = themeBitmap?.let { ThemeImageDrawable(it, pale) } ?: android.graphics.drawable.ColorDrawable(pale)
    }
    private fun chooseThemeImage() {
        if (imageWork) return
        disableControl()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }, 45)
    }
    private fun showFpvOptions() {
        disableControl()
        val camera = fpvCamera ?: return
        val names = FpvStreamPolicy.Mode.entries.map { (if (it == camera.mode()) "✓ " else "") + it.label } + "USB diagnostics"
        AlertDialog.Builder(this).setTitle("FPV video options")
            .setItems(names.toTypedArray()) { _, index ->
                if (index == FpvStreamPolicy.Mode.entries.size) {
                    AlertDialog.Builder(this).setTitle("USB FPV diagnostics").setMessage(camera.diagnosis())
                        .setPositiveButton("Close", null).show()
                } else {
                    showingCamera = true; updateControlsScene()
                    // Persist the format before requesting runtime camera access.
                    getSharedPreferences("fpv", MODE_PRIVATE).edit().putString("mode", FpvStreamPolicy.Mode.entries[index].name).apply()
                    connectFpv()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun connectFpv() {
        if (page != Page.CONTROLS || !resumed || pendingFpvPermission) return
        // Runtime and USB permission dialogs can take focus. Neutralize before either.
        disableControl()
        showingCamera = true
        updateControlsScene()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingFpvPermission = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 44)
        } else fpvCamera?.connectCamera()
    }

    private fun setControlsMapVisible(visible: Boolean) {
        if (page != Page.CONTROLS) return
        mapActive = visible
        updateControlsScene()
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
        updateControlsScene()
        controlsEstimate?.text = "RC STOPPED · last estimate frozen"
        startPhoneLocation()
        routeMap?.view?.post { routeMap?.draw() }
    }
    private fun updateRoute() {
        routeMap?.draw()
        val lastGps = route.rover.lastOrNull()?.timeMs
        val gpsAge = lastGps?.let {
            val seconds = ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 1000L)
            if (seconds < 60) "${seconds}s" else "${seconds / 60}m"
        }
        mapBadge?.text = "${if (gpsAge == null) "NO GPS · ${if (route.home == null) "SAMPLE" else "PHONE HOME"}" else "LAST GPS $gpsAge AGO"} · " +
            if (controlEnabled.get()) "CYAN RC ESTIMATE" else "CYAN ESTIMATE FROZEN"
        routeStatus?.text = "HOME ${if (route.home == null) "GPS pending · SAMPLE MAP" else "GPS fixed"}  ·  " +
            "PHONE ${route.phone.size}  ·  ROVER ${route.rover.size}" +
            (route.rover.lastOrNull()?.headingDegrees?.let { " H ${it.toInt()}°" } ?: "") +
            "  ·  SENT ${route.commands.size}"
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
        if (requestCode == 44) {
            pendingFpvPermission = false
            if (page == Page.CONTROLS && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (resumed) fpvCamera?.connectCamera() else connectFpvOnResume = true
            } else if (page == Page.CONTROLS)
                Toast.makeText(this, "Camera permission required. Tap Connect FPV to retry.", Toast.LENGTH_LONG).show()
            return
        }
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
        if (requestCode == 45 && resultCode == RESULT_OK && data?.data != null && !imageWork) {
            val uri = data.data!!
            disableControl(); imageWork = true
            imageExecutor.execute {
                var errorMessage: String? = null
                val image = try { ThemeImageStore.importImage(applicationContext, uri) }
                    catch (error: Exception) { errorMessage = error.message ?: "Image could not be imported"; null }
                    catch (_: OutOfMemoryError) { errorMessage = "Image is too large; choose a smaller photo"; null }
                runOnUiThread {
                    imageWork = false
                    if (!isDestroyed) {
                        if (image != null) { themeBitmap = image; if (page == Page.SETUP) render() else applyThemeImage() }
                        Toast.makeText(this, errorMessage ?: "Background image saved", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }
        if (requestCode == 43 && resultCode == RESULT_OK) {
            try {
                val uri = data?.data ?: return
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(route.encode()) }
                Toast.makeText(this, "Route exported", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(this, "Could not export route", Toast.LENGTH_LONG).show() }
        }
    }
    private fun stopPhoneLocation() { try { locationManager.removeUpdates(phoneListener) } catch (_: Exception) {} }
    private fun releaseMap() {
        pendingFpvPermission = false; connectFpvOnResume = false
        fpvCamera?.close(); fpvCamera = null; fpvSwap = null; fpvConnect = null
        showingCamera = false
        pendingPhoneLocate = false
        stopPhoneLocation(); mapActive = false
        routeMap?.let { if (resumed) it.onPause(); if (started) it.onStop(); it.onDestroy() }
        routeMap = null; routeStatus = null; mapBadge = null
        controlsPlaceholder = null; controlsLocate = null; controlsEstimate = null; deadReckoning.reset()
    }
    private fun saveRoute() {
        try { openFileOutput("route-session.csv", MODE_PRIVATE).bufferedWriter().use { it.write(route.encode()) } }
        catch (_: Exception) { routeStatus?.text = "Could not save route locally" }
    }
    private fun linkFresh() = HeartbeatHealth.isFresh(target, heartbeatAt, SystemClock.elapsedRealtime())
    private fun showMiniRoverSetup() {
        AlertDialog.Builder(this).setTitle("Mini rover Wi-Fi")
            .setMessage(MiniRoverWifi.SETUP + "\n\nEndpoint: ${MiniRoverWifi.HOST}:${MiniRoverWifi.PORT}.")
            .setPositiveButton("Open Wi-Fi settings") { _, _ -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            .setNegativeButton("Close", null).show()
    }
    private fun linkDiagnosis(): String {
        if (miniRoverMode) {
            val state = if (linkFresh()) "Rover heartbeat received; ${if (vehicleArmed == true) "ARMED" else "DISARMED"}. "
                else "No fresh rover heartbeat. Join ${MiniRoverWifi.SSID}, keep the no-internet Wi-Fi connected, and tap Connect. "
            return state + "Target ${MiniRoverWifi.HOST}:${MiniRoverWifi.PORT}; local UDP ${MiniRoverWifi.PORT}. " +
                "Close PC TeleRC to free the single-client lease. " +
                "Enable app control, center until ARM · READY, then ARM. " +
                "Use standalone Wi-Fi firmware 1.2.0; no receiver or flight controller is required. " +
                "F405 UART settings and bridge counters do not apply. " +
                (if (lastArmAck.isNotBlank()) "Last ARM/DISARM result: $lastArmAck. " else "") +
                lastLinkError
        }
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
            usbMode && usbPermissionPending -> "USB PERMISSION PENDING"
            !connected.get() && wantsLink -> if (usbMode) "CONNECT USB LoRa BASE" else "RECONNECTING · CHECK ROVER WI-FI"
            !connected.get() -> "DISCONNECTED"
            target == 0 -> "WAITING FOR HEARTBEAT"
            controlEnabled.get() && !HeartbeatHealth.isFresh(target, heartbeatAt, now) ->
                "SYSTEM $target  •  CONTROL PAUSED"
            !HeartbeatHealth.isRecentlySeen(target, heartbeatAt, now) -> "SYSTEM $target  •  HEARTBEAT LOST"
            controlEnabled.get() -> "SYSTEM $target  •  CONTROL ON"
            else -> "SYSTEM $target  •  LINK ACTIVE"
        }
        connect?.text = if (wantsLink) "Disconnect" else "Connect"
        enable?.isEnabled = connected.get() && (controlEnabled.get() || linkFresh())
        enable?.text = if (controlEnabled.get()) "STOP CONTROL" else "ENABLE CONTROL"
        armButton?.apply { text = armCaption(); isEnabled = linkFresh() && vehicleArmed != null }
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
        if (page == Page.TEST || connected.get()) return
        val saved = getSharedPreferences("link", MODE_PRIVATE)
        val address = if (miniRoverMode) MiniRoverWifi.HOST else if (usbMode) "127.0.0.1" else
            (host?.text?.toString() ?: saved.getString("host", "192.168.4.1")).orEmpty().trim()
        val valid = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) &&
            address.split('.').all { it.toInt() in 0..255 }
        val number = if (miniRoverMode) MiniRoverWifi.PORT else if (usbMode) 14550 else
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
            if (!miniRoverMode) saved.edit().putString("host", address).putInt("port", number).apply()
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
                                        else if (miniRoverMode && ack.result == 2)
                                            "DENIED · Enable control and center both sticks before ARM"
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
                                if (miniRoverMode) miniArmGate.sent(channels.one, channels.two, SystemClock.elapsedRealtime())
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
        if (resumed && wantsLink && page != Page.TEST) reconnectHandler.postDelayed(reconnect, 2000)
    }
    private fun revokeExpiredControl(transport: LinkTransport) {
        val expired = synchronized(commandLock) {
            if (socket !== transport || !controlEnabled.expire(target, heartbeatAt, SystemClock.elapsedRealtime())) false
            else {
                commandEpoch++
                miniArmGate.reset()
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
    override fun onPause() { mazeView?.pause(); practiceSteer?.reset(); practiceDrive?.reset(); fpvCamera?.pause(); hideLoRaKey(); resumed = false; reconnectHandler.removeCallbacks(reconnect); disableControl(); stopPhoneLocation(); routeMap?.onPause(); saveRoute(); super.onPause() }
    override fun onResume() { super.onResume(); resumed = true; mazeView?.resume(); fpvCamera?.resume(); if (connectFpvOnResume && page == Page.CONTROLS) { connectFpvOnResume = false; connectFpv() }; routeMap?.onResume(); startPhoneLocation(); scheduleReconnect(); if (::updater.isInitialized) updater.resumePendingInstall() }
    override fun onStart() { super.onStart(); started = true; routeMap?.onStart() }
    override fun onStop() { fpvCamera?.suspendCamera(); started = false; routeMap?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); routeMap?.onLowMemory() }
    override fun onDestroy() { usbPermissionReceiver?.let { unregisterReceiver(it) }; usbPermissionReceiver = null; usbPermissionPending = false; wantsLink = false; reconnectHandler.removeCallbacks(reconnect); releaseMap(); stop(explicitDisconnect = true); saveRoute(); updater.close(); imageExecutor.shutdown(); currentShell = null; txExecutor.shutdown(); super.onDestroy() }
}
