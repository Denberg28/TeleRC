package io.github.denberg28.telerc

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.util.Locale

/** Owns planner presentation; MainActivity owns plan persistence and document pickers. */
class MissionPlannerView(
    context: Context,
    private val plan: MissionPlan,
    private val map: RouteMapView,
    private val save: () -> Unit,
    exportPlan: () -> Unit,
    importPlan: () -> Unit
) : LinearLayout(context) {
    private val dark = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", false)
    private val ink = if (dark) Color.WHITE else Color.rgb(27, 38, 49)
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    private val summary = label("")
    private var coordinateDialog: AlertDialog? = null
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String) = TextView(context).apply {
        text = value; textSize = 14f; setTextColor(ink); setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private fun action(value: String, clicked: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 13f; setOnClickListener { clicked() }
    }

    // UI — Planner spacing. The list scrolls; map and 48 dp action rows keep their own space.
    init {
        orientation = HORIZONTAL
        val mapPanel = FrameLayout(context)
        mapPanel.addView(map.view, FrameLayout.LayoutParams(-1, -1))
        mapPanel.addView(label("Tap map to add waypoints").apply {
            setBackgroundColor(if (dark) Color.rgb(22, 34, 45) else Color.WHITE)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        addView(mapPanel, LayoutParams(0, -1, 3f))
        val sidebar = LinearLayout(context).apply { orientation = VERTICAL }
        sidebar.addView(summary)
        val tools = LinearLayout(context)
        tools.addView(action("Add") { editPoint() }, LayoutParams(0, dp(48), 1f))
        tools.addView(action("Fit") { map.fitPlan() }, LayoutParams(0, dp(48), 1f))
        tools.addView(action("Clear") {
            if (plan.waypoints.isNotEmpty()) AlertDialog.Builder(context).setTitle("Clear local plan?")
                .setMessage("This removes ${plan.waypoints.size} waypoints. Export first to keep a copy.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ ->
                    plan.clear(); changed()
                }.show()
        }, LayoutParams(0, dp(48), 1f))
        sidebar.addView(tools)
        sidebar.addView(ScrollView(context).apply { addView(rows) }, LayoutParams(-1, 0, 1f))
        val files = LinearLayout(context)
        files.addView(action("Import", importPlan), LayoutParams(0, dp(48), 1f))
        files.addView(action("Export", exportPlan), LayoutParams(0, dp(48), 1f))
        sidebar.addView(files)
        addView(sidebar, LayoutParams(0, -1, 2f).apply { leftMargin = dp(8) })
        map.onAddWaypoint = { editPoint(point = it) }
        map.onEditWaypoint = { editPoint(it) }
        refresh(fit = true)
    }

    fun refresh(fit: Boolean = false) {
        val points = plan.waypoints
        summary.text = String.format(Locale.US, "%d / %d waypoints · %.0f m", points.size, MissionPlan.MAX_WAYPOINTS, plan.distanceMeters)
        rows.removeAllViews()
        points.forEachIndexed { index, p ->
            rows.addView(action(String.format(Locale.US, "%d   %.6f, %.6f", index + 1, p.latitude, p.longitude)) {
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
                            "Move earlier" -> { plan.move(index, -1); changed() }
                            "Move later" -> { plan.move(index, 1); changed() }
                            "Delete" -> { plan.remove(index); changed() }
                        }
                    }.show()
            }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                contentDescription = "Waypoint ${index + 1}, latitude ${p.latitude}, longitude ${p.longitude}. Edit, reorder or delete."
            }, LayoutParams(-1, -2).apply { height = dp(52) })
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
