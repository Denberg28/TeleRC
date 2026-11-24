package io.github.denberg28.telerc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.AtomicFile
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.Reader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// UI — TeleRC Go controller with Wi-Fi ArduRover Mission Planning tab.
// Single Wi-Fi transport (GoLink) owns UDP I/O, ARM safety, rover GPS and mission upload.
class MainActivity : Activity() {
    companion object {
        private const val IMAGE_REQUEST = 41
        private const val LOCATION_REQUEST = 42
        private const val EXPORT_MISSION_REQUEST = 44
        private const val IMPORT_MISSION_REQUEST = 45
        // Serialize private wallpaper file access across activity recreations.
        private val imageExecutor = Executors.newSingleThreadExecutor()
    }

    private enum class Page { CONTROLS, MISSION }

    private var page = Page.CONTROLS
    internal lateinit var link: GoLink
    internal lateinit var updater: AppUpdater
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("go", MODE_PRIVATE) }

    // Controls screen widgets
    private var status: TextView? = null
    private var arm: Button? = null
    private var connect: Button? = null
    private var left: JoystickView? = null
    private var right: JoystickView? = null
    private var steerLabel: TextView? = null
    private var driveLabel: TextView? = null
    private var steer = 1500
    private var drive = 1500
    private var wallpaper: ImageView? = null
    private var themeBitmap: Bitmap? = null

    // Settings / Update modal state
    private var showing: Dialog? = null
    private var updateDialog: Dialog? = null
    private var updateMessage: TextView? = null
    private var responseDirty = false
    private var imageWork = false
    private var pendingImageUri: android.net.Uri? = null

    // Mission screen state (local persistence, measured GPS tracks, Wi-Fi upload)
    private val route = RouteSession()
    private var mission = MissionPlan()
    private var planner: MissionPlannerView? = null
    private var routeMap: RouteMapView? = null
    private var gpsBadge: TextView? = null
    private var mapActive = false
    private var started = false
    @Volatile private var resumed = false
    private var locationPermissionRequested = false
    private var pendingPhoneLocate = false
    private val documentBusy = AtomicBoolean(false)
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }

    private val phoneListener = LocationListener { location: Location ->
        if (!mapActive || !location.hasAccuracy() ||
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos !in 0L..10_000_000_000L
        ) return@LocationListener
        if (route.addPhone(TrackPoint(location.latitude, location.longitude, location.time), location.accuracy)) {
            updateRoute()
        }
        if (pendingPhoneLocate && location.accuracy.isFinite() && location.accuracy in 0f..50f &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
            (location.latitude != 0.0 || location.longitude != 0.0)
        ) {
            pendingPhoneLocate = false
            routeMap?.locatePhoneFix(location.latitude, location.longitude)
        }
    }

    private val refreshTask = Runnable { if (!isFinishing && !isDestroyed) refresh() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun row() = dp(48)
    private fun text(value: String, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(236, 242, 250)); includeFontPadding = false
        setLineSpacing(dp(2).toFloat(), 1f); setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
    }
    private fun button(value: String, filled: Boolean = false, click: () -> Unit) =
        AppUi.button(this, value, filled, click).apply {
            setAutoSizeTextTypeUniformWithConfiguration(12, 14, 1, TypedValue.COMPLEX_UNIT_SP)
        }
    private fun shape(color: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        getSharedPreferences("appearance", MODE_PRIVATE).edit()
            .putString("background", "DARK").putBoolean("dark", true).putString("accent", "Blue").apply()
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        ControlResponseStore.migrate(this)
        migrateLegacyWifiEndpoint()
        prefs.edit().putString("profile", "WIFI").apply()
        loadSavedTracksAndMission()

        link = GoLink(
            context = this,
            update = { ui.removeCallbacks(refreshTask); ui.post(refreshTask) },
            onRoverPosition = { lat, lon ->
                ui.post {
                    if (isFinishing || isDestroyed) return@post
                    if (route.addRover(TrackPoint(lat, lon, System.currentTimeMillis()))) {
                        saveRouteSession()
                        updateRoute()
                    }
                }
            }
        )
        updater = AppUpdater(
            activity = this,
            report = ::updateReport,
            onDownloadStart = { link.disconnect(); resetSticks(); refresh() }
        )

        page = when (savedInstanceState?.getString("page")) {
            "MISSION" -> Page.MISSION
            else -> Page.CONTROLS
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        render()
        imageTask(null) { ThemeImageStore.load(applicationContext) }
    }

    // Persistence — Retain legacy TeleRC Wi-Fi IP/port, tracks and saved mission on upgrade.
    private fun migrateLegacyWifiEndpoint() {
        if (prefs.contains("WIFI.host")) return
        val legacy = getSharedPreferences("link", MODE_PRIVATE)
        val host = legacy.getString("host", null) ?: return
        val port = legacy.getInt("port", 14550)
        val candidate = GoEndpoint(GoProfile.WIFI, host, port, 14550, 1)
        if (candidate.valid()) {
            prefs.edit().putString("WIFI.host", host).putInt("WIFI.port", port).apply()
        }
    }

    private fun loadSavedTracksAndMission() {
        try {
            val session = getFileStreamPath("route-session.csv")
            if (session.length() <= 16_000_000L) {
                route.decode(openFileInput("route-session.csv").bufferedReader().use { it.readText() })
            }
        } catch (_: Exception) {}
        try {
            val file = AtomicFile(getFileStreamPath("mission.csv"))
            mission = MissionPlan.decode(file.openRead().bufferedReader().use { it.readTextBounded(MissionPlan.MAX_CSV_CHARS) })
        } catch (_: FileNotFoundException) {
            // Fresh install has no saved mission yet.
        } catch (_: Exception) {
            Toast.makeText(this, "Saved mission could not be read. Import a backup to recover it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveRouteSession() {
        val file = AtomicFile(getFileStreamPath("route-session.csv"))
        var output: FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(route.encode().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (_: Exception) {
            file.failWrite(output)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name)
        routeMap?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun endpoint() = GoEndpoint(
        profile = GoProfile.WIFI,
        host = prefs.getString("WIFI.host", "192.168.4.1") ?: "192.168.4.1",
        port = prefs.getInt("WIFI.port", 14550),
        localPort = prefs.getInt("WIFI.local", 14550),
        system = prefs.getInt("WIFI.system", 1)
    )

    private fun phoneConnect() {
        if (link.snapshot().connected) {
            link.disconnect(); resetSticks(); refresh(); return
        }
        val value = endpoint()
        if (value.valid()) {
            link.connect(value); refresh()
        } else {
            connectionDetails()
        }
    }

    private fun switchTo(next: Page) {
        if (page == next) return
        link.stop(true)
        resetSticks()
        releaseMap()
        page = next
        link.setMissionForeground(page == Page.MISSION)
        render()
    }

    private fun resetSticks() {
        left?.reset(); right?.reset(); steer = 1500; drive = 1500; refreshLabels()
    }

    private fun dismissSheet() {
        showing?.setOnDismissListener(null); showing?.dismiss(); showing = null
        updateDialog = null; updateMessage = null
    }

    // UI — Root shell. Controls keeps Go's wallpaper + translucent dials; Mission adds the map/plan tab.
    private fun render() {
        dismissSheet()
        resetSticks()
        releaseMap()
        left = null; right = null; status = null; arm = null; connect = null
        steerLabel = null; driveLabel = null; planner = null; gpsBadge = null

        val frame = FrameLayout(this)
        wallpaper = backgroundImage().apply { tag = "go-wallpaper" }
        frame.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        val scrimColor = if (page == Page.MISSION) 0xE60B131B.toInt() else 0x28071120
        frame.addView(View(this).apply { setBackgroundColor(scrimColor) }, FrameLayout.LayoutParams(-1, -1))

        val root = LinearLayout(this).apply {
            keepScreenOn = true
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    v.setPadding(dp(12) + safe.left, dp(8) + safe.top, dp(12) + safe.right, dp(8) + safe.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(
                        dp(12) + insets.systemWindowInsetLeft, dp(8) + insets.systemWindowInsetTop,
                        dp(12) + insets.systemWindowInsetRight, dp(8) + insets.systemWindowInsetBottom
                    )
                }
                insets
            }
        }

        if (page == Page.MISSION) {
            renderMission(root)
        } else {
            renderControls(root)
        }
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        link.setMissionForeground(page == Page.MISSION)
        refresh()
    }

    // UI — TeleRC Go Controls screen (Wi-Fi proportional sticks, status and single ARM/DISARM).
    private fun renderControls(root: LinearLayout) {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        header.addView(text("TeleRC v${BuildConfig.VERSION_NAME}", 17f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            contentDescription = "TeleRC v${BuildConfig.VERSION_NAME}; check for updates"
            setOnClickListener { checkUpdates() }
            setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, TypedValue.COMPLEX_UNIT_SP)
            maxLines = 1; setPadding(0, 0, dp(8), 0)
            val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = a.getDrawable(0); a.recycle()
        }, LinearLayout.LayoutParams(0, row(), 1f))

        header.addView(button("Mission") { switchTo(Page.MISSION) }.apply {
            tag = "go-mission"; maxLines = 1
            contentDescription = "Open Wi-Fi Mission Planning tab; stops manual driving"
        }, LinearLayout.LayoutParams(dp(96), row()).apply { rightMargin = dp(8) })

        connect = button("Phone connect", true) { phoneConnect() }.apply {
            contentDescription = "Phone Wi-Fi connection"
        }
        header.addView(connect, LinearLayout.LayoutParams(dp(142), row()))
        header.addView(button("Settings") { settings() }, LinearLayout.LayoutParams(dp(92), row()).apply { leftMargin = dp(8) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val controls = LinearLayout(this).apply { isBaselineAligned = false }
        fun stick(vertical: Boolean) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), dp(12))
            val label = text(if (vertical) "DRIVE · CH2" else "STEER · CH1", 13f).apply {
                gravity = Gravity.CENTER; maxLines = 1
                tag = if (vertical) "go-drive-label" else "go-steer-label"
                setAutoSizeTextTypeUniformWithConfiguration(11, 13, 1, TypedValue.COMPLEX_UNIT_SP)
            }
            if (vertical) driveLabel = label else steerLabel = label
            addView(label, LinearLayout.LayoutParams(-1, dp(28)))
            val joystick = JoystickView(
                context = this@MainActivity,
                vertical = vertical,
                translucent = true,
                receiverDeadbandUs = ControlResponse.GO_RECEIVER_DEADBAND_US,
                proportionalResponse = true
            ) { value ->
                if (vertical) drive = value else steer = value
                link.axes(steer, drive)
                refreshLabels()
            }
            if (vertical) this@MainActivity.right = joystick else this@MainActivity.left = joystick
            addView(joystick, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        }
        controls.addView(stick(false), LinearLayout.LayoutParams(0, -1, 1.25f))

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(12))
        }
        status = text("Join rover Wi-Fi, then Phone connect", 13f).apply {
            gravity = Gravity.CENTER; maxLines = 3; ellipsize = TextUtils.TruncateAt.END
            background = shape(0xc0112033.toInt()); setPadding(dp(10), dp(8), dp(10), dp(8)); tag = "go-status"
        }
        center.addView(status, LinearLayout.LayoutParams(-1, -2))
        arm = button("ARM", true) { resetSticks(); link.toggleArm(); refresh() }.apply { tag = "go-arm" }
        center.addView(arm, LinearLayout.LayoutParams(-1, row()).apply { topMargin = dp(8) })

        controls.addView(center, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        controls.addView(stick(true), LinearLayout.LayoutParams(0, -1, 1.25f))
        root.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
    }

    // UI — Mission Planning tab (local CSV/map editing + acknowledged ArduRover Wi-Fi upload).
    private fun renderMission(root: LinearLayout) {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        titleColumn.addView(text("Mission plan", 16f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1
        })
        gpsBadge = text("NO GPS · SAMPLE AREA", 11f).apply {
            setTextColor(AppColors.muted(this@MainActivity)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        titleColumn.addView(gpsBadge)
        header.addView(titleColumn, LinearLayout.LayoutParams(0, row(), 1f).apply { rightMargin = dp(8) })

        header.addView(button("Controls") { switchTo(Page.CONTROLS) }.apply {
            tag = "go-controls"; maxLines = 1
            contentDescription = "Return to TeleRC Controls; cancels active mission upload"
        }, LinearLayout.LayoutParams(dp(96), row()).apply { rightMargin = dp(8) })

        connect = button("Phone connect", true) { phoneConnect() }
        header.addView(connect, LinearLayout.LayoutParams(dp(132), row()).apply { rightMargin = dp(8) })
        header.addView(button("Locate", false) { locatePhone() }.apply {
            contentDescription = "Locate phone GPS on mission map"
        }, LinearLayout.LayoutParams(dp(84), row()).apply { rightMargin = dp(8) })
        header.addView(button("Settings") { settings() }, LinearLayout.LayoutParams(dp(86), row()))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val map = RouteMapView(this, route)
        routeMap = map; mapActive = true
        planner = MissionPlannerView(
            context = this,
            plan = mission,
            map = map,
            save = ::saveMission,
            exportPlan = { missionDocument(EXPORT_MISSION_REQUEST, true) },
            importPlan = { missionDocument(IMPORT_MISSION_REQUEST, false) },
            uploadPlan = ::confirmMissionUpload,
            cancelUpload = { link.cancelMissionUpload("Upload cancelled") }
        )
        root.addView(planner, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        if (started) map.onStart()
        if (resumed) map.onResume()
        startPhoneLocation()
        updateRoute()
    }

    private fun refreshLabels() {
        steerLabel?.text = "STEER · $steer"
        driveLabel?.text = "DRIVE · $drive"
    }

    private fun refresh() {
        if (!::link.isInitialized) return
        val s = link.snapshot()
        connect?.isEnabled = true
        connect?.text = if (s.connected) "Disconnect" else "Phone connect"
        if (page == Page.MISSION) {
            planner?.transferState(s.missionStatus, s.missionActive, s.missionReady)
            refreshGpsLabel()
            return
        }
        status?.text = s.text
        arm?.text = when {
            s.handset -> "Radio control"
            s.pending -> "Cancel arming"
            s.resume -> "Resume control"
            s.armed -> "DISARM"
            else -> "ARM"
        }
        arm?.isEnabled = !imageWork && !s.handset && (s.pending || s.fresh && s.verified && !s.stopped)
        val canDrive = !imageWork && s.fresh && s.enabled && s.armed && s.driveReady && !s.handset
        if (left?.isEnabled != canDrive) left?.isEnabled = canDrive
        if (right?.isEnabled != canDrive) right?.isEnabled = canDrive
    }

    // Mission — Explicit confirmation before Wi-Fi upload. Never emits ARM, AUTO or MISSION_START.
    private fun confirmMissionUpload() {
        val snap = link.snapshot()
        val snapshot = mission.waypoints
        if (snapshot.isEmpty() || !snap.missionReady || snap.missionActive) return
        AlertDialog.Builder(this)
            .setTitle("Upload ${snapshot.size} waypoints to rover ${snap.targetSystem}?")
            .setMessage(
                "Replaces the onboard mission through the ESP32-S3 ArduRover Wi-Fi bridge (TeleRCBridge). " +
                    "Keep the rover disarmed and Switch A (CH7) permitted. Upload does not arm or start navigation; " +
                    "verify the onboard route and select AUTO separately on your transmitter. " +
                    "A failed or cancelled transfer leaves the onboard plan unverified."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Upload") { _, _ ->
                if (mission.waypoints == snapshot) {
                    link.beginMissionUpload(snapshot)
                }
                refresh()
            }.show()
    }

    private fun saveMission() {
        link.missionEdited()
        val file = AtomicFile(getFileStreamPath("mission.csv"))
        var output: FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(mission.encode().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (_: Exception) {
            file.failWrite(output)
            Toast.makeText(this, "Could not save mission. Export a backup before closing.", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun missionDocument(request: Int, export: Boolean) {
        val intent = Intent(if (export) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (export) "text/csv" else "*/*"
            if (export) putExtra(Intent.EXTRA_TITLE, "TeleRC-mission.csv")
        }
        try {
            startActivityForResult(intent, request)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No document provider available", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateRoute() {
        routeMap?.draw()
        refreshGpsLabel()
    }

    private fun refreshGpsLabel() {
        val gps = route.rover.lastOrNull()
        val seconds = gps?.let { ((System.currentTimeMillis() - it.timeMs).coerceAtLeast(0L) / 1000L) }
        val locationLabel = when {
            seconds != null -> "ROVER GPS · ${seconds}s ago"
            route.home != null -> "PHONE MAP · NO ROVER GPS"
            else -> "NO GPS · SAMPLE AREA"
        }
        gpsBadge?.text = locationLabel
    }

    private fun locatePhone() {
        if (!mapActive || !resumed) return
        pendingPhoneLocate = true
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequested = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_REQUEST)
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            AlertDialog.Builder(this).setTitle("Phone location is off")
                .setMessage("Enable location services to center the map on your phone. Return to TeleRC after enabling it.")
                .setNegativeButton("Cancel") { _, _ -> pendingPhoneLocate = false }
                .setOnCancelListener { pendingPhoneLocate = false }
                .setPositiveButton("Open location settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.show()
            return
        }
        gpsBadge?.text = "Acquiring phone GPS fix…"
        Toast.makeText(this, "Acquiring current phone GPS fix", Toast.LENGTH_SHORT).show()
        startPhoneLocation()
    }

    private fun startPhoneLocation() {
        if (!resumed || !mapActive) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!locationPermissionRequested) {
                locationPermissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_REQUEST)
            }
            gpsBadge?.text = "Grant location permission for phone map"
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
            } catch (_: Exception) {}
        }
        if (!subscribed) gpsBadge?.text = "Enable phone location for Home"
    }

    private fun stopPhoneLocation() {
        try { locationManager.removeUpdates(phoneListener) } catch (_: Exception) {}
    }

    private fun releaseMap() {
        pendingPhoneLocate = false
        stopPhoneLocation()
        mapActive = false
        routeMap?.let {
            if (resumed) it.onPause()
            if (started) it.onStop()
            it.onDestroy()
        }
        routeMap = null
        planner = null
    }

    // UI — Go modal sheets. Fixed intrinsic heights keep fields above the soft keyboard.
    private fun dialog(title: String, content: View, positive: Pair<String, () -> Unit>? = null): Dialog {
        link.stop(true)
        link.cancelMissionUpload("Settings opened; upload cancelled")
        resetSticks()
        dismissSheet()
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = shape(AppColors.surface(this@MainActivity))
            tag = "go-sheet"
        }
        panel.addView(text(title, 18f).apply {
            gravity = Gravity.CENTER; maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(14, 18, 1, TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(-1, dp(28)))
        panel.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val footer = LinearLayout(this).apply { isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(button(if (positive == null) "Done" else "Cancel") { d.dismiss() }, LinearLayout.LayoutParams(0, row(), 1f))
        positive?.let { (label, action) ->
            footer.addView(button(label, true) { action() }, LinearLayout.LayoutParams(0, row(), 1f).apply { leftMargin = dp(8) })
        }
        panel.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        d.setContentView(panel)
        val visible = Rect().also { window.decorView.getWindowVisibleDisplayFrame(it) }
        val availableWidth = if (visible.width() > 0) visible.width() else resources.displayMetrics.widthPixels
        val width = minOf(dp(420), availableWidth - dp(32)).coerceAtLeast(1)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setDimAmount(.48f)
            setGravity(Gravity.CENTER); attributes = attributes.apply { x = 0; y = 0; windowAnimations = 0 }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setLayout(width, panel.measuredHeight)
        }
        showing = d
        d.setOnDismissListener { if (showing === d) { showing = null; updateDialog = null; updateMessage = null } }
        d.show(); refresh(); return d
    }

    private fun settings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val actions = listOf(
            "Wi-Fi settings" to { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            "Connection details" to { connectionDetails() },
            "Sensitivity" to { sensitivity() },
            "Background" to { backgroundSettings() }
        )
        for ((index, items) in actions.chunked(2).withIndex()) {
            val line = LinearLayout(this).apply { isBaselineAligned = false }
            for ((column, item) in items.withIndex()) {
                line.addView(
                    button(item.first) { item.second() },
                    LinearLayout.LayoutParams(0, row(), 1f).apply { if (column > 0) leftMargin = dp(8) }
                )
            }
            body.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(8) })
        }
        dialog("Controls settings", body)
    }

    private fun backgroundImage() = ImageView(this).apply {
        themeBitmap?.let { setImageBitmap(it) } ?: setImageResource(R.drawable.go_background)
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun backgroundSettings() {
        if (imageWork) { Toast.makeText(this, "Loading background…", Toast.LENGTH_SHORT).show(); return }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(backgroundImage().apply { tag = "go-background-preview" }, LinearLayout.LayoutParams(-1, dp(80)))
        val actions = LinearLayout(this).apply { isBaselineAligned = false }
        actions.addView(button("Choose image") { chooseBackground() }, LinearLayout.LayoutParams(0, row(), 1f))
        actions.addView(
            button("Restore default") {
                dismissSheet()
                imageTask("Default background restored") { ThemeImageStore.remove(applicationContext); null }
            }.apply { isEnabled = themeBitmap != null || ThemeImageStore.exists(this@MainActivity) },
            LinearLayout.LayoutParams(0, row(), 1f).apply { leftMargin = dp(8) }
        )
        body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        dialog("Background", body)
    }

    @Suppress("DEPRECATION")
    private fun chooseBackground() {
        if (imageWork) return
        link.stop(true); resetSticks(); dismissSheet(); refresh()
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, IMAGE_REQUEST)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No image picker available", Toast.LENGTH_LONG).show()
        }
    }

    private fun imageTask(success: String?, work: () -> Bitmap?) {
        if (imageWork) return
        if (success != null) { link.stop(true); resetSticks() }
        imageWork = true; refresh()
        imageExecutor.execute {
            var failure: String? = null
            val image = try {
                work()
            } catch (error: Exception) {
                failure = error.message ?: "Image could not be imported"; null
            } catch (_: OutOfMemoryError) {
                failure = "Image is too large; choose a smaller photo"; null
            }
            ui.post {
                imageWork = false
                if (!isFinishing && !isDestroyed) {
                    if (failure == null) {
                        themeBitmap = image
                        if (image == null) wallpaper?.setImageResource(R.drawable.go_background)
                        else wallpaper?.setImageBitmap(image)
                    }
                    refresh()
                    (failure ?: success)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                    pendingImageUri?.let { uri ->
                        pendingImageUri = null
                        imageTask("Background image saved") { ThemeImageStore.importImage(applicationContext, uri) }
                    }
                }
            }
        }
    }

    private fun sensitivity() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var value = ControlResponseStore.read(this)
        fun slider(label: String, initial: Int, save: (Int) -> Unit) {
            val caption = text("$label · $initial%", 13f); body.addView(caption, LinearLayout.LayoutParams(-1, dp(22)))
            body.addView(SeekBar(this).apply {
                max = 75; progress = initial - 25; contentDescription = "$label sensitivity"; setPadding(dp(8), 0, dp(8), 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                    override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                        caption.text = "$label · ${p + 25}%"
                        if (user) { responseDirty = true; save(p + 25) }
                    }
                })
            }, LinearLayout.LayoutParams(-1, row()))
        }
        slider("Steering", value.steeringPercent) { value = value.copy(steeringPercent = it); ControlResponseStore.save(this, value) }
        body.addView(View(this), LinearLayout.LayoutParams(-1, dp(8)))
        slider("Drive", value.drivePercent) { value = value.copy(drivePercent = it); ControlResponseStore.save(this, value) }
        dialog("Sensitivity", body).setOnDismissListener {
            showing = null
            if (!isFinishing && !isDestroyed) { responseDirty = false; render() }
        }
    }

    private fun connectionDetails() {
        val value = endpoint(); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun field(parent: LinearLayout, label: String, content: String, numeric: Boolean = false) = LinearLayout(this).run {
            orientation = LinearLayout.VERTICAL
            addView(text(label, 12f), LinearLayout.LayoutParams(-1, dp(22)))
            val edit = EditText(this@MainActivity).apply {
                contentDescription = label; setText(content); setSingleLine(); textSize = 16f
                setTextColor(Color.rgb(236, 242, 250)); setPadding(dp(8), 0, dp(8), 0); minWidth = 0; minimumWidth = 0
                inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }
            addView(edit, LinearLayout.LayoutParams(-1, row()))
            parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { if (parent.childCount > 0) leftMargin = dp(12) })
            edit
        }
        val first = LinearLayout(this).apply { isBaselineAligned = false }; body.addView(first)
        val host = field(first, "IPv4", value.host); val remote = field(first, "Rover port", value.port.toString(), true)
        val second = LinearLayout(this).apply { isBaselineAligned = false }
        body.addView(second, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val local = field(second, "Phone port", value.localPort.toString(), true)
        val id = field(second, "System ID", value.system.toString(), true)
        dialog("Connection details", body, "Save" to {
            val v = GoEndpoint(
                GoProfile.WIFI, host.text.toString().trim(),
                remote.text.toString().toIntOrNull() ?: 0,
                local.text.toString().toIntOrNull() ?: 0,
                id.text.toString().toIntOrNull() ?: 0
            )
            if (v.valid()) {
                link.disconnect()
                prefs.edit()
                    .putString("profile", "WIFI").putString("WIFI.host", v.host)
                    .putInt("WIFI.port", v.port).putInt("WIFI.local", v.localPort).putInt("WIFI.system", v.system)
                    .apply()
                render()
            } else {
                host.error = "Check address, ports and ID"
            }
        })
    }

    private fun checkUpdates() {
        val message = text("Checking for updates…", 14f).apply {
            gravity = Gravity.CENTER; minHeight = dp(76); setPadding(dp(4), dp(8), dp(4), dp(8)); tag = "go-update-message"
        }
        val d = dialog("TeleRC v${BuildConfig.VERSION_NAME}", message)
        updateDialog = d; updateMessage = message; updater.check()
    }

    private fun updateReport(value: String) {
        if (isFinishing || isDestroyed) return
        if (showing === updateDialog && updateDialog != null) updateMessage?.text = value
        else if (hasWindowFocus()) Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (pendingPhoneLocate) locatePhone() else startPhoneLocation()
        } else if (requestCode == LOCATION_REQUEST) {
            pendingPhoneLocate = false
            gpsBadge?.text = "Location permission required for phone GPS"
            Toast.makeText(this, "Precise location permission is required", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Activity result used for Android document pickers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == IMAGE_REQUEST) {
            if (imageWork) { pendingImageUri = uri; return }
            imageTask("Background image saved") { ThemeImageStore.importImage(applicationContext, uri) }
            return
        }
        if (requestCode != EXPORT_MISSION_REQUEST && requestCode != IMPORT_MISSION_REQUEST) return
        if (!documentBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "A mission file operation is still running", Toast.LENGTH_SHORT).show()
            return
        }
        val exportSnapshot = mission.encode()
        thread(name = "telerc-mission-document", isDaemon = true) {
            try {
                val imported = if (requestCode == EXPORT_MISSION_REQUEST) {
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                    output.bufferedWriter().use { it.write(exportSnapshot) }
                    null
                } else {
                    val input = contentResolver.openInputStream(uri) ?: error("No input stream")
                    val csv = input.bufferedReader().use { it.readTextBounded(MissionPlan.MAX_CSV_CHARS) }
                    MissionPlan.decode(csv)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    when {
                        imported == null -> Toast.makeText(this, "Mission exported", Toast.LENGTH_SHORT).show()
                        page != Page.MISSION -> Toast.makeText(this, "Import cancelled: reopen Mission and retry", Toast.LENGTH_LONG).show()
                        else -> AlertDialog.Builder(this).setTitle("Replace local plan?")
                            .setMessage("Import ${imported.waypoints.size} waypoints? Export your current plan first if needed.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Import") { _, _ ->
                                mission.replaceWith(imported); saveMission(); planner?.refresh(fit = true)
                            }.show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) Toast.makeText(
                        this,
                        "Could not read or write mission. Use TeleRC waypoint CSV (up to 100 points).",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                documentBusy.set(false)
            }
        }
    }

    private fun Reader.readTextBounded(limit: Int): String {
        val buffer = CharArray(limit + 1)
        var used = 0
        while (used < buffer.size) {
            val count = read(buffer, used, buffer.size - used)
            if (count < 0) break
            used += count
        }
        require(used <= limit) { "Mission file too large" }
        return String(buffer, 0, used)
    }

    @Deprecated("Platform back callback")
    override fun onBackPressed() {
        if (page == Page.MISSION) switchTo(Page.CONTROLS) else super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        link.stop(true)
        link.cancelMissionUpload("Configuration changed; upload cancelled")
        render()
    }

    override fun onStart() {
        super.onStart(); started = true; routeMap?.onStart()
    }

    override fun onResume() {
        super.onResume(); resumed = true; routeMap?.onResume()
        if (responseDirty) { responseDirty = false; render() }
        if (::link.isInitialized) link.foreground(true)
        if (::updater.isInitialized) updater.resumePendingInstall()
        if (mapActive) startPhoneLocation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::updater.isInitialized) updater.resumePendingInstall()
    }

    override fun onPause() {
        resumed = false
        stopPhoneLocation()
        routeMap?.onPause()
        if (::link.isInitialized) link.foreground(false)
        dismissSheet(); resetSticks()
        super.onPause()
    }

    override fun onStop() {
        started = false; routeMap?.onStop(); super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory(); routeMap?.onLowMemory()
    }

    override fun onDestroy() {
        releaseMap()
        if (::link.isInitialized) link.close()
        if (::updater.isInitialized) updater.close()
        ui.removeCallbacksAndMessages(null)
        dismissSheet()
        super.onDestroy()
    }
}
