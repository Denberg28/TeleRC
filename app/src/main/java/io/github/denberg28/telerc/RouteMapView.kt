package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Phone branch and measured rover positions are separate layers. Camera moves only at Home/Locate. */
class RouteMapView(context: Context, private val session: RouteSession) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var homeMarker: Marker? = null
    private var phoneMarker: Marker? = null
    private var roverMarker: Marker? = null
    private var phoneLine: Polyline? = null
    private var roverLine: Polyline? = null
    private var centered = false

    init {
        MapLibre.getInstance(context)
        view = MapView(context)
        view.onCreate(null)
        view.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isCompassEnabled = true
            ready.uiSettings.isZoomGesturesEnabled = true
            ready.uiSettings.isRotateGesturesEnabled = true
            ready.uiSettings.isScrollGesturesEnabled = true
            ready.setStyle("https://demotiles.maplibre.org/style.json") { draw() }
        }
    }

    fun draw() {
        val m = map ?: return
        if (m.style?.isFullyLoaded != true) return
        homeMarker?.let(m::removeMarker); phoneMarker?.let(m::removeMarker)
        roverMarker?.let(m::removeMarker)
        phoneLine?.let(m::removePolyline); roverLine?.let(m::removePolyline)
        fun coords(p: TrackPoint) = LatLng(p.latitude, p.longitude)
        session.home?.let {
            homeMarker = m.addMarker(MarkerOptions().position(coords(it)).title("HOME · fixed phone GPS"))
            if (!centered) { locateHome(); centered = true }
        }
        session.phone.lastOrNull()?.let {
            phoneMarker = m.addMarker(MarkerOptions().position(coords(it)).title("Phone · current fix"))
        }
        session.rover.lastOrNull()?.let {
            roverMarker = m.addMarker(MarkerOptions().position(coords(it)).title("Rover · position telemetry"))
        }
        if (session.phone.size > 1) phoneLine = m.addPolyline(
            PolylineOptions().addAll(session.phone.map(::coords)).color(Color.rgb(42, 153, 191)).width(5f))
        if (session.rover.size > 1) roverLine = m.addPolyline(
            PolylineOptions().addAll(session.rover.map(::coords)).color(Color.rgb(112, 88, 166)).width(6f))
    }

    fun locateHome() {
        val home = session.home ?: return
        map?.cameraPosition = CameraPosition.Builder().target(LatLng(home.latitude, home.longitude)).zoom(17.0).build()
    }

    fun reset() { centered = false; draw() }
    fun onStart() = view.onStart()
    fun onResume() = view.onResume()
    fun onPause() = view.onPause()
    fun onStop() = view.onStop()
    fun onDestroy() = view.onDestroy()
    fun onLowMemory() = view.onLowMemory()
    fun onSaveInstanceState(out: Bundle) = view.onSaveInstanceState(out)
}
