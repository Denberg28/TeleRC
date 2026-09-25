package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Phone branch and measured rover positions are separate layers. Camera moves only at Home/Locate. */
class RouteMapView(context: Context, private val session: RouteSession) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var homeMarker: Marker? = null
    private var phoneMarker: Marker? = null
    private var roverMarker: Marker? = null
    private var phoneLine: Polyline? = null
    private var roverLine: Polyline? = null
    private var roverSource: GeoJsonSource? = null
    private var roverLayer: SymbolLayer? = null
    private var previewSource: GeoJsonSource? = null
    private var previewLayer: SymbolLayer? = null
    private var previewPose: RoverPose? = null
    private var previewOrigin: RoverPose? = null
    private var lastPreviewDraw = 0L
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
            ready.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                installRoverIcon(style)
                draw()
            }
        }
    }

    private fun installRoverIcon(style: Style) {
        // Draw a top-down rover pointing north. MapLibre rotates it clockwise by telemetry heading.
        fun icon(bodyColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 28, 41) }
        canvas.drawRoundRect(10f, 10f, 17f, 46f, 3f, 3f, paint)
        canvas.drawRoundRect(39f, 10f, 46f, 46f, 3f, 3f, paint)
        paint.color = bodyColor
        canvas.drawRoundRect(18f, 11f, 38f, 45f, 5f, 5f, paint)
        paint.color = Color.rgb(178, 232, 239)
        canvas.drawRect(21f, 17f, 35f, 26f, paint)
        paint.color = Color.rgb(255, 218, 109)
        canvas.drawPath(Path().apply { moveTo(28f, 3f); lineTo(18f, 14f); lineTo(38f, 14f); close() }, paint)
        return bitmap
        }
        style.addImage("telerc-rover-heading", icon(Color.rgb(112, 88, 166)))
        style.addImage("telerc-preview-heading", icon(Color.rgb(42, 153, 191)))
        roverSource = GeoJsonSource("telerc-rover-position", FeatureCollection.fromFeatures(arrayOf<Feature>()))
        style.addSource(roverSource!!)
        roverLayer = SymbolLayer("telerc-rover-heading-layer", "telerc-rover-position").apply {
            setProperties(
                PropertyFactory.iconImage("telerc-rover-heading"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
            )
        }
        style.addLayer(roverLayer!!)
        previewSource = GeoJsonSource("telerc-preview-position", FeatureCollection.fromFeatures(arrayOf<Feature>()))
        style.addSource(previewSource!!)
        previewLayer = SymbolLayer("telerc-preview-layer", "telerc-preview-position").apply {
            setProperties(
                PropertyFactory.iconImage("telerc-preview-heading"),
                PropertyFactory.iconOpacity(.75f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
            )
        }
        style.addLayer(previewLayer!!)
    }

    /** Display a cyan simulation at Home only when there is no measured rover position. */
    internal fun updatePreview(pose: RoverPose) {
        previewPose = pose
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewDraw >= 100L) { lastPreviewDraw = now; drawPreview() }
    }

    private fun drawPreview() {
        val source = previewSource ?: return
        val home = session.home
        val pose = previewPose
        if (home == null || pose == null || session.rover.isNotEmpty()) {
            source.setGeoJson(FeatureCollection.fromFeatures(arrayOf<Feature>()))
            return
        }
        val origin = previewOrigin ?: pose.also { previewOrigin = it }
        val location = previewLocation(home, origin, pose)
        source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(
            Point.fromLngLat(location.longitude, location.latitude)))))
        previewLayer?.setProperties(PropertyFactory.iconRotate(location.headingDegrees.toFloat()))
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
            if (!centered && view.visibility == android.view.View.VISIBLE && view.width > 0 && view.height > 0) {
                locateHome(); centered = true
            }
        }
        session.phone.lastOrNull()?.let {
            phoneMarker = m.addMarker(MarkerOptions().position(coords(it)).title("Phone · current fix"))
        }
        session.rover.lastOrNull()?.let {
            if (it.headingDegrees != null) {
                roverSource?.setGeoJson(FeatureCollection.fromFeatures(arrayOf(
                    Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)))))
                roverLayer?.setProperties(PropertyFactory.iconRotate(it.headingDegrees.toFloat()))
            } else {
                roverSource?.setGeoJson(FeatureCollection.fromFeatures(arrayOf<Feature>()))
                roverMarker = m.addMarker(MarkerOptions().position(coords(it)).title("Rover · heading unavailable"))
            }
        }
        if (session.rover.isEmpty()) roverSource?.setGeoJson(FeatureCollection.fromFeatures(arrayOf<Feature>()))
        drawPreview()
        if (session.phone.size > 1) phoneLine = m.addPolyline(
            PolylineOptions().addAll(session.phone.map(::coords)).color(Color.rgb(42, 153, 191)).width(5f))
        if (session.rover.size > 1) roverLine = m.addPolyline(
            PolylineOptions().addAll(session.rover.map(::coords)).color(Color.rgb(112, 88, 166)).width(6f))
    }

    fun locateHome() {
        val home = session.home ?: return
        map?.cameraPosition = CameraPosition.Builder().target(LatLng(home.latitude, home.longitude)).zoom(17.0).build()
    }

    fun reset() { centered = false; previewOrigin = null; draw() }
    fun onStart() = view.onStart()
    fun onResume() = view.onResume()
    fun onPause() = view.onPause()
    fun onStop() = view.onStop()
    fun onDestroy() { view.onDestroy(); roverSource = null; roverLayer = null; previewSource = null; previewLayer = null; map = null }
    fun onLowMemory() = view.onLowMemory()
    fun onSaveInstanceState(out: Bundle) = view.onSaveInstanceState(out)
}
