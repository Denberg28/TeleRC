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
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
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

    // Settings / Update / Calibration modal state
    private var showing: Dialog? = null
    private var updateDialog: Dialog? = null
    private var updateMessage: TextView? = null
    private var calDialog: Dialog? = null
    private var calStatus: TextView? = null
    private var calArmBtn: Button? = null
    private var calPulseBtn: Button? = null
    private var calTurnBtn: Button? = null
    private var calStopBtn: Button? = null
    private var responseDirty = false
    private var imageWork = false
    private var pendingImageUri: android.net.Uri? = null

    // Mission screen state (1m calibration gate, phone initial origin + orientation, 1m step planner)
    private val route = RouteSession()
    private var mission = MissionPlan()
    private val plannedSteps = mutableListOf<StepCommand>()
    private var planOrigin: PlanOrigin? = null
    private var liveHeadingDeg: Double? = null
    private var phoneHeading: PhoneHeading? = null
    private val stepPrefs by lazy { getSharedPreferences("step_cal", MODE_PRIVATE) }
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
            if (planOrigin == null || (!planOrigin!!.fromPhoneGps && plannedSteps.isEmpty())) {
                lockPhoneOrigin(silent = true)
            }
        }
        if (pendingPhoneLocate && location.accuracy.isFinite() && location.accuracy in 0f..50f &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
            (location.latitude != 0.0 || location.longitude != 0.0)
        ) {
            pendingPhoneLocate = false
            routeMap?.locatePhoneFix(location.latitude, location.longitude)
            lockPhoneOrigin(silent = true)
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

    private fun clearCalibrationRefs() {
        calDialog = null; calStatus = null
        calArmBtn = null; calPulseBtn = null; calTurnBtn = null; calStopBtn = null
    }

    private fun dismissSheet() {
        val wasCal = showing === calDialog && calDialog != null
        showing?.setOnDismissListener(null); showing?.dismiss(); showing = null
        updateDialog = null; updateMessage = null
        clearCalibrationRefs()
        if (wasCal && ::link.isInitialized) {
            link.stopStepPlan("Calibration closed · stopped")
        }
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

    // UI — Minimal Mission Planning tab: clear map + 1m calibration gate + phone-origin 1m step pad.
    private fun renderMission(root: LinearLayout) {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        titleColumn.addView(text("Mission", 16f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1
        })
        gpsBadge = text("Step 1: Calibrate 1m", 11f).apply {
            setTextColor(AppColors.muted(this@MainActivity)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        titleColumn.addView(gpsBadge)
        header.addView(titleColumn, LinearLayout.LayoutParams(0, row(), 1f).apply { rightMargin = dp(8) })

        header.addView(button("Controls") { switchTo(Page.CONTROLS) }.apply {
            tag = "go-controls"; maxLines = 1
            contentDescription = "Return to TeleRC Controls; stops step run and mission upload"
        }, LinearLayout.LayoutParams(dp(92), row()).apply { rightMargin = dp(6) })

        connect = button("Phone connect", true) { phoneConnect() }
        header.addView(connect, LinearLayout.LayoutParams(dp(128), row()).apply { rightMargin = dp(6) })
        header.addView(button("Settings") { settings() }, LinearLayout.LayoutParams(dp(84), row()))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val map = RouteMapView(this, route)
        routeMap = map; mapActive = true
        planner = MissionPlannerView(
            context = this,
            plan = mission,
            map = map,
            steps = plannedSteps,
            onCalibrate = ::showStepCalibration,
            onLockOrigin = { lockPhoneOrigin(silent = false) },
            onStepTapped = ::handleStepTap,
            onUndoStep = ::undoLastStep,
            onClearAll = ::clearStepPlan,
            onArmToggle = { link.toggleArm(); refresh() },
            onRunPlan = ::confirmRunStepPlan,
            onStopRun = { link.stopStepPlan("Stopped by operator"); refresh() },
            onMoreMenu = ::showMissionMoreMenu
        ).also { it.setCalibrated(readStepCalibration().calibrated) }
        root.addView(planner, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        if (started) map.onStart()
        if (resumed) map.onResume()
        startPhoneHeading()
        startPhoneLocation()
        planOrigin?.let { map.showOrigin(it, center = false) }
        refreshOriginBanner()
        updateRoute()
    }

    private fun startPhoneHeading() {
        val sensor = phoneHeading ?: PhoneHeading(this) { heading ->
            liveHeadingDeg = heading
            if (page == Page.MISSION) refreshOriginBanner()
        }.also { phoneHeading = it }
        if (resumed && page == Page.MISSION) sensor.start()
    }

    private fun stopPhoneHeading() {
        phoneHeading?.stop()
    }

    private fun readStepCalibration(): StepCalibration = StepCalibration(
        metersPerSecond = stepPrefs.getFloat("speed_mps", 0.65f).toDouble(),
        drivePercent = stepPrefs.getInt("drive_pct", 35),
        steerPercent = stepPrefs.getInt("steer_pct", 45),
        turn90Ms = stepPrefs.getLong("turn90_ms", 800L),
        pauseMs = stepPrefs.getLong("pause_ms", 300L),
        calibrated = stepPrefs.getBoolean("calibrated", false)
    )

    private fun readCalibrationPulseMs(): Long =
        stepPrefs.getLong("test_pulse_ms", 1500L).coerceIn(StepCalibration.MIN_PULSE_MS, StepCalibration.MAX_PULSE_MS)

    private fun saveStepCalibration(cal: StepCalibration, testPulseMs: Long = readCalibrationPulseMs()) {
        stepPrefs.edit()
            .putFloat("speed_mps", cal.safeSpeedMps.toFloat())
            .putInt("drive_pct", cal.safeDrivePercent)
            .putInt("steer_pct", cal.safeSteerPercent)
            .putLong("turn90_ms", cal.safeTurn90Ms)
            .putLong("pause_ms", cal.safePauseMs)
            .putBoolean("calibrated", cal.calibrated)
            .putLong("test_pulse_ms", testPulseMs.coerceIn(StepCalibration.MIN_PULSE_MS, StepCalibration.MAX_PULSE_MS))
            .apply()
    }

    private fun lockPhoneOrigin(silent: Boolean) {
        if (!silent) locatePhone()
        val phoneFix = route.phone.lastOrNull() ?: route.home
        val lat = phoneFix?.latitude ?: 13.6218
        val lon = phoneFix?.longitude ?: 123.1948
        val heading = liveHeadingDeg ?: planOrigin?.normalizedHeading ?: 0.0
        val locked = PlanOrigin(lat, lon, heading, fromPhoneGps = phoneFix != null)
        planOrigin = locked
        routeMap?.showOrigin(locked, center = !silent)
        if (plannedSteps.isNotEmpty()) {
            rebuildPlanFromSteps(locked)
        }
        refreshOriginBanner()
        if (!silent) {
            val src = if (locked.fromPhoneGps) "Phone GPS" else "Sample start (indoors)"
            Toast.makeText(this, "Start locked · $src · ${StepGeometry.compassLabel(locked.normalizedHeading)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensurePlanOrigin(): PlanOrigin {
        planOrigin?.let { return it }
        lockPhoneOrigin(silent = true)
        return planOrigin!!
    }

    private fun rebuildPlanFromSteps(origin: PlanOrigin) {
        val projected = StepGeometry.projectWaypoints(origin, plannedSteps)
        val nextPlan = MissionPlan()
        projected.forEach(nextPlan::add)
        mission.replaceWith(nextPlan)
        saveMission()
        planner?.refresh(fit = false)
    }

    private fun handleStepTap(direction: StepDirection) {
        if (!readStepCalibration().calibrated) {
            showStepCalibration()
            return
        }
        if (plannedSteps.size >= StepRunner.MAX_STEPS || mission.waypoints.size >= MissionPlan.MAX_WAYPOINTS) {
            Toast.makeText(this, "Maximum 100 steps reached", Toast.LENGTH_SHORT).show()
            return
        }
        val origin = ensurePlanOrigin()
        plannedSteps.add(StepCommand(direction, 1.0))
        rebuildPlanFromSteps(origin)
        routeMap?.showOrigin(origin, center = false)
        refreshOriginBanner()
        refresh()
    }

    private fun undoLastStep() {
        if (plannedSteps.isNotEmpty()) {
            plannedSteps.removeAt(plannedSteps.lastIndex)
            planOrigin?.let(::rebuildPlanFromSteps) ?: run {
                if (mission.waypoints.isNotEmpty()) {
                    mission.remove(mission.waypoints.lastIndex)
                    saveMission()
                    planner?.refresh(fit = false)
                }
            }
        } else if (mission.waypoints.isNotEmpty()) {
            mission.remove(mission.waypoints.lastIndex)
            saveMission()
            planner?.refresh(fit = false)
        }
        refresh()
    }

    private fun clearStepPlan() {
        link.stopStepPlan("Plan cleared · stopped")
        plannedSteps.clear()
        mission.clear()
        saveMission()
        planner?.refresh(fit = false)
        refresh()
    }

    private fun confirmRunStepPlan() {
        val cal = readStepCalibration()
        if (!cal.calibrated) {
            showStepCalibration()
            return
        }
        val snap = link.snapshot()
        val stepSnapshot = plannedSteps.toList()
        if (stepSnapshot.isEmpty() || !snap.stepReady || snap.stepActive) return
        AlertDialog.Builder(this)
            .setTitle("Run ${stepSnapshot.size} open-loop 1m steps?")
            .setMessage(
                "Commands CH1/CH2 over Wi-Fi without joystick input (no rover GPS feedback): " +
                    "1m = ${cal.oneMeterMs} ms (${String.format(java.util.Locale.US, "%.2f", cal.safeSpeedMps)} m/s), " +
                    "Drive ${cal.safeDrivePercent}%, Steer ${cal.safeSteerPercent}%, 90° Turn ${cal.safeTurn90Ms} ms. " +
                    "Keep clear space or raised wheels and be ready to tap STOP or flip Switch A."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Run Plan") { _, _ ->
                if (plannedSteps == stepSnapshot) {
                    link.beginStepPlan(stepSnapshot, cal)
                }
                refresh()
            }.show()
    }

    // Control — Guided 1-meter calibration modal. Supports live ARM + forward/90° test pulses before saving.
    private fun showStepCalibration() {
        val cal = readStepCalibration()
        val initialPulseMs = readCalibrationPulseMs()
        val initialMeasured = if (cal.calibrated) cal.safeSpeedMps * (initialPulseMs / 1000.0) else 1.0
        val initialHeading = ((planOrigin?.normalizedHeading ?: liveHeadingDeg ?: 0.0).toInt() % 360)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val statusView = text("Connect Wi-Fi & ARM for live test pulse, or enter measured 1m values.", 12f).apply {
            setTextColor(AppColors.muted(this@MainActivity))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        body.addView(statusView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        val pulseRow = LinearLayout(this).apply { isBaselineAligned = false }
        val armBtn = button("ARM", false) { link.toggleArm(); refresh() }
        var pulseInput: EditText? = null
        var distInput: EditText? = null
        var driveInput: EditText? = null
        var turnInput: EditText? = null
        var headingInput: EditText? = null

        fun currentDraftCal(): StepCalibration = StepCalibration(
            metersPerSecond = cal.safeSpeedMps,
            drivePercent = driveInput?.text?.toString()?.toIntOrNull() ?: cal.safeDrivePercent,
            steerPercent = cal.safeSteerPercent,
            turn90Ms = turnInput?.text?.toString()?.toLongOrNull() ?: cal.safeTurn90Ms,
            pauseMs = cal.safePauseMs,
            calibrated = cal.calibrated
        )

        val testPulseBtn = button("Test Pulse", true) {
            val ms = pulseInput?.text?.toString()?.toLongOrNull() ?: initialPulseMs
            if (ms !in StepCalibration.MIN_PULSE_MS..StepCalibration.MAX_PULSE_MS) {
                pulseInput?.error = "300–5000 ms"
                return@button
            }
            if (!link.beginCalibrationPulse(false, ms, currentDraftCal())) {
                Toast.makeText(this, "ARM rover with Switch A permitted to run calibration pulse", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
        val testTurnBtn = button("Test 90°", false) {
            val ms = turnInput?.text?.toString()?.toLongOrNull() ?: cal.safeTurn90Ms
            if (ms !in 200L..3000L) {
                turnInput?.error = "200–3000 ms"
                return@button
            }
            if (!link.beginCalibrationPulse(true, ms, currentDraftCal())) {
                Toast.makeText(this, "ARM rover with Switch A permitted to test 90° turn", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
        val stopBtn = button("STOP", true) {
            link.stopStepPlan("Calibration pulse stopped")
            refresh()
        }.apply {
            background = shape(Color.rgb(168, 42, 42), 10)
            setTextColor(Color.WHITE)
        }
        pulseRow.addView(armBtn, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })
        pulseRow.addView(testPulseBtn, LinearLayout.LayoutParams(0, dp(42), 1.15f).apply { rightMargin = dp(4) })
        pulseRow.addView(testTurnBtn, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })
        pulseRow.addView(stopBtn, LinearLayout.LayoutParams(0, dp(42), 0.9f))
        body.addView(pulseRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        fun field(parent: LinearLayout, label: String, content: String, decimal: Boolean = false) = LinearLayout(this).run {
            orientation = LinearLayout.VERTICAL
            addView(text(label, 11f), LinearLayout.LayoutParams(-1, dp(18)))
            val edit = EditText(this@MainActivity).apply {
                contentDescription = label; setText(content); setSingleLine(); textSize = 14f
                setTextColor(Color.rgb(236, 242, 250)); setPadding(dp(8), 0, dp(8), 0); minWidth = 0; minimumWidth = 0
                inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }
            addView(edit, LinearLayout.LayoutParams(-1, dp(42)))
            parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { if (parent.childCount > 0) leftMargin = dp(8) })
            edit
        }

        val r1 = LinearLayout(this).apply { isBaselineAligned = false }
        body.addView(r1)
        pulseInput = field(r1, "Test pulse (ms)", initialPulseMs.toString())
        distInput = field(r1, "Measured dist (m)", String.format(java.util.Locale.US, "%.2f", initialMeasured), decimal = true)

        val r2 = LinearLayout(this).apply { isBaselineAligned = false }
        body.addView(r2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        driveInput = field(r2, "Drive % (25–70)", cal.safeDrivePercent.toString())
        turnInput = field(r2, "90° Turn (ms)", cal.safeTurn90Ms.toString())
        headingInput = field(r2, "Heading (deg)", initialHeading.toString())

        val preview = text("", 12f).apply {
            setTextColor(AppColors.accent(this@MainActivity))
            setPadding(0, dp(6), 0, 0)
        }
        body.addView(preview)

        fun updatePreview() {
            val ms = pulseInput.text.toString().toLongOrNull()
            val dist = distInput.text.toString().toDoubleOrNull()
            val drv = driveInput.text.toString().toIntOrNull() ?: cal.safeDrivePercent
            val trn = turnInput.text.toString().toLongOrNull() ?: cal.safeTurn90Ms
            val computed = if (ms != null && dist != null) {
                StepCalibration.fromMeasuredPulse(ms, dist, drv, cal.safeSteerPercent, trn, cal.safePauseMs)
            } else null
            preview.text = if (computed != null) {
                String.format(
                    java.util.Locale.US,
                    "1.0 m = %d ms (%.2f m/s · Drive %d%% · 90° %d ms)",
                    computed.oneMeterMs, computed.safeSpeedMps, computed.safeDrivePercent, computed.safeTurn90Ms
                )
            } else {
                "Enter pulse 300–5000 ms and measured distance 0.10–5.0 m"
            }
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }
        pulseInput.addTextChangedListener(watcher)
        distInput.addTextChangedListener(watcher)
        driveInput.addTextChangedListener(watcher)
        turnInput.addTextChangedListener(watcher)
        updatePreview()

        val d = dialog("Calibrate 1 meter", body, "Save 1m" to {
            val ms = pulseInput.text.toString().toLongOrNull()
            val dist = distInput.text.toString().toDoubleOrNull()
            val drv = driveInput.text.toString().toIntOrNull() ?: cal.safeDrivePercent
            val trn = turnInput.text.toString().toLongOrNull() ?: cal.safeTurn90Ms
            val next = if (ms != null && dist != null) {
                StepCalibration.fromMeasuredPulse(ms, dist, drv, cal.safeSteerPercent, trn, cal.safePauseMs)
            } else null
            if (next == null) {
                if (ms == null || ms !in StepCalibration.MIN_PULSE_MS..StepCalibration.MAX_PULSE_MS) {
                    pulseInput.error = "300–5000 ms"
                } else {
                    distInput.error = "0.10–5.0 m"
                }
                return@to
            }
            saveStepCalibration(next, ms!!)
            headingInput.text.toString().toDoubleOrNull()?.takeIf { it.isFinite() }?.let { manualDeg ->
                val norm = ((manualDeg % 360.0) + 360.0) % 360.0
                if (planOrigin != null || manualDeg.toInt() % 360 != initialHeading) {
                    val base = ensurePlanOrigin()
                    val updated = base.copy(headingDegrees = norm)
                    planOrigin = updated
                    routeMap?.showOrigin(updated, center = false)
                    if (plannedSteps.isNotEmpty()) rebuildPlanFromSteps(updated)
                }
            }
            planner?.setCalibrated(true)
            dismissSheet()
            refreshOriginBanner()
            refreshGpsLabel()
            refresh()
            Toast.makeText(
                this,
                String.format(java.util.Locale.US, "1m calibrated · %d ms (%.2f m/s)", next.oneMeterMs, next.safeSpeedMps),
                Toast.LENGTH_SHORT
            ).show()
        })
        calDialog = d
        calStatus = statusView
        calArmBtn = armBtn
        calPulseBtn = testPulseBtn
        calTurnBtn = testTurnBtn
        calStopBtn = stopBtn
        d.setOnDismissListener {
            if (showing === d) {
                showing = null; updateDialog = null; updateMessage = null
                clearCalibrationRefs()
                if (::link.isInitialized) {
                    link.stopStepPlan("Calibration closed · stopped")
                }
            }
        }
        refresh()
    }

    private fun showMissionMoreMenu() {
        val items = listOf(
            "Re-calibrate 1m" to { showStepCalibration() },
            "Fit route on map" to { routeMap?.fitPlan(); Unit },
            "Import CSV" to { missionDocument(IMPORT_MISSION_REQUEST, false) },
            "Export CSV" to { missionDocument(EXPORT_MISSION_REQUEST, true) }
        )
        AlertDialog.Builder(this)
            .setTitle("Mission options")
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun refreshOriginBanner() {
        val cal = readStepCalibration()
        if (!cal.calibrated) {
            planner?.updateOriginBanner("1. Calibrate 1m first")
            return
        }
        val origin = planOrigin
        val live = liveHeadingDeg?.let { StepGeometry.compassLabel(it) }
            ?: if (phoneHeading?.available == false) "manual dir" else "compass…"
        val banner = if (origin != null) {
            val src = if (origin.fromPhoneGps) "Phone GPS" else "Sample start"
            "Start ${StepGeometry.compassLabel(origin.normalizedHeading)} ($src) · 1m = ${cal.oneMeterMs} ms"
        } else {
            "Heading $live · tap Set Start or ↑←→↓"
        }
        planner?.updateOriginBanner(banner)
    }

    private fun refreshLabels() {
        steerLabel?.text = "STEER · $steer"
        driveLabel?.text = "DRIVE · $drive"
    }

    private fun refreshCalibrationModal(s: GoLink.Snapshot) {
        if (calStatus == null || showing !== calDialog) return
        calStatus?.text = s.stepStatus
        calArmBtn?.text = when {
            s.handset -> "Radio"
            s.pending -> "Cancel"
            s.armed -> "DISARM"
            else -> "ARM"
        }
        calArmBtn?.isEnabled = !s.handset && !s.missionActive &&
            (s.pending || s.fresh && s.verified && !s.stopped)
        calPulseBtn?.isEnabled = s.stepReady && !s.stepActive
        calTurnBtn?.isEnabled = s.stepReady && !s.stepActive
        calStopBtn?.isEnabled = s.stepActive || s.armed || s.enabled || s.pending
    }

    private fun refresh() {
        if (!::link.isInitialized) return
        val s = link.snapshot()
        connect?.isEnabled = true
        connect?.text = if (s.connected) "Disconnect" else "Phone connect"
        refreshCalibrationModal(s)
        if (page == Page.MISSION) {
            planner?.updateLinkState(s)
            refreshGpsLabel()
            refreshOriginBanner()
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
        val cal = readStepCalibration()
        if (!cal.calibrated) {
            gpsBadge?.text = "Step 1: Calibrate 1m first"
            return
        }
        gpsBadge?.text = if (route.home != null) {
            "1m = ${cal.oneMeterMs} ms · Phone origin"
        } else {
            "1m = ${cal.oneMeterMs} ms · Sample map"
        }
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
        stopPhoneHeading()
        mapActive = false
        routeMap?.let {
            if (resumed) it.onPause()
            if (started) it.onStop()
            it.onDestroy()
        }
        routeMap = null
        planner = null
    }

    // UI — Go modal sheets. Fixed intrinsic heights keep fields above the soft keyboard and scroll on short screens.
    private fun dialog(title: String, content: View, positive: Pair<String, () -> Unit>? = null): Dialog {
        link.stop(true)
        link.stopStepPlan("Settings opened · stopped")
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
        val scroll = ScrollView(this).apply { addView(content) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val footer = LinearLayout(this).apply { isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(button(if (positive == null) "Done" else "Cancel") { d.dismiss() }, LinearLayout.LayoutParams(0, row(), 1f))
        positive?.let { (label, action) ->
            footer.addView(button(label, true) { action() }, LinearLayout.LayoutParams(0, row(), 1f).apply { leftMargin = dp(8) })
        }
        panel.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        d.setContentView(panel)
        val visible = Rect().also { window.decorView.getWindowVisibleDisplayFrame(it) }
        val availableWidth = if (visible.width() > 0) visible.width() else resources.displayMetrics.widthPixels
        val availableHeight = if (visible.height() > 0) visible.height() else resources.displayMetrics.heightPixels
        val width = minOf(dp(440), availableWidth - dp(32)).coerceAtLeast(1)
        val maxHeight = (availableHeight - dp(24)).coerceAtLeast(1)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = if (panel.measuredHeight > maxHeight) {
            scroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) }
            maxHeight
        } else {
            panel.measuredHeight
        }
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setDimAmount(.48f)
            setGravity(Gravity.CENTER); attributes = attributes.apply { x = 0; y = 0; windowAnimations = 0 }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setLayout(width, height)
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
                                plannedSteps.clear()
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
        link.stopStepPlan("Configuration changed · stopped")
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
        if (mapActive) {
            startPhoneHeading()
            startPhoneLocation()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::updater.isInitialized) updater.resumePendingInstall()
    }

    override fun onPause() {
        resumed = false
        stopPhoneLocation()
        stopPhoneHeading()
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
