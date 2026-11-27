package io.github.denberg28.telerc

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

// UI — Minimal mission planner with wide clear map (~72%) and compact right control strip (~28%).
// Enforces 1-meter calibration first before unlocking phone-origin waypoint steps.
class MissionPlannerView(
    context: Context,
    private val plan: MissionPlan,
    private val map: RouteMapView,
    private val steps: MutableList<StepCommand>,
    private val onCalibrate: () -> Unit,
    private val onLockOrigin: () -> Unit,
    private val onStepTapped: (StepDirection) -> Unit,
    private val onUndoStep: () -> Unit,
    private val onClearAll: () -> Unit,
    private val onArmToggle: () -> Unit,
    private val onRunPlan: () -> Unit,
    private val onStopRun: () -> Unit,
    private val onMoreMenu: () -> Unit
) : LinearLayout(context) {
    private val ink = AppColors.ink(context)
    private val muted = AppColors.muted(context)
    private val surface = AppColors.surface(context)

    private val originBanner = label("Calibrate 1m first", ink, 12f)
    private val gatePanel = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
    private val controlPanel = LinearLayout(context).apply { orientation = VERTICAL }

    private val summary = label("0 steps · 0.0 m", ink, 12f).apply { gravity = Gravity.CENTER }
    private val statusLine = label("Connect Wi-Fi to test steps", muted, 11f).apply {
        gravity = Gravity.CENTER; maxLines = 2
    }
    private val startBtn = action("Set Start (GPS + Dir)", false, onLockOrigin)
    private val fwdBtn = action("↑ 1m", true) { onStepTapped(StepDirection.FORWARD) }
    private val leftBtn = action("← 1m", true) { onStepTapped(StepDirection.LEFT) }
    private val rightBtn = action("→ 1m", true) { onStepTapped(StepDirection.RIGHT) }
    private val backBtn = action("↓ 1m", true) { onStepTapped(StepDirection.BACK) }
    private val undoBtn = action("Undo", false, onUndoStep)
    private val clearBtn = action("Clear", false) {
        if (plan.waypoints.isNotEmpty() || steps.isNotEmpty()) {
            AlertDialog.Builder(context).setTitle("Clear planned steps?")
                .setMessage("Removes ${plan.waypoints.size} waypoints from the map.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> onClearAll() }.show()
        }
    }
    private val moreBtn = action("⋯", false, onMoreMenu).apply {
        contentDescription = "More mission options: recalibrate, fit map, import, export, Wi-Fi upload"
    }
    private val armBtn = action("ARM", false, onArmToggle)
    private val runBtn = action("Run", true, onRunPlan)
    private val stopBtn = action("STOP", true, onStopRun).apply {
        background = shape(Color.rgb(168, 42, 42), 10)
        setTextColor(Color.WHITE)
    }

    private var calibrated = false

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 12) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, color: Int = ink, size: Float = 12f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(4), dp(3), dp(4), dp(3))
    }
    private fun action(value: String, filled: Boolean = false, clicked: () -> Unit) =
        AppUi.button(context, value, filled, clicked).apply { textSize = 12f; maxLines = 1 }

    // UI — Clear map view (weight 2.6) + minimal right control strip (weight 1.0).
    init {
        orientation = HORIZONTAL
        val mapPanel = FrameLayout(context)
        mapPanel.addView(map.view, FrameLayout.LayoutParams(-1, -1))
        mapPanel.addView(originBanner.apply {
            background = shape(0xCC15202B.toInt(), 8)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(8); topMargin = dp(8)
        })
        addView(mapPanel, LayoutParams(0, -1, 2.6f))

        // Gate panel shown until 1m calibration is completed.
        gatePanel.setPadding(dp(10), dp(12), dp(10), dp(12))
        gatePanel.addView(label("1. Calibrate 1m", AppColors.accent(context), 15f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
        })
        gatePanel.addView(label(
            "Run a short forward pulse, measure distance, and save before placing 1m waypoints.",
            muted, 12f
        ).apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(8), dp(4), dp(12)) })
        gatePanel.addView(action("Calibrate 1m", true, onCalibrate), LayoutParams(-1, dp(48)))

        // Minimal D-pad + Run/STOP panel unlocked after 1m calibration.
        controlPanel.setPadding(dp(8), dp(8), dp(8), dp(8))
        controlPanel.addView(startBtn, LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })

        // Compact 1m D-pad: Forward / Left-Right / Back
        val topPad = LinearLayout(context).apply { gravity = Gravity.CENTER }
        topPad.addView(fwdBtn, LayoutParams(dp(92), dp(46)))
        controlPanel.addView(topPad, LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val midPad = LinearLayout(context).apply { isBaselineAligned = false }
        midPad.addView(leftBtn, LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
        midPad.addView(rightBtn, LayoutParams(0, dp(46), 1f))
        controlPanel.addView(midPad, LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val botPad = LinearLayout(context).apply { gravity = Gravity.CENTER }
        botPad.addView(backBtn, LayoutParams(dp(92), dp(46)))
        controlPanel.addView(botPad, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        controlPanel.addView(summary)

        val utilRow = LinearLayout(context).apply { isBaselineAligned = false }
        utilRow.addView(undoBtn, LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })
        utilRow.addView(clearBtn, LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })
        utilRow.addView(moreBtn, LayoutParams(dp(44), dp(42)))
        controlPanel.addView(utilRow, LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })

        controlPanel.addView(statusLine, LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val execRow = LinearLayout(context).apply { isBaselineAligned = false }
        execRow.addView(armBtn, LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        execRow.addView(runBtn, LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        execRow.addView(stopBtn, LayoutParams(0, dp(46), 1f))
        controlPanel.addView(execRow, LayoutParams(-1, -2))

        val stripContainer = FrameLayout(context).apply {
            background = shape(surface, 14)
            addView(gatePanel, FrameLayout.LayoutParams(-1, -1))
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(controlPanel)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        addView(stripContainer, LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) })

        // Keep map interactions uncluttered; waypoint placement uses the calibrated 1m D-pad.
        map.onAddWaypoint = null
        map.onEditWaypoint = null
        setCalibrated(false)
        refresh(fit = true)
    }

    fun setCalibrated(done: Boolean) {
        calibrated = done
        gatePanel.visibility = if (done) View.GONE else View.VISIBLE
        (controlPanel.parent as? View)?.visibility = if (done) View.VISIBLE else View.GONE
    }

    fun updateOriginBanner(text: String) {
        originBanner.text = text
    }

    internal fun updateLinkState(snapshot: GoLink.Snapshot) {
        statusLine.text = snapshot.stepStatus
        armBtn.text = when {
            snapshot.handset -> "Radio"
            snapshot.pending -> "Cancel"
            snapshot.armed -> "DISARM"
            else -> "ARM"
        }
        armBtn.isEnabled = !snapshot.handset && !snapshot.missionActive &&
            (snapshot.pending || snapshot.fresh && snapshot.verified && !snapshot.stopped)
        runBtn.isEnabled = calibrated && snapshot.stepReady && !snapshot.stepActive && steps.isNotEmpty()
        stopBtn.isEnabled = snapshot.stepActive || snapshot.armed || snapshot.enabled || snapshot.pending
        val padEnabled = calibrated && !snapshot.stepActive
        startBtn.isEnabled = padEnabled
        fwdBtn.isEnabled = padEnabled
        leftBtn.isEnabled = padEnabled
        rightBtn.isEnabled = padEnabled
        backBtn.isEnabled = padEnabled
        undoBtn.isEnabled = padEnabled && (steps.isNotEmpty() || plan.waypoints.isNotEmpty())
        clearBtn.isEnabled = padEnabled && (steps.isNotEmpty() || plan.waypoints.isNotEmpty())
    }

    fun refresh(fit: Boolean = false) {
        val points = plan.waypoints
        summary.text = if (steps.isNotEmpty()) {
            String.format(Locale.US, "%d steps · %.1f m", steps.size, plan.distanceMeters)
        } else {
            String.format(Locale.US, "%d pts · %.1f m", points.size, plan.distanceMeters)
        }
        map.showPlan(points, fit)
    }
}
