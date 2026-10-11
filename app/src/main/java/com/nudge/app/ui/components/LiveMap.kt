package com.nudge.app.ui.components

import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nudge.app.data.Position
import com.nudge.app.data.Stop
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Free vector tiles, no API key (OpenStreetMap data). Swap the style URL to change provider. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

/**
 * Real map: route through [stops] (dashed), driven [trail], stop dots and the shuttle dot at [vehicles].
 * Fits the route on first load; with [follow] the camera follows the first vehicle.
 */
@Composable
fun LiveMap(
  stops: List<Stop>,
  modifier: Modifier = Modifier,
  trail: List<Position> = emptyList(),
  vehicles: List<Position> = emptyList(),
  follow: Boolean = false,
  topInsetDp: Int = 120,
  interactive: Boolean = true,
  /** Start centred here (map picker); the camera centre is reported to [onCenter] whenever the map stops moving. */
  center: Position? = null,
  onCenter: ((Double, Double) -> Unit)? = null,
  /** The rider's own pickup stop, drawn larger. */
  highlight: Stop? = null,
  /** Road-following path (lat, lng) to draw instead of straight lines between [stops]. */
  road: List<Pair<Double, Double>>? = null,
) {
  val ctx = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val topPx = with(LocalDensity.current) { topInsetDp.dp.roundToPx() }
  val mapView = remember {
    MapLibre.getInstance(ctx)
    // Texture mode so the map fades/slides with Compose screen transitions.
    MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true)).apply { onCreate(null) }
  }
  var map by remember { mutableStateOf<MapLibreMap?>(null) }
  var style by remember { mutableStateOf<Style?>(null) }

  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, e ->
      when (e) {
        Lifecycle.Event.ON_START -> mapView.onStart()
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_STOP -> mapView.onStop()
        else -> {}
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
      if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
      mapView.onDestroy()
    }
  }

  AndroidView(factory = {
    mapView.apply {
      getMapAsync { m ->
        m.uiSettings.apply {
          isLogoEnabled = false
          isCompassEnabled = false
          isRotateGesturesEnabled = false
          isTiltGesturesEnabled = false
          if (!interactive) setAllGesturesEnabled(false)
          attributionGravity = Gravity.TOP or Gravity.START
          setAttributionMargins(topPx / 6, topPx, 0, 0)
        }
        m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s -> addLayers(s); style = s }
        if (onCenter != null) m.addOnCameraIdleListener { m.cameraPosition.target?.let { onCenter(it.latitude, it.longitude) } }
        map = m
      }
    }
  }, modifier = modifier)

  LaunchedEffect(style, stops, trail, vehicles, highlight, road) {
    val s = style ?: return@LaunchedEffect
    fun pt(lat: Double, lng: Double) = Point.fromLngLat(lng, lat)
    s.source("route")?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(road?.map { (la, ln) -> pt(la, ln) } ?: stops.map { pt(it.lat, it.lng) })))
    s.source("stops")?.setGeoJson(FeatureCollection.fromFeatures(stops.map { Feature.fromGeometry(pt(it.lat, it.lng)) }))
    s.source("trail")?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(trail.map { pt(it.lat, it.lng) })))
    s.source("pickup")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(highlight).map { Feature.fromGeometry(pt(it.lat, it.lng)) }))
    s.source("vehicle")?.setGeoJson(FeatureCollection.fromFeatures(vehicles.map { Feature.fromGeometry(pt(it.lat, it.lng)) }))
  }

  // Frame the whole route once (and whenever the route changes).
  LaunchedEffect(map, stops, if (stops.isEmpty()) vehicles.size else 0) {
    val m = map ?: return@LaunchedEffect
    val points = stops.map { LatLng(it.lat, it.lng) } + vehicles.map { LatLng(it.lat, it.lng) }
    when {
      center != null -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.lat, center.lng), 15.0))
      points.size >= 2 -> m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), topPx / 3, topPx, topPx / 3, topPx * 3))
      points.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 11.0))
      else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(28.57, 77.25), 10.0)) // Delhi NCR
    }
  }
  LaunchedEffect(map, follow, vehicles.firstOrNull()) {
    val m = map ?: return@LaunchedEffect
    val v = vehicles.firstOrNull() ?: return@LaunchedEffect
    if (follow) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(v.lat, v.lng), maxOf(m.cameraPosition.zoom, 12.5)), 600)
  }
}

private fun Style.source(id: String) = getSourceAs<GeoJsonSource>(id)

private fun addLayers(s: Style) {
  listOf("route", "trail", "stops", "pickup", "vehicle").forEach { s.addSource(GeoJsonSource(it)) }
  val primary = "#b8c3ff"
  val container = "#2e5bff"
  s.addLayer(LineLayer("route-line", "route").withProperties(
    lineColor(container), lineWidth(4f), lineDasharray(arrayOf(1.5f, 1.5f)), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
  ))
  s.addLayer(LineLayer("trail-line", "trail").withProperties(
    lineColor(primary), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
  ))
  s.addLayer(CircleLayer("stop-dots", "stops").withProperties(
    circleRadius(5f), circleColor("#131313"), circleStrokeColor(primary), circleStrokeWidth(2.5f),
  ))
  s.addLayer(CircleLayer("pickup-dot", "pickup").withProperties(
    circleRadius(8f), circleColor(primary), circleStrokeColor("#131313"), circleStrokeWidth(3f),
  ))
  s.addLayer(CircleLayer("vehicle-dot", "vehicle").withProperties(
    circleRadius(9f), circleColor(container), circleStrokeColor("#ffffff"), circleStrokeWidth(3f),
  ))
}
