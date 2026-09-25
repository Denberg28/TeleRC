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
import android.os.Bundle
import android.os.SystemClock
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
    private val controlEnabled = AtomicBoolean(false)
    private val commandLock = Any()
    @Volatile private var heartbeatAt = 0L
    @Volatile private var bridgeStatusAt = 0L
    @Volatile private var bridgeRxBytes = 0L
    @Volatile private var bridgeFrames = 0L
    @Volatile private var target = 0
    @Volatile private var steering = 1500
    @Volatile private var drive = 1500
    private var status: TextView? = null
    private var connect: Button? = null
    private var enable: Button? = null
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
    private var started = false
    private var resumed = false
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val phoneListener = LocationListener { location: Location ->
        if (!mapActive || !location.hasAccuracy() ||
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos > 10_000_000_000L) return@LocationListener
        if (route.addPhone(TrackPoint(location.latitude, location.longitude, location.time), location.accuracy)) {
            if (routeMap?.anchorToHomeIfWaiting() == true) deadReckoning.reset()
            updateRoute()
        }
    }
    private var host: EditText? = null
    private var port: EditText? = null
    private var updateStatus: TextView? = null
    private lateinit var updater: AppUpdater
    private var endpoint: InetAddress? = null
    private var endpointPort = 0
    private val ink = Color.rgb(35, 38, 53)
    private val muted = Color.rgb(103, 105, 124)
    private val accent = Color.rgb(112, 88, 166)
    private val pale = Color.rgb(247, 245, 251)

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(value: String, filled: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 15f
        setTextColor(if (filled) Color.WHITE else accent)
        background = shape(if (filled) accent else Color.rgb(237, 231, 249), 16)
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape(Color.WHITE); elevation = dp(2).toFloat()
    }
    private fun LinearLayout.addCard(view: View) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val session = getFileStreamPath("route-session.csv")
            if (session.length() <= 16_000_000L)
                route.decode(openFileInput("route-session.csv").bufferedReader().use { it.readText() })
        } catch (_: Exception) {}
        updater = AppUpdater(this) { updateStatus?.text = it }
        page = when (savedInstanceState?.getString("page")) {
            "CONTROLS" -> Page.CONTROLS
            "TEST_DRIVE" -> Page.TEST_DRIVE
            else -> Page.SETUP
        }
        requestedOrientation = if (page == Page.SETUP) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        val orientation = if (next == Page.SETUP) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        requestedOrientation = orientation
        render()
    }
    private fun shell(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(pale)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnApplyWindowInsetsListener { view, insets ->
            val left: Int; val top: Int; val right: Int; val bottom: Int
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom
            } else {
                left = insets.systemWindowInsetLeft; top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight; bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(dp(16) + left, dp(12) + top, dp(16) + right, dp(12) + bottom)
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
    private fun render() {
        window.decorView.systemUiVisibility = 0
        when (page) {
            Page.SETUP -> renderSetup()
            Page.CONTROLS -> renderControls()
            Page.TEST_DRIVE -> renderTestDrive()
        }
        refreshUi()
    }
    private fun renderSetup() {
        enable = null; steeringStick = null; driveStick = null
        val root = shell()
        root.addView(nav(), LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(6) })
        val scroll = ScrollView(this).apply { isFillViewport = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
        body.addView(text("TeleRC", 24f, ink, true), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val craft = card().apply {
            addView(text("CRAFT PROFILE", 12f, accent, true))
            addView(text("Rover", 19f, ink, true))
            addView(text("ArduRover  ·  CH1 steer  ·  CH3 drive", 12f, muted))
            addView(text("Other craft profiles are not yet available.", 11f, muted))
        }
        body.addCard(craft)
        val connection = card().apply {
            addView(text("CONNECTION", 12f, accent, true))
            addView(text("MAVLink over local Wi-Fi", 17f, ink, true))
            addView(text("ESP32 or Raspberry Pi bridge address and UDP port", 12f, muted))
            host = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "Bridge IPv4"; setTextColor(ink)
                inputType = InputType.TYPE_CLASS_TEXT
                background = shape(pale, 12); setPadding(dp(12), 0, dp(12), 0)
                setText(if (connected.get()) endpoint?.hostAddress else getSharedPreferences("link", MODE_PRIVATE).getString("host", "192.168.4.1"))
                isEnabled = !connected.get()
            }
            port = EditText(this@MainActivity).apply {
                setSingleLine(); hint = "UDP port"; setTextColor(ink)
                inputType = InputType.TYPE_CLASS_NUMBER
                background = shape(pale, 12); setPadding(dp(12), 0, dp(12), 0)
                setText((if (connected.get()) endpointPort else getSharedPreferences("link", MODE_PRIVATE).getInt("port", 14550)).toString())
                isEnabled = !connected.get()
            }
            addView(host, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
            addView(port, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
            connect = button("Connect") { if (connected.get()) stop() else start() }
            addView(connect, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        }
        body.addCard(connection)
        val info = card().apply {
            addView(text("LINK STATUS", 12f, accent, true))
            status = text("DISCONNECTED", 15f, ink, true); addView(status)
            addView(text("A valid heartbeat and Enable action are required.", 12f, muted))
            addView(button("Diagnose link", false) {
                AlertDialog.Builder(this@MainActivity).setTitle("Link diagnostics")
                    .setMessage(linkDiagnosis()).setPositiveButton("OK", null).show()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        }
        body.addCard(info)
        val updates = card().apply {
            addView(text("APP UPDATE", 12f, accent, true))
            addView(text("TeleRC ${BuildConfig.VERSION_NAME}", 17f, ink, true))
            addView(text("Check GitHub for a newer signed APK.", 12f, muted))
            updateStatus = text("Updates are checked only when you tap the button.", 12f, muted)
            addView(updateStatus)
            addView(button("Check for updates", false) { updater.check() },
                LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }
        body.addCard(updates)
        body.addView(text("Private bench test  •  Raise wheels before enabling control.", 12f, muted))
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun renderControls() {
        host = null; port = null; connect = null
        val root = shell()
        root.addView(nav(), LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(6) })
        status = text("DISCONNECTED", 12f, accent, true).apply { gravity = Gravity.CENTER }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(30)).apply { bottomMargin = dp(6) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun control(title: String, hint: String, vertical: Boolean, changed: (Int) -> Unit): Pair<LinearLayout, JoystickView> {
            lateinit var stick: JoystickView
            val panel = card().apply {
                addView(text(title, 16f, ink, true).apply { gravity = Gravity.CENTER })
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
        val (drivePanel, driveInput) = control("DRIVE", "CH3 · forward / reverse", true) { drive = it }
        steeringStick = steerInput; driveStick = driveInput
        val actions = card().apply {
            addView(text("CONTROL", 16f, ink, true).apply { gravity = Gravity.CENTER })
            val scene = FrameLayout(this@MainActivity)
            controlsPlaceholder = text("Rover · CH1 / CH3\n\nEnable control to show recovery map", 12f, muted).apply {
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
                background = shape(Color.WHITE, 8); setPadding(dp(5), dp(2), dp(5), dp(2))
                visibility = View.GONE
            }
            mapBadge = badge
            scene.addView(badge, FrameLayout.LayoutParams(-2, dp(25), Gravity.TOP or Gravity.LEFT)
                .apply { leftMargin = dp(4); topMargin = dp(4) })
            controlsLocate = button("⌖", false) { map.locateRecovery() }.apply {
                contentDescription = "Center on last rover GPS, then estimate or Home"; visibility = View.GONE
            }
            scene.addView(controlsLocate, FrameLayout.LayoutParams(dp(36), dp(34), Gravity.TOP or Gravity.RIGHT)
                .apply { rightMargin = dp(4); topMargin = dp(4) })
            controlsEstimate = text("EST 0 m · H 0° · 0 m/s", 10f, ink, true).apply {
                background = shape(Color.WHITE, 8); setPadding(dp(5), dp(2), dp(5), dp(2))
                visibility = View.GONE
            }
            scene.addView(controlsEstimate, FrameLayout.LayoutParams(-2, dp(25), Gravity.BOTTOM or Gravity.RIGHT)
                .apply { rightMargin = dp(4); bottomMargin = dp(4) })
            if (started) map.onStart()
            if (resumed) map.onResume()
            addView(scene, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(7) })
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
        actions.addView(enable, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(7) })
        row.addView(steerPanel, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(8) })
        row.addView(actions, LinearLayout.LayoutParams(0, -1, 1.4f).apply { rightMargin = dp(8) })
        row.addView(drivePanel, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("Purple: last rover GPS · Cyan: RC estimate · ⌖ locate · link loss stops commands", 11f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
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
        root.addView(nav(), LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(6) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val course = TestDriveView(this)
        testCourse = course
        val vehiclePrefs = getSharedPreferences("test_vehicle", MODE_PRIVATE)
        course.maxYawRateDegrees = vehiclePrefs.getFloat("turn_deg_s", 220f).coerceIn(10f, 360f)
        val left = card().apply {
            addView(text("STEER", 16f, ink, true).apply { gravity = Gravity.CENTER })
            addView(text("CH1  ·  left / right", 11f, muted).apply { gravity = Gravity.CENTER })
            val stick = JoystickView(this@MainActivity, false) { course.setSteering(it) }
            stick.isEnabled = true
            addView(stick, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        row.addView(left, LinearLayout.LayoutParams(0, -1, 0.9f).apply { rightMargin = dp(8) })
        val middle = card().apply {
            val scene = FrameLayout(this@MainActivity)
            scene.addView(course, FrameLayout.LayoutParams(-1, -1))
            val map = RouteMapView(this@MainActivity, route)
            map.maxSpeedMetersPerSecond = vehiclePrefs.getFloat("max_m_s", 2.8f).coerceIn(.1f, 30f).toDouble()
            routeMap = map
            course.onTestPose = { pose -> if (mapActive) map.updatePreview(pose) }
            map.view.visibility = View.GONE
            scene.addView(map.view, FrameLayout.LayoutParams(-1, -1))
            mapBadge = text("CYAN ROVER · OFFLINE PREVIEW", 10f, ink, true).apply {
                background = shape(Color.WHITE, 8)
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
            val bottom = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val modeLabel = text("GAME", 12f, ink, true)
            bottom.addView(modeLabel, LinearLayout.LayoutParams(0, -2, 1f))
            routeStatus = text("", 11f, muted)
            val locate = button("⌖", false) { map.locateHome() }.apply { contentDescription = "Center map on fixed Home"; visibility = View.GONE }
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
            bottom.addView(viewMode, LinearLayout.LayoutParams(dp(58), dp(36)).apply { rightMargin = dp(5) })
            val reset = button("Reset", false) {
                route.reset(); map.reset(); updateRoute()
            }.apply { contentDescription = "Clear route and choose new Home from next GPS fix"; visibility = View.GONE }
            bottom.addView(locate, LinearLayout.LayoutParams(dp(42), dp(36)))
            bottom.addView(reset, LinearLayout.LayoutParams(dp(68), dp(36)).apply { leftMargin = dp(5) })
            val export = button("CSV", false) {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "telerc-route.csv")
                }
                startActivityForResult(intent, 43)
            }.apply { contentDescription = "Export recorded route and transmitted control commands as CSV"; visibility = View.GONE }
            bottom.addView(export, LinearLayout.LayoutParams(dp(58), dp(36)).apply { leftMargin = dp(5) })
            val music = button("♫", false) { }.apply {
                contentDescription = "Turn game music on or off; uses media volume"
                setOnClickListener {
                    isSelected = course.setMusic(!isSelected)
                    text = if (isSelected) "♫ ON" else "♫"
                }
            }
            musicButton = music
            bottom.addView(Switch(this@MainActivity).apply {
                text = ""; isChecked = false
                contentDescription = "Switch between Game and Test modes"
                setOnCheckedChangeListener { _, checked ->
                    course.setMode(if (checked) TestDriveView.Mode.TEST else TestDriveView.Mode.GAME)
                    modeLabel.text = if (checked) "TEST" else "GAME"
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
            }, LinearLayout.LayoutParams(-2, dp(36)))
            bottom.addView(music, LinearLayout.LayoutParams(dp(60), dp(36)).apply { leftMargin = dp(6) })
            addView(bottom, LinearLayout.LayoutParams(-1, dp(36)))
            routeStatus?.visibility = View.GONE
            addView(routeStatus, LinearLayout.LayoutParams(-1, dp(24)))
        }
        row.addView(middle, LinearLayout.LayoutParams(0, -1, 2.1f).apply { rightMargin = dp(8) })
        val right = card().apply {
            addView(text("DRIVE", 16f, ink, true).apply { gravity = Gravity.CENTER })
            addView(text("CH3  ·  forward / reverse", 11f, muted).apply { gravity = Gravity.CENTER })
            val stick = JoystickView(this@MainActivity, true) { course.setDrive(it) }
            stick.isEnabled = true
            addView(stick, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        row.addView(right, LinearLayout.LayoutParams(0, -1, 0.9f))
        root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("Test map: cyan offline rover · blue phone branch · purple live rover · live controls recorded on Controls", 11f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
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
                    locationManager.requestLocationUpdates(provider, 1000L, 2f, phoneListener)
                    locationManager.getLastKnownLocation(provider)?.let(phoneListener::onLocationChanged)
                    subscribed = true
                }
            } catch (_: Exception) { /* Keep the other location provider available. */ }
        }
        if (!subscribed) routeStatus?.text = "Enable phone location; map uses a sample start until GPS Home is set"
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPhoneLocation()
        else if (requestCode == 42) routeStatus?.text = "Precise GPS permission required for phone track"
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
        stopPhoneLocation(); mapActive = false
        routeMap?.let { if (resumed) it.onPause(); if (started) it.onStop(); it.onDestroy() }
        routeMap = null; routeStatus = null; mapBadge = null; musicButton = null
        controlsPlaceholder = null; controlsLocate = null; controlsEstimate = null; deadReckoning.reset()
    }
    private fun saveRoute() {
        try { openFileOutput("route-session.csv", MODE_PRIVATE).bufferedWriter().use { it.write(route.encode()) } }
        catch (_: Exception) { routeStatus?.text = "Could not save route locally" }
    }
    private fun linkFresh() = target != 0 && SystemClock.elapsedRealtime() - heartbeatAt < 1500
    private fun linkDiagnosis(): String {
        if (!connected.get()) return "Tap Connect first, then wait four seconds and diagnose again."
        if (linkFresh()) return "Rover heartbeat received. Link active. Control still requires Enable Control."
        if (bridgeStatusAt == 0L || SystemClock.elapsedRealtime() - bridgeStatusAt > 7000)
            return "No recent reply from the ESP32 bridge. Check that the phone stays on TeleRC-Rover Wi-Fi, " +
                "the address/UDP port are 192.168.4.1:14550, and the updated bridge sketch is running. " +
                "Wait four seconds after Connect and try again."
        if (bridgeRxBytes == 0L)
            return "ESP32 responds, but receives zero bytes from the F405. Check T3 → ESP GPIO18, " +
                "shared GND, SERIAL3_PROTOCOL=2 and SERIAL3_BAUD=115; reboot the F405 after setting them."
        if (bridgeFrames == 0L)
            return "ESP32 receives UART bytes ($bridgeRxBytes), but no complete MAVLink frames. " +
                "Check baud 115200, the ESP GPIO18 RX pin and UART3 wiring."
        return "ESP32 receives UART bytes ($bridgeRxBytes) and complete frames ($bridgeFrames), " +
            "but TeleRC has no valid autopilot heartbeat. Check that the F405 is sending MAVLink " +
            "heartbeats from SERIAL3. Frame count alone does not verify message contents."
    }
    private fun refreshUi() {
        status?.text = when {
            !connected.get() -> "DISCONNECTED"
            !linkFresh() -> "WAITING FOR HEARTBEAT"
            controlEnabled.get() -> "SYSTEM $target  •  CONTROL ON"
            else -> "SYSTEM $target  •  LINK ACTIVE"
        }
        connect?.text = if (connected.get()) "Disconnect" else "Connect"
        enable?.isEnabled = connected.get()
        enable?.text = if (controlEnabled.get()) "STOP CONTROL" else "ENABLE CONTROL"
    }
    private fun disableControl() {
        // Serialize release with periodic sends so a queued override cannot follow Stop.
        synchronized(commandLock) {
            val active = controlEnabled.getAndSet(false)
            steering = 1500; drive = 1500
            if (active && target != 0) try {
                val udp = socket; val remote = endpoint
                if (udp != null && remote != null) {
                    val neutral = Mavlink.override(0, target, 1, 1500, 1500, 1500, 1500)
                    udp.send(DatagramPacket(neutral, neutral.size, remote, endpointPort))
                    val release = Mavlink.release(1, target, 1)
                    udp.send(DatagramPacket(release, release.size, remote, endpointPort))
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
        val address = host?.text?.toString()?.trim().orEmpty()
        val valid = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(address) &&
            address.split('.').all { it.toInt() in 0..255 }
        val number = port?.text?.toString()?.toIntOrNull()
        if (!valid || number == null || number !in 1..65535) {
            status?.text = "INVALID ADDRESS OR PORT"; return
        }
        val remote = InetAddress.getByName(address)
        val udp = try { DatagramSocket(number).apply { soTimeout = 50 } }
        catch (e: Exception) { status?.text = "UDP PORT UNAVAILABLE"; return }
        getSharedPreferences("link", MODE_PRIVATE).edit().putString("host", address).putInt("port", number).apply()
        socket = udp; endpoint = remote; endpointPort = number
        target = 0; heartbeatAt = 0; bridgeStatusAt = 0; bridgeRxBytes = 0; bridgeFrames = 0
        disableControl(); connected.set(true)
        host?.isEnabled = false; port?.isEnabled = false; refreshUi()
        thread(name = "telerc-link") {
            val input = ByteArray(512); var sequence = 0; var lastSend = 0L; var lastDiscovery = 0L
            val discovery = "TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII)
            while (connected.get() && socket === udp) {
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
                        if (payload.size in 20..80 && payload.take(17).toByteArray()
                                .contentEquals("TELERC_STATUS_V1,".toByteArray(Charsets.US_ASCII))) {
                            val fields = String(payload, Charsets.US_ASCII).split(',')
                            val bytes = fields.getOrNull(1)?.toLongOrNull()
                            val frames = fields.getOrNull(2)?.toLongOrNull()
                            if (fields.size == 3 && bytes != null && frames != null &&
                                bytes >= 0 && frames >= 0 && frames <= bytes) {
                                bridgeRxBytes = bytes; bridgeFrames = frames
                                bridgeStatusAt = SystemClock.elapsedRealtime()
                            }
                        } else for (frame in Mavlink.frames(payload)) {
                            val system = Mavlink.heartbeatSystem(frame)
                            if (system != null && (target == 0 || target == system)) {
                                target = system; heartbeatAt = SystemClock.elapsedRealtime()
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
                val fresh = target != 0 && now - heartbeatAt < 1500
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
                        val sent = ControlSample(System.currentTimeMillis(), rc.one, rc.three)
                        runOnUiThread {
                            if (socket === udp) {
                                route.addCommand(sent)
                                if (page == Page.CONTROLS && mapActive && controlEnabled.get()) {
                                    val pose = deadReckoning.accept(now, rc.one, rc.three)
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
                runOnUiThread {
                    if (socket === udp && connected.get()) {
                        if (!fresh && controlEnabled.get()) disableControl()
                        refreshUi()
                    }
                }
            }
            runOnUiThread { if (socket === udp) stop() }
        }
    }
    private fun stop() {
        disableControl(); connected.set(false)
        val udp = socket; udp?.close(); socket = null; endpoint = null; target = 0; heartbeatAt = 0
        bridgeStatusAt = 0; bridgeRxBytes = 0; bridgeFrames = 0
        host?.isEnabled = true; port?.isEnabled = true; refreshUi()
    }
    override fun onPause() { resumed = false; stopPhoneLocation(); routeMap?.onPause(); testCourse?.stop(); musicButton?.apply { isSelected = false; text = "♫" }; stop(); saveRoute(); super.onPause() }
    override fun onResume() { super.onResume(); resumed = true; routeMap?.onResume(); startPhoneLocation(); testCourse?.resume(); if (::updater.isInitialized) updater.resumePendingInstall() }
    override fun onStart() { super.onStart(); started = true; routeMap?.onStart() }
    override fun onStop() { started = false; routeMap?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); routeMap?.onLowMemory() }
    override fun onDestroy() { releaseMap(); stop(); saveRoute(); updater.close(); super.onDestroy() }
}
