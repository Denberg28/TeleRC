package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Measured tracks and local waypoints. No inferred position or command transmission. */
class RouteMapView(context: Context, private val session: RouteSession) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var plan: List<Waypoint> = emptyList()
    private var origin: PlanOrigin? = null
    private val waypointMarkers = mutableMapOf<Long, Int>()
    private var centered = false
    private var requestedLocate: LatLng? = null
    private var pendingFit = false
    var onAddWaypoint: ((Waypoint) -> Unit)? = null
    var onEditWaypoint: ((Int) -> Unit)? = null

    init {
        MapLibre.getInstance(context)
        view = MapView(context)
        view.onCreate(null)
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pendingFit && view.width > 0 && view.height > 0 && map?.style?.isFullyLoaded == true) fitPlan()
        }
        view.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isCompassEnabled = true
            ready.addOnMapClickListener { point ->
                onAddWaypoint?.invoke(Waypoint(point.latitude, point.longitude))
                onAddWaypoint != null
            }
            ready.setOnMarkerClickListener { marker ->
                val index = waypointMarkers[marker.id]
                if (index != null) onEditWaypoint?.invoke(index)
                index != null
            }
            ready.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                draw()
                if (pendingFit) fitPlan()
            }
        }
    }

    // Map — Only these stored coordinates become planned waypoints; Home remains phone GPS.
    fun showOrigin(value: PlanOrigin?, center: Boolean = false) {
        origin = value
        draw()
        if (center && value != null) locatePhoneFix(value.latitude, value.longitude)
    }
    fun showPlan(points: List<Waypoint>, fit: Boolean = false) {
        plan = points.toList(); draw()
        if (fit) fitPlan()
    }
    fun draw() {
        val m = map ?: return
        if (m.style?.isFullyLoaded != true) return
        m.clear(); waypointMarkers.clear()
        fun coords(p: TrackPoint) = LatLng(p.latitude, p.longitude)
        session.home?.let { m.addMarker(MarkerOptions().position(coords(it)).title("Phone Home")) }
        session.phone.lastOrNull()?.let { m.addMarker(MarkerOptions().position(coords(it)).title("Phone GPS")) }
        session.rover.lastOrNull()?.let { m.addMarker(MarkerOptions().position(coords(it)).title("Last measured rover GPS")) }
        origin?.let { o ->
            val start = LatLng(o.latitude, o.longitude)
            val tag = if (o.fromPhoneGps) "Phone origin" else "Sample origin"
            m.addMarker(MarkerOptions().position(start).title("$tag · ${StepGeometry.compassLabel(o.normalizedHeading)}"))
            // Draw a 1.5 m heading reference vector from the locked phone origin.
            val tip = StepGeometry.offsetMeters(o.latitude, o.longitude, o.normalizedHeading, 1.5)
            m.addPolyline(
                PolylineOptions()
                    .add(start, LatLng(tip.latitude, tip.longitude))
                    .color(Color.rgb(90, 210, 255))
                    .width(4f)
            )
        }
        if (session.phone.size > 1) m.addPolyline(PolylineOptions().addAll(session.phone.map(::coords))
            .color(Color.rgb(49, 113, 203)).width(3f))
        if (session.rover.size > 1) m.addPolyline(PolylineOptions().addAll(session.rover.map(::coords))
            .color(Color.rgb(20, 140, 90)).width(4f))
        plan.forEachIndexed { index, p ->
            val marker = m.addMarker(MarkerOptions().position(LatLng(p.latitude, p.longitude)).title("Waypoint ${index + 1}"))
            waypointMarkers[marker.id] = index
        }
        val routeCoords = buildList {
            origin?.let { add(LatLng(it.latitude, it.longitude)) }
            plan.forEach { add(LatLng(it.latitude, it.longitude)) }
        }
        if (routeCoords.size > 1) m.addPolyline(PolylineOptions()
            .addAll(routeCoords).color(Color.rgb(210, 120, 20)).width(5f))
        if (!centered) {
            centered = true
            if (plan.isNotEmpty()) fitPlan() else locateRecovery()
        }
        requestedLocate?.let { locatePhoneFix(it.latitude, it.longitude) }
    }
    fun locatePhoneFix(latitude: Double, longitude: Double) {
        requestedLocate = LatLng(latitude, longitude)
        val m = map ?: return
        if (m.style?.isFullyLoaded != true) return
        m.cameraPosition = CameraPosition.Builder().target(requestedLocate).zoom(17.0).build()
        requestedLocate = null; centered = true
    }
    fun locateRecovery() {
        val last = session.rover.lastOrNull() ?: session.phone.lastOrNull() ?: session.home
        // Before GPS, this is only a camera origin; it is never stored as Home or a waypoint.
        locatePhoneFix(last?.latitude ?: 13.6218, last?.longitude ?: 123.1948)
    }
    fun fitPlan() {
        val m = map
        if (m == null || m.style?.isFullyLoaded != true || view.width == 0 || view.height == 0) {
            pendingFit = true
            view.post { if (view.width > 0 && view.height > 0 && map?.style?.isFullyLoaded == true) fitPlan() }
            return
        }
        pendingFit = false
        if (plan.isEmpty()) return
        val coordinates = plan.map { LatLng(it.latitude, it.longitude) }
        if (coordinates.distinct().size == 1) locatePhoneFix(plan[0].latitude, plan[0].longitude)
        else m.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.Builder().includes(coordinates).build(), (24 * view.resources.displayMetrics.density).toInt()))
    }
    fun onStart() = view.onStart()
    fun onResume() = view.onResume()
    fun onPause() = view.onPause()
    fun onStop() = view.onStop()
    fun onDestroy() { onAddWaypoint = null; onEditWaypoint = null; view.onDestroy(); map = null }
    fun onLowMemory() = view.onLowMemory()
    fun onSaveInstanceState(out: Bundle) = view.onSaveInstanceState(out)
}
