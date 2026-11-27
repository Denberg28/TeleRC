package io.github.denberg28.telerc

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import java.util.Locale

// UI — Minimal mission planner with wide clear viewport (~72%) and compact right control strip (~28%).
// Supports DJI Fly / fpvDROID-style single-tap PiP swap between Map and live FPV camera without reloading either view.
class MissionPlannerView(
    context: Context,
    private val plan: MissionPlan,
    private val map: RouteMapView,
    private val fpv: FpvFeedView,
    private val steps: MutableList<StepCommand>,
    private val onCalibrate: () -> Unit,
    private val onLockOrigin: () -> Unit,
    private val onStepTapped: (StepDirection) -> Unit,
    private val onUndoStep: () -> Unit,
    private val onClearAll: () -> Unit,
    private val onArmToggle: () -> Unit,
    private val onRunPlan: () -> Unit,
    private val onStopRun: () -> Unit,
    private val onFpvSettings: () -> Unit,
    private val onMoreMenu: () -> Unit
) : LinearLayout(context) {
    private val ink = AppColors.ink(context)
    private val muted = AppColors.muted(context)
    private val surface = AppColors.surface(context)

    private val originBanner = label("Calibrate 1m first", ink, 12f)
    private val camChip = TextView(context).apply {
        text = "● FPV Cam"
        textSize = 11.5f
        setTextColor(ink)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = shape(0xCC15202B.toInt(), 8)
        isClickable = true
        isFocusable = true
        contentDescription = "Configure FPV camera source and scaling"
        setOnClickListener { onFpvSettings() }
    }

    // UI — Swap viewport slots (both stay attached so Map & FPV swap with zero reload or stream interruption).
    private val mapCard = FrameLayout(context)
    private val fpvCard = FrameLayout(context)
    private val mapPipShield = View(context).apply {
        isClickable = true
        contentDescription = "Swap mini map to full screen"
        setOnClickListener { swapFeeds() }
    }
    private val fpvPipShield = View(context).apply {
        isClickable = true
        contentDescription = "Swap mini FPV camera to full screen"
        setOnClickListener { swapFeeds() }
    }
    private val mapPipBadge = pipBadge("MAP ⇄")
    private val fpvPipBadge = pipBadge("● FPV ⇄")
    private val mapPipHide = pipHideButton { setPipCollapsed(true) }
    private val fpvPipHide = pipHideButton { setPipCollapsed(true) }
    private val pipRestorePill = TextView(context).apply {
        text = "● FPV ↗"
        textSize = 11.5f
        setTextColor(ink)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = shape(0xD815202B.toInt(), 10)
        isClickable = true
        isFocusable = true
        visibility = View.GONE
        contentDescription = "Show Picture-in-Picture feed preview"
        setOnClickListener { setPipCollapsed(false) }
    }

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
        contentDescription = "More mission options: recalibrate 1m, FPV camera, fit map, import, export"
    }
    private val armBtn = action("ARM", false, onArmToggle)
    private val runBtn = action("Run", true, onRunPlan)
    private val stopBtn = action("STOP", true, onStopRun).apply {
        background = shape(Color.rgb(168, 42, 42), 10)
        setTextColor(Color.WHITE)
    }

    private var calibrated = false
    private var fpvPrimary = false
    private var pipCollapsed = false
    private var fpvLive = false
    private var fpvStatusLabel = "FPV standby"

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 12) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, color: Int = ink, size: Float = 12f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(4), dp(3), dp(4), dp(3))
    }
    private fun action(value: String, filled: Boolean = false, clicked: () -> Unit) =
        AppUi.button(context, value, filled, clicked).apply { textSize = 12f; maxLines = 1 }
    private fun pipBadge(value: String) = TextView(context).apply {
        text = value
        textSize = 10f
        setTextColor(Color.WHITE)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        background = shape(0xB80E1621.toInt(), 6)
    }
    private fun pipHideButton(onClick: () -> Unit) = TextView(context).apply {
        text = "−"
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = shape(0xB80E1621.toInt(), 6)
        isClickable = true
        isFocusable = true
        contentDescription = "Hide Picture-in-Picture preview"
        setOnClickListener { onClick() }
    }

    // UI — Clear swap viewport (weight 2.6) + minimal right control strip (weight 1.0).
    init {
        orientation = HORIZONTAL
        val viewport = FrameLayout(context)

        mapCard.addView(map.view, FrameLayout.LayoutParams(-1, -1))
        mapCard.addView(mapPipShield, FrameLayout.LayoutParams(-1, -1))
        mapCard.addView(mapPipBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(5); topMargin = dp(5)
        })
        mapCard.addView(mapPipHide, FrameLayout.LayoutParams(dp(24), dp(22), Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(5); topMargin = dp(5)
        })

        fpvCard.addView(fpv, FrameLayout.LayoutParams(-1, -1))
        fpvCard.addView(fpvPipShield, FrameLayout.LayoutParams(-1, -1))
        fpvCard.addView(fpvPipBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(5); topMargin = dp(5)
        })
        fpvCard.addView(fpvPipHide, FrameLayout.LayoutParams(dp(24), dp(22), Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(5); topMargin = dp(5)
        })

        viewport.addView(mapCard, FrameLayout.LayoutParams(-1, -1))
        viewport.addView(fpvCard, FrameLayout.LayoutParams(-1, -1))
        viewport.addView(originBanner.apply {
            background = shape(0xCC15202B.toInt(), 8)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(8); topMargin = dp(8)
        })
        viewport.addView(camChip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(8); topMargin = dp(8)
        })
        viewport.addView(pipRestorePill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(10); bottomMargin = dp(10)
        })
        addView(viewport, LayoutParams(0, -1, 2.6f))

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
        applyFeedLayout()
        refresh(fit = true)
    }

    fun swapFeeds() {
        fpvPrimary = !fpvPrimary
        pipCollapsed = false
        applyFeedLayout()
    }

    fun setPipCollapsed(collapsed: Boolean) {
        pipCollapsed = collapsed
        applyFeedLayout()
    }

    fun updateFpvStatus(live: Boolean, label: String) {
        fpvLive = live
        fpvStatusLabel = label
        val dot = if (live) "●" else "○"
        val dotColor = if (live) Color.rgb(66, 214, 128) else Color.rgb(245, 190, 72)
        fpvPipBadge.text = "$dot FPV ⇄"
        fpvPipBadge.setTextColor(if (live) Color.rgb(185, 250, 205) else Color.WHITE)
        camChip.text = if (fpvPrimary) "$dot $label" else "$dot FPV Cam"
        camChip.setTextColor(if (live) dotColor else ink)
        pipRestorePill.text = if (fpvPrimary) "MAP ↗" else "$dot FPV ↗"
    }

    private fun applyFeedLayout() {
        val pipW = dp(152)
        val pipH = dp(92)
        fun style(card: FrameLayout, isPip: Boolean) {
            if (isPip) {
                card.layoutParams = FrameLayout.LayoutParams(pipW, pipH, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = dp(10); bottomMargin = dp(10)
                }
                card.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
                card.clipToOutline = true
                card.elevation = dp(8).toFloat()
                card.foreground = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(2), Color.argb(215, 226, 238, 250))
                }
            } else {
                card.layoutParams = FrameLayout.LayoutParams(-1, -1)
                card.outlineProvider = ViewOutlineProvider.BACKGROUND
                card.clipToOutline = false
                card.elevation = 0f
                card.foreground = null
            }
        }

        val mapIsPip = fpvPrimary
        val fpvIsPip = !fpvPrimary

        style(mapCard, mapIsPip)
        style(fpvCard, fpvIsPip)

        mapPipShield.visibility = if (mapIsPip) View.VISIBLE else View.GONE
        mapPipBadge.visibility = if (mapIsPip) View.VISIBLE else View.GONE
        mapPipHide.visibility = if (mapIsPip) View.VISIBLE else View.GONE

        fpvPipShield.visibility = if (fpvIsPip) View.VISIBLE else View.GONE
        fpvPipBadge.visibility = if (fpvIsPip) View.VISIBLE else View.GONE
        fpvPipHide.visibility = if (fpvIsPip) View.VISIBLE else View.GONE

        mapCard.visibility = if (mapIsPip && pipCollapsed) View.GONE else View.VISIBLE
        fpvCard.visibility = if (fpvIsPip && pipCollapsed) View.GONE else View.VISIBLE
        pipRestorePill.visibility = if (pipCollapsed) View.VISIBLE else View.GONE

        // Keep secondary PiP above primary feed, and keep compact HUD pills above both.
        if (fpvPrimary) {
            fpvCard.bringToFront()
            if (!pipCollapsed) mapCard.bringToFront()
        } else {
            mapCard.bringToFront()
            if (!pipCollapsed) fpvCard.bringToFront()
        }
        originBanner.bringToFront()
        camChip.bringToFront()
        if (pipCollapsed) pipRestorePill.bringToFront()

        fpv.setCompact(fpvIsPip)
        updateFpvStatus(fpvLive, fpvStatusLabel)
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
