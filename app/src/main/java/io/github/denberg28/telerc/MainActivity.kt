package io.github.denberg28.telerc

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
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
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private enum class Page { SETUP, CONTROLS, TEST_DRIVE }
    private var page = Page.SETUP
    private var socket: DatagramSocket? = null
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
    private val controlEnabled = AtomicBoolean(false)
    private val commandLock = Any()
    @Volatile private var heartbeatAt = 0L
    @Volatile private var bridgeStatusAt = 0L
    @Volatile private var bridgeRxBytes = 0L
    @Volatile private var bridgeFrames = 0L
    @Volatile private var bridgeAccepted = -1L
    @Volatile private var bridgeRejected = -1L
    @Volatile private var bridgeCommandBytes = -1L
    @Volatile private var bridgeSteer = 1500
    @Volatile private var bridgeDrive = 1500
    @Volatile private var bridgeDriveMin = 1500
    @Volatile private var bridgeDriveMax = 1500
    @Volatile private var bridgeDriveChanged = -1L
    @Volatile private var bridgeHeartbeatCount = -1L
    @Volatile private var bridgeHeartbeatGapMs = -1L
    @Volatile private var bridgeHeartbeatMaxGapMs = -1L
    @Volatile private var target = 0
    @Volatile private var vehicleArmed: Boolean? = null
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
    private var resumed = false
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
    private fun nav(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL
        val setup = button("⌂  Setup", page == Page.SETUP) { switchTo(Page.SETUP) }
        val controls = button("▣  Controls", page == Page.CONTROLS) { switchTo(Page.CONTROLS) }
        val test = button("▤  Test drive", page == Page.TEST_DRIVE) { switchTo(Page.TEST_DRIVE) }
        addView(setup, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(7) })
        addView(controls, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(7) })
        addView(test, LinearLayout.LayoutParams(0, -1, 1f))
    }
    private fun pageDropdown(label: String): Button {
        lateinit var anchor: Button
        anchor = button("$label ▾", true) {
            PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Setup")
                menu.add(0, 2, 1, "Controls")
                menu.add(0, 3, 2, "Test drive")
                setOnMenuItemClickListener { item ->
                    switchTo(when (item.itemId) { 1 -> Page.SETUP; 2 -> Page.CONTROLS; else -> Page.TEST_DRIVE })
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
        armButton = null
        functionButtons.clear()
        window.statusBarColor = pale
        window.navigationBarColor = pale
        window.decorView.systemUiVisibility = if (darkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        when (page) {
            Page.SETUP -> renderSetup()
            Page.CONTROLS -> renderControls()
            Page.TEST_DRIVE -> renderTestDrive()
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
    private fun toggleArmDisarm() {
        val armed = vehicleArmed
        val udp = socket
        val remote = endpoint
        val system = target
        if (!linkFresh() || armed == null || udp == null || remote == null || system == 0) {
            android.widget.Toast.makeText(this, "ARM state unavailable · wait for a fresh rover heartbeat",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // Arming and disarming always start from neutral and revoke live joystick authority.
        disableControl()
        try {
            synchronized(commandLock) {
                repeat(3) { attempt ->
                    val command = Mavlink.armDisarm(200 + attempt, system, 1, !armed, attempt)
                    udp.send(DatagramPacket(command, command.size, remote, endpointPort))
                }
            }
            android.widget.Toast.makeText(this,
                if (armed) "DISARM command sent · waiting for heartbeat" else "ARM command sent · waiting for heartbeat",
                android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "ARM/DISARM command could not be sent",
                android.widget.Toast.LENGTH_SHORT).show()
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
    private fun renderSetup() {
        enable = null; steeringStick = null; driveStick = null
        val root = shell()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(pageDropdown("Setup"), LinearLayout.LayoutParams(dp(120), dp(44)))
        header.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(button(if (darkTheme) "☀" else "☾", false) {
            disableControl()
            getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark", !darkTheme).apply()
            recreate()
        }.apply { contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme" }, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        val connection = card().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(text("CONNECTION", 12f, accent, true))
            host = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "Bridge IPv4"; setTextColor(ink); setHintTextColor(muted)
                inputType = InputType.TYPE_CLASS_TEXT
                background = shape(pale, 12); setPadding(dp(12), 0, dp(12), 0)
                setText(if (connected.get()) endpoint?.hostAddress else getSharedPreferences("link", MODE_PRIVATE).getString("host", "192.168.4.1"))
                isEnabled = !connected.get()
            }
            port = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "UDP port"; setTextColor(ink); setHintTextColor(muted)
                inputType = InputType.TYPE_CLASS_NUMBER
                background = shape(pale, 12); setPadding(dp(12), 0, dp(12), 0)
                setText((if (connected.get()) endpointPort else getSharedPreferences("link", MODE_PRIVATE).getInt("port", 14550)).toString())
                isEnabled = !connected.get()
            }
            val endpointRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            host?.textSize = 15f; port?.textSize = 15f
            host?.contentDescription = "Bridge IPv4 address"; port?.contentDescription = "UDP port"
            endpointRow.addView(host, LinearLayout.LayoutParams(0, dp(36), 2f).apply { rightMargin = dp(6) })
            endpointRow.addView(port, LinearLayout.LayoutParams(0, dp(36), 1f))
            addView(endpointRow, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
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
            addView(connect, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
        }
        left.addView(connection, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(8) })
        val colors = card().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(text("COLOR THEME", 12f, accent, true))
            val choices = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val selected = getSharedPreferences("appearance", MODE_PRIVATE).getString("accent", "Blue")
            AppColors.names.forEach { name ->
                choices.addView(button(if (selected == name) "✓ $name" else name, selected == name) {
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putString("accent", name).apply()
                    render()
                }.apply { textSize = 12f; contentDescription = "$name accent${if (selected == name) ", selected" else ""}" },
                    LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(4) })
            }
            addView(choices, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(4) })
        }
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val sensitivityLabel = text("Sensitivity · ${prefs.getInt("sensitivity", 100)}%", 11f, muted)
        colors.addView(sensitivityLabel)
        colors.addView(SeekBar(this).apply {
            max = 75; progress = prefs.getInt("sensitivity", 100).coerceIn(25, 100) - 25
            contentDescription = "Joystick sensitivity, 25 to 100 percent"
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    sensitivityLabel.text = "Sensitivity · ${value + 25}%"
                    if (fromUser) prefs.edit().putInt("sensitivity", value + 25).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(28)))
        left.addView(colors, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(left, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val info = card().apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(text("LINK STATUS", 12f, accent, true))
            status = text("DISCONNECTED", 15f, ink, true); addView(status)
            status?.apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            addView(button("Diagnose link", false) {
                AlertDialog.Builder(this@MainActivity).setTitle("Link diagnostics")
                    .setMessage(linkDiagnosis()).setPositiveButton("OK", null).show()
            }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(4) })
        }
        details.addView(info, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(8) })
        val updates = card().apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(text("APP UPDATE", 12f, accent, true))
            addView(text("TeleRC ${BuildConfig.VERSION_NAME}", 14f, ink, true))
            updateStatus = text("Signed APK · manual check", 11f, muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            addView(updateStatus)
            addView(button("Check for updates", false) { updater.check() },
                LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(4) })
        }
        details.addView(updates, LinearLayout.LayoutParams(-1, 0, 1.15f))
        body.addView(details, LinearLayout.LayoutParams(0, -1, 1f))
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
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> switchTo(Page.SETUP)
                        2 -> Unit
                        3 -> switchTo(Page.TEST_DRIVE)
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
            if (controlEnabled.get()) disableControl() else if (linkFresh()) {
                controlEnabled.set(true)
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
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> switchTo(Page.SETUP)
                        2 -> switchTo(Page.CONTROLS)
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
        if (!connected.get()) return "Tap Connect first, then wait four seconds and diagnose again."
        if (linkFresh()) {
            val commands = if (bridgeAccepted >= 0) {
                " Bridge: accepted $bridgeAccepted, rejected $bridgeRejected, UART TX $bridgeCommandBytes bytes, " +
                    "CH1 $bridgeSteer, CH2 $bridgeDrive. " +
                    (if (bridgeDriveChanged >= 0) "Drive history: min $bridgeDriveMin, max $bridgeDriveMax, " +
                        "$bridgeDriveChanged non-neutral commands since bridge boot."
                    else "Drive history unavailable; update the bridge sketch.")
            } else " Bridge command counters unavailable; upload the current bridge sketch."
            val heartbeatTiming = if (bridgeHeartbeatCount >= 0)
                " FC heartbeat count $bridgeHeartbeatCount, latest gap $bridgeHeartbeatGapMs ms, max gap $bridgeHeartbeatMaxGapMs ms."
            else ""
            return "Rover heartbeat received. Link active. Control still requires Enable Control." + commands +
                heartbeatTiming + " An accepted command does not prove the rover is armed or that its motor outputs are configured."
        }
        if (bridgeStatusAt == 0L || SystemClock.elapsedRealtime() - bridgeStatusAt > 7000)
            return "No recent reply from the ESP32 bridge over Wi-Fi. Verify that the updated bridge sketch " +
                "is running (older firmware cannot send diagnostics), and check its USB Serial Monitor. " +
                "Confirm the bridge address/UDP port and phone Wi-Fi address are on the same network. " +
                "Wait four seconds after Connect and try again."
        if (bridgeRxBytes == 0L)
            return "ESP32 responds, but receives zero bytes from the F405. Check T3 → ESP GPIO18, " +
                "shared GND, SERIAL3_PROTOCOL=2 and SERIAL3_BAUD=115; reboot the F405 after setting them."
        if (bridgeFrames == 0L)
            return "ESP32 receives UART bytes ($bridgeRxBytes), but no complete MAVLink frames. " +
                "Check baud 115200, the ESP GPIO18 RX pin and UART3 wiring."
        val age = if (heartbeatAt > 0) "Last valid heartbeat ${SystemClock.elapsedRealtime() - heartbeatAt} ms ago. " else "No valid autopilot heartbeat yet. "
        return age + "ESP32 UART RX: $bridgeRxBytes bytes, $bridgeFrames frames; commands accepted: " +
            "$bridgeAccepted, rejected: $bridgeRejected, UART TX: $bridgeCommandBytes bytes. " +
            "Last CH1/CH2: $bridgeSteer/$bridgeDrive. If commands are accepted but motors do not move, " +
            "check ArduRover armed state, mode, RC override source system ID, RC1/RC2 input calibration, " +
            "SERVO output functions, motor driver enable and power. A written UART frame is not a motor acknowledgement."
    }
    private fun refreshUi() {
        val now = SystemClock.elapsedRealtime()
        status?.text = when {
            !connected.get() && wantsLink -> "RECONNECTING · CHECK ROVER WI-FI"
            !connected.get() -> "DISCONNECTED"
            target == 0 -> "WAITING FOR HEARTBEAT"
            !HeartbeatHealth.isRecentlySeen(target, heartbeatAt, now) -> "SYSTEM $target  •  HEARTBEAT LOST"
            controlEnabled.get() -> "SYSTEM $target  •  CONTROL ON"
            else -> "SYSTEM $target  •  LINK ACTIVE"
        }
        connect?.text = if (wantsLink) "Disconnect" else "Connect"
        enable?.isEnabled = connected.get()
        enable?.text = if (controlEnabled.get()) "STOP CONTROL" else "ENABLE CONTROL"
        refreshFunctionButtons()
    }
    private fun disableControl() {
        // Safety invariant: leaving live control must command neutral, never release immediately
        // back to a possibly non-neutral physical RC input. The ESP32 bridge then holds neutral
        // through app/tab/link loss until a fresh non-neutral TeleRC control session starts.
        synchronized(commandLock) {
            controlEnabled.set(false)
            steering = 1500; drive = 1500
            if (target != 0) try {
                val udp = socket; val remote = endpoint
                if (udp != null && remote != null) {
                    // UDP is intentionally repeated so a tab change/Disconnect is very unlikely
                    // to lose the one packet that establishes the bridge's safe-stop state.
                    repeat(3) { sequence ->
                        val neutral = Mavlink.override(sequence, target, 1, 1500, 1500, 1500, 1500)
                        udp.send(DatagramPacket(neutral, neutral.size, remote, endpointPort))
                    }
                }
            } catch (_: Exception) {}
        }
        if (page == Page.CONTROLS && mapActive) {
            // Preserve the recovery map and its last estimate after Stop or link loss.
            deadReckoning.hold()
            controlsEstimate?.text = "RC STOPPED · last estimate frozen"
            updateRoute()
        }
        steeringStick?.isEnabled = false; driveStick?.isEnabled = false
        steeringStick?.reset(); driveStick?.reset()
        refreshUi()
    }
    private fun start() {
        if (connected.get()) return
        val saved = getSharedPreferences("link", MODE_PRIVATE)
        val address = (host?.text?.toString() ?: saved.getString("host", "192.168.4.1")).orEmpty().trim()
        val valid = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) &&
            address.split('.').all { it.toInt() in 0..255 }
        val number = (port?.text?.toString() ?: saved.getInt("port", 14550).toString()).toIntOrNull()
        if (!valid || number == null || number !in 1..65535) {
            wantsLink = false
            saved.edit().putBoolean("auto_connect", false).apply()
            refreshUi(); status?.text = "INVALID ADDRESS OR PORT"; return
        }
        val remote = InetAddress.getByName(address)
        saved.edit().putString("host", address).putInt("port", number).apply()
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = connectivity.allNetworks.firstOrNull {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifi == null) { status?.text = "CONNECT PHONE TO ROVER WI-FI"; return }
        val udp = try {
            DatagramSocket(number).also { socket ->
                try {
                    wifi.bindSocket(socket) // keep UDP on the rover AP even if mobile data is the default
                    socket.soTimeout = 50
                } catch (e: Exception) { socket.close(); throw e }
            }
        } catch (e: Exception) { status?.text = "WI-FI UDP PORT UNAVAILABLE"; return }
        socket = udp; endpoint = remote; endpointPort = number
        target = 0; vehicleArmed = null; heartbeatAt = 0; bridgeStatusAt = 0; bridgeRxBytes = 0; bridgeFrames = 0
        bridgeAccepted = -1; bridgeRejected = -1; bridgeCommandBytes = -1; bridgeDriveChanged = -1
        bridgeHeartbeatCount = -1; bridgeHeartbeatGapMs = -1; bridgeHeartbeatMaxGapMs = -1
        disableControl(); connected.set(true)
        host?.isEnabled = false; port?.isEnabled = false; refreshUi()
        thread(name = "telerc-link") {
            val input = ByteArray(512); var sequence = 0; var lastSend = 0L; var lastDiscovery = 0L; var lastUiRefresh = 0L
            val discovery = "TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII)
            while (connected.get() && socket === udp) {
                // Do not tear down the socket because Android temporarily changes
                // capabilities on the captured Network object. The ESP32 data path
                // is authoritative: actual UDP failure or sustained packet silence
                // drives reconnection instead.
                val discoveryNow = SystemClock.elapsedRealtime()
                if (discoveryNow - lastDiscovery >= 1000) {
                    try {
                        udp.send(DatagramPacket(discovery, discovery.size, remote, number))
                        lastDiscovery = discoveryNow
                    } catch (_: Exception) { break }
                }
                try {
                    val packet = DatagramPacket(input, input.size)
                    udp.receive(packet)
                    if (packet.address == remote && packet.port == number) {
                        val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                        if (payload.size in 20..160 && payload.take(17).toByteArray()
                                .contentEquals("TELERC_STATUS_V1,".toByteArray(Charsets.US_ASCII))) {
                            val fields = String(payload, Charsets.US_ASCII).split(',')
                            val bytes = fields.getOrNull(1)?.toLongOrNull()
                            val frames = fields.getOrNull(2)?.toLongOrNull()
                            if (fields.size in listOf(3, 8, 11, 14) && bytes != null && frames != null &&
                                bytes >= 0 && frames >= 0 && frames <= bytes) {
                                bridgeRxBytes = bytes; bridgeFrames = frames
                                bridgeStatusAt = SystemClock.elapsedRealtime()
                                if (fields.size >= 8) {
                                    val counters = fields.drop(3).map { it.toLongOrNull() }
                                    if (counters.all { it != null && it >= 0 } &&
                                        (counters[3] == 0L || counters[3]!! in 1000..2000) &&
                                        (counters[4] == 0L || counters[4]!! in 1000..2000)) {
                                        bridgeAccepted = counters[0]!!; bridgeRejected = counters[1]!!
                                        bridgeCommandBytes = counters[2]!!
                                        bridgeSteer = if (counters[3] == 0L) 1500 else counters[3]!!.toInt()
                                        bridgeDrive = if (counters[4] == 0L) 1500 else counters[4]!!.toInt()
                                        if (fields.size >= 11 && counters[5]!! in 1000..2000 &&
                                            counters[6]!! in 1000..2000 && counters[5]!! <= counters[6]!!) {
                                            bridgeDriveMin = counters[5]!!.toInt()
                                            bridgeDriveMax = counters[6]!!.toInt()
                                            bridgeDriveChanged = counters[7]!!
                                        }
                                        if (fields.size == 14) {
                                            bridgeHeartbeatCount = counters[8]!!
                                            bridgeHeartbeatGapMs = counters[9]!!
                                            bridgeHeartbeatMaxGapMs = counters[10]!!
                                        }
                                    }
                                }
                            }
                        } else for (frame in Mavlink.frames(payload)) {
                            val heartbeat = Mavlink.heartbeat(frame)
                            if (heartbeat != null && (target == 0 || target == heartbeat.system)) {
                                target = heartbeat.system
                                vehicleArmed = heartbeat.armed
                                heartbeatAt = SystemClock.elapsedRealtime()
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
                } catch (_: SocketTimeoutException) {} catch (_: Exception) { break }
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
                    } catch (_: Exception) { break }
                }
                if (now - lastUiRefresh >= 250) {
                    lastUiRefresh = now
                    runOnUiThread {
                        if (socket === udp && connected.get()) {
                            // Recheck at execution time: another heartbeat may have arrived.
                            if (!linkFresh() && controlEnabled.get()) disableControl()
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
    private fun stop(explicitDisconnect: Boolean = false) {
        disableControl()
        val udp = socket
        val remote = endpoint
        if (explicitDisconnect && udp != null && remote != null) {
            val message = "TELERC_DISCONNECT_V1".toByteArray(Charsets.US_ASCII)
            try {
                repeat(3) { udp.send(DatagramPacket(message, message.size, remote, endpointPort)) }
            } catch (_: Exception) {}
        }
        connected.set(false)
        udp?.close(); socket = null; endpoint = null; target = 0; vehicleArmed = null; heartbeatAt = 0
        bridgeStatusAt = 0; bridgeRxBytes = 0; bridgeFrames = 0
        bridgeAccepted = -1; bridgeRejected = -1; bridgeCommandBytes = -1; bridgeDriveChanged = -1
        bridgeHeartbeatCount = -1; bridgeHeartbeatGapMs = -1; bridgeHeartbeatMaxGapMs = -1
        host?.isEnabled = true; port?.isEnabled = true; refreshUi()
    }
    override fun onPause() { resumed = false; reconnectHandler.removeCallbacks(reconnect); disableControl(); stopPhoneLocation(); routeMap?.onPause(); testCourse?.stop(); musicButton?.apply { isSelected = false; text = "♫" }; saveRoute(); super.onPause() }
    override fun onResume() { super.onResume(); resumed = true; routeMap?.onResume(); startPhoneLocation(); testCourse?.resume(); scheduleReconnect(); if (::updater.isInitialized) updater.resumePendingInstall() }
    override fun onStart() { super.onStart(); started = true; routeMap?.onStart() }
    override fun onStop() { started = false; routeMap?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); routeMap?.onLowMemory() }
    override fun onDestroy() { wantsLink = false; reconnectHandler.removeCallbacks(reconnect); releaseMap(); stop(explicitDisconnect = true); saveRoute(); updater.close(); super.onDestroy() }
}
