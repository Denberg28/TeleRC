package io.github.denberg28.telerc

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.util.Locale

// UI — Mission planner with phone-origin 1m step planning (Fwd/Back/Left/Right)
// and joystick-free open-loop step testing. MainActivity owns persistence, sensors, and pickers.
class MissionPlannerView(
    context: Context,
    private val plan: MissionPlan,
    private val map: RouteMapView,
    private val steps: MutableList<StepCommand>,
    private val onStepTapped: (StepDirection, Boolean) -> Unit,
    private val onUndoStep: () -> Unit,
    private val onClearAll: () -> Unit,
    private val onArmToggle: () -> Unit,
    private val onRunPlan: () -> Unit,
    private val onStopRun: () -> Unit,
    private val onCalibrate: () -> Unit,
    private val save: () -> Unit,
    exportPlan: () -> Unit,
    importPlan: () -> Unit,
    uploadPlan: () -> Unit,
    cancelUpload: () -> Unit
) : LinearLayout(context) {
    private val ink = AppColors.ink(context)
    private val muted = AppColors.muted(context)
    private val surface = AppColors.surface(context)
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    private val originBanner = label("Tap Lock Origin to set phone location + heading", ink, 12f)
    private val stepStatus = label("Step test idle", muted, 12f)
    private val summary = label("", ink, 13f)
    private val transferStatus = label("Plan saved on phone", muted, 11f)
    private val modeToggle = action("Mode: Plan", false) { toggleInstantMode() }
    private val armBtn = action("ARM", true, onArmToggle)
    private val runBtn = action("Run Plan", true, onRunPlan)
    private val stopBtn = action("STOP", true, onStopRun).apply {
        background = shape(Color.rgb(168, 42, 42), 10)
        setTextColor(Color.WHITE)
    }
    private val upload = action("Upload Wi-Fi", false, uploadPlan)
    private val cancel = action("Cancel", false, cancelUpload)
    private val fwdBtn = action("↑ 1m", true) { onStepTapped(StepDirection.FORWARD, instantMode) }
    private val leftBtn = action("← 1m", true) { onStepTapped(StepDirection.LEFT, instantMode) }
    private val rightBtn = action("→ 1m", true) { onStepTapped(StepDirection.RIGHT, instantMode) }
    private val backBtn = action("↓ 1m", true) { onStepTapped(StepDirection.BACK, instantMode) }
    private var instantMode = false
    private var lastStepReady = false
    private var coordinateDialog: AlertDialog? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 12) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, color: Int = ink, size: Float = 13f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(6), dp(4), dp(6), dp(4))
    }
    private fun action(value: String, filled: Boolean = false, clicked: () -> Unit) =
        AppUi.button(context, value, filled, clicked).apply { textSize = 12f; maxLines = 1 }

    // UI — Planner spacing. Left map (55%) + right scrollable step/plan sidebar (45%).
    init {
        orientation = HORIZONTAL
        val mapPanel = FrameLayout(context)
        mapPanel.addView(map.view, FrameLayout.LayoutParams(-1, -1))
        mapPanel.addView(originBanner.apply {
            background = shape(0xD815202B.toInt(), 8)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(8); topMargin = dp(8)
        })
        addView(mapPanel, LayoutParams(0, -1, 11f))

        val sidebarContent = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        // Section 1: 1m Forward / Left / Right / Back relative to locked phone orientation.
        val padHeader = LinearLayout(context).apply { isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
        padHeader.addView(label("1m STEP CONTROL", AppColors.accent(context), 12f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LayoutParams(0, -2, 1f))
        padHeader.addView(modeToggle, LayoutParams(dp(106), dp(40)).apply { rightMargin = dp(6) })
        padHeader.addView(action("Cal", false, onCalibrate), LayoutParams(dp(56), dp(40)))
        sidebarContent.addView(padHeader, LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val stepPad = LinearLayout(context).apply { isBaselineAligned = false }
        stepPad.addView(fwdBtn, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        stepPad.addView(leftBtn, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        stepPad.addView(rightBtn, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        stepPad.addView(backBtn, LayoutParams(0, dp(48), 1f))
        sidebarContent.addView(stepPad, LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        sidebarContent.addView(stepStatus)

        // Section 2: ARM / Run Plan / STOP (joystick-free step testing).
        val execRow = LinearLayout(context).apply { isBaselineAligned = false }
        execRow.addView(armBtn, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        execRow.addView(runBtn, LayoutParams(0, dp(48), 1.2f).apply { rightMargin = dp(6) })
        execRow.addView(stopBtn, LayoutParams(0, dp(48), 1f))
        sidebarContent.addView(execRow, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        // Section 3: Planned steps/waypoints + CSV + optional Wi-Fi FC upload.
        sidebarContent.addView(summary)
        val tools = LinearLayout(context).apply { isBaselineAligned = false }
        tools.addView(action("Undo", false, onUndoStep), LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        tools.addView(action("Fit") { map.fitPlan() }, LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        tools.addView(action("Clear") {
            if (plan.waypoints.isNotEmpty() || steps.isNotEmpty()) {
                AlertDialog.Builder(context).setTitle("Clear local plan?")
                    .setMessage("This removes ${plan.waypoints.size} planned points.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ -> onClearAll() }.show()
            }
        }, LayoutParams(0, dp(44), 1f))
        sidebarContent.addView(tools, LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        sidebarContent.addView(rows, LayoutParams(-1, -2))

        val files = LinearLayout(context).apply { isBaselineAligned = false }
        files.addView(action("Import", false, importPlan), LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        files.addView(action("Export", false, exportPlan), LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        files.addView(action("+ Lat/Lon") { editPoint() }, LayoutParams(0, dp(44), 1f))
        sidebarContent.addView(files, LayoutParams(-1, -2).apply { topMargin = dp(6) })

        sidebarContent.addView(transferStatus)
        val transferRow = LinearLayout(context).apply { isBaselineAligned = false }
        transferRow.addView(upload, LayoutParams(0, dp(44), 2f).apply { rightMargin = dp(6) })
        transferRow.addView(cancel, LayoutParams(0, dp(44), 1f))
        sidebarContent.addView(transferRow)

        val sidebarScroll = ScrollView(context).apply {
            background = shape(surface, 14)
            isFillViewport = true
            addView(sidebarContent)
        }
        addView(sidebarScroll, LayoutParams(0, -1, 9f).apply { leftMargin = dp(8) })
        map.onAddWaypoint = { editPoint(point = it) }
        map.onEditWaypoint = { editPoint(it) }
        refresh(fit = true)
    }

    private fun toggleInstantMode() {
        if (!instantMode && !lastStepReady) {
            Toast.makeText(context, "Connect and ARM rover first to enable Instant 1m mode", Toast.LENGTH_SHORT).show()
            return
        }
        instantMode = !instantMode
        refreshModeButton()
    }

    fun resetInstantMode() {
        if (instantMode) {
            instantMode = false
            refreshModeButton()
        }
    }

    private fun refreshModeButton() {
        modeToggle.text = if (instantMode) "Instant 1m" else "Mode: Plan"
    }

    fun updateOriginBanner(text: String) {
        originBanner.text = text
    }

    internal fun updateLinkState(snapshot: GoLink.Snapshot) {
        lastStepReady = snapshot.stepReady
        if (!snapshot.stepReady && instantMode) resetInstantMode()
        stepStatus.text = snapshot.stepStatus
        armBtn.text = when {
            snapshot.handset -> "Radio"
            snapshot.pending -> "Cancel"
            snapshot.armed -> "DISARM"
            else -> "ARM"
        }
        armBtn.isEnabled = !snapshot.handset && !snapshot.missionActive &&
            (snapshot.pending || snapshot.fresh && snapshot.verified && !snapshot.stopped)
        runBtn.isEnabled = snapshot.stepReady && !snapshot.stepActive && steps.isNotEmpty()
        stopBtn.isEnabled = snapshot.stepActive || snapshot.armed || snapshot.enabled || snapshot.pending
        val stepsEnabled = !snapshot.stepActive && (!instantMode || snapshot.stepReady)
        fwdBtn.isEnabled = stepsEnabled
        leftBtn.isEnabled = stepsEnabled
        rightBtn.isEnabled = stepsEnabled
        backBtn.isEnabled = stepsEnabled

        transferStatus.text = snapshot.missionStatus
        upload.isEnabled = snapshot.missionReady && !snapshot.missionActive && plan.waypoints.isNotEmpty()
        cancel.isEnabled = snapshot.missionActive
    }

    fun refresh(fit: Boolean = false) {
        val points = plan.waypoints
        summary.text = if (steps.isNotEmpty()) {
            String.format(Locale.US, "%d steps · %d / %d pts · %.1f m", steps.size, points.size, MissionPlan.MAX_WAYPOINTS, plan.distanceMeters)
        } else {
            String.format(Locale.US, "%d / %d waypoints · %.0f m", points.size, MissionPlan.MAX_WAYPOINTS, plan.distanceMeters)
        }
        rows.removeAllViews()
        points.forEachIndexed { index, p ->
            val stepTag = steps.getOrNull(index)?.let { "${it.direction.shortLabel} ${it.meters}m · " } ?: ""
            rows.addView(action(String.format(Locale.US, "%d  %s%.6f, %.6f", index + 1, stepTag, p.latitude, p.longitude)) {
                val choices = buildList {
                    add("Edit coordinates")
                    if (index > 0) add("Move earlier")
                    if (index < plan.waypoints.lastIndex) add("Move later")
                    add("Delete")
                }
                AlertDialog.Builder(context).setTitle("Waypoint ${index + 1}")
                    .setItems(choices.toTypedArray()) { _, choice ->
                        when (choices[choice]) {
                            "Edit coordinates" -> editPoint(index)
                            "Move earlier" -> {
                                plan.move(index, -1)
                                if (index in steps.indices && index - 1 in steps.indices) {
                                    val tmp = steps[index]; steps[index] = steps[index - 1]; steps[index - 1] = tmp
                                }
                                changed()
                            }
                            "Move later" -> {
                                plan.move(index, 1)
                                if (index in steps.indices && index + 1 in steps.indices) {
                                    val tmp = steps[index]; steps[index] = steps[index + 1]; steps[index + 1] = tmp
                                }
                                changed()
                            }
                            "Delete" -> {
                                plan.remove(index)
                                if (index in steps.indices) steps.removeAt(index)
                                changed()
                            }
                        }
                    }.show()
            }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                contentDescription = "Waypoint ${index + 1}, ${stepTag}latitude ${p.latitude}, longitude ${p.longitude}."
            }, LayoutParams(-1, dp(44)).apply { bottomMargin = dp(4) })
        }
        map.showPlan(points, fit)
    }

    private fun changed() { save(); refresh() }

    // Mission — Coordinate entry validates both fields before changing the saved plan.
    private fun editPoint(index: Int? = null, point: Waypoint? = null) {
        if (coordinateDialog?.isShowing == true) return
        if (index == null && plan.waypoints.size >= MissionPlan.MAX_WAYPOINTS) {
            Toast.makeText(context, "Maximum 100 waypoints. Edit or remove an existing point.", Toast.LENGTH_LONG).show()
            return
        }
        val initial = point ?: index?.let { plan.waypoints[it] }
        fun field(title: String, value: Double?) = EditText(context).apply {
            hint = title; contentDescription = title; setSingleLine(); setTextColor(ink)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(value?.toString().orEmpty())
        }
        val latitude = field("Latitude (−90 to 90°)", initial?.latitude)
        val longitude = field("Longitude (−180 to 180°)", initial?.longitude)
        val form = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(20), 0, dp(20), 0)
            addView(label("Latitude (degrees)")); addView(latitude)
            addView(label("Longitude (degrees)")); addView(longitude)
        }
        val dialog = AlertDialog.Builder(context).setTitle(if (index == null) "Add waypoint" else "Edit waypoint ${index + 1}")
            .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        coordinateDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val lat = latitude.text.toString().toDoubleOrNull()
                val lon = longitude.text.toString().toDoubleOrNull()
                when {
                    lat == null || !lat.isFinite() || lat !in -90.0..90.0 -> latitude.error = "Enter −90 to 90"
                    lon == null || !lon.isFinite() || lon !in -180.0..180.0 -> longitude.error = "Enter −180 to 180"
                    else -> {
                        val value = Waypoint(lat, lon)
                        if (index == null) plan.add(value) else plan.edit(index, value)
                        changed(); dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onDetachedFromWindow() {
        coordinateDialog?.dismiss(); coordinateDialog = null
        super.onDetachedFromWindow()
    }
}
