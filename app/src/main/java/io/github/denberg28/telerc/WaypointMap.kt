package io.github.denberg28.telerc

import android.content.Context
import android.graphics.*
import android.view.View
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*

/** Own MapView and lifecycle: never reconfigures the Controls map or its route session. */
class WaypointMap(context: Context, private val onPoint: (Waypoint)->Unit, private val onMapStatus: (String)->Unit) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var points = emptyList<Waypoint>()
    private var rover: Waypoint? = null
    private var line: Polyline? = null
    private val markers=mutableListOf<Marker>()
    private val icons by lazy { IconFactory.getInstance(context) }
    private var centered=false
    private var destroyed=false
    private var fallback=false
    private val iconCache=mutableMapOf<String,Icon>()
    init {
        MapLibre.getInstance(context)
        view=MapView(context).apply { contentDescription="Waypoint map; tap to choose a destination"; onCreate(null) }
        view.addOnDidFailLoadingMapListener {
            if (!destroyed && !fallback) {
                fallback=true
                onMapStatus("Street tiles unavailable · use coordinates; background is a planning grid")
                map?.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"plain","type":"background","paint":{"background-color":"#243442"}}]}""")) { draw() }
            }
        }
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map=ready
            ready.addOnMapClickListener { p -> onPoint(Waypoint(p.latitude,p.longitude)); true }
            ready.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                if (!destroyed) { if (!fallback) onMapStatus("Tap map to plan · verify clear ground on site"); draw() }
            }
        }
    }
    fun update(route: List<Waypoint>, measured: Waypoint?) {
        if (points==route && rover==measured) return
        points=route.toList(); rover=measured; draw()
    }
    private fun icon(label: String, color: Int): Icon {
        iconCache[label]?.let { return it }
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.WHITE; canvas.drawCircle(32f,32f,29f,paint)
        paint.color=color; canvas.drawCircle(32f,32f,25f,paint)
        paint.color=Color.WHITE; paint.textSize=28f; paint.typeface=Typeface.DEFAULT_BOLD; paint.textAlign=Paint.Align.CENTER
        canvas.drawText(label,32f,42f,paint)
        return icons.fromBitmap(bitmap).also { iconCache[label]=it }
    }
    private fun draw() {
        val m=map ?: return
        if (destroyed || m.style?.isFullyLoaded!=true) return
        line?.let(m::removePolyline); line=null
        markers.forEach(m::removeMarker); markers.clear()
        fun coord(p: Waypoint)=LatLng(p.latitude,p.longitude)
        points.forEachIndexed { index,p -> markers.add(m.addMarker(MarkerOptions().position(coord(p))
            .title("Waypoint ${index+1}").snippet(p.label()).icon(icon("${index+1}",Color.rgb(34,102,167))))) }
        rover?.let { markers.add(m.addMarker(MarkerOptions().position(coord(it)).title("Last measured rover GPS")
            .icon(icon("R",Color.rgb(114,73,172))))) }
        val path=(listOfNotNull(rover)+points).map(::coord)
        if (path.size>1) line=m.addPolyline(PolylineOptions().addAll(path).color(Color.rgb(49,164,216)).width(4f))
        if (!centered) {
            val anchor=rover ?: points.firstOrNull() ?: Waypoint(13.6218,123.1948)
            m.cameraPosition=CameraPosition.Builder().target(coord(anchor)).zoom(18.0).build(); centered=true
        }
    }
    fun locate() {
        val point=rover ?: points.firstOrNull() ?: return
        map?.cameraPosition=CameraPosition.Builder().target(LatLng(point.latitude,point.longitude)).zoom(18.0).build()
    }
    fun start()=view.onStart()
    fun resume()=view.onResume()
    fun pause()=view.onPause()
    fun stop()=view.onStop()
    fun destroy() { destroyed=true; view.onDestroy(); map=null; markers.clear(); line=null }
    fun lowMemory()=view.onLowMemory()
}
