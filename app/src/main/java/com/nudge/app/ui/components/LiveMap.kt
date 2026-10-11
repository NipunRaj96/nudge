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
import com.nudge.app.data.distanceKm
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
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
 * Real map: shuttle icons at [vehicles] (turned to their heading), the rider's own [highlight] pickup pin, and for
 * playback the driven [trail]. No planned route or stop dots: riders know the road, they need where the shuttle is.
 * Fits everything shown on first load; with [follow] the camera follows the first vehicle.
 */
@Composable
fun LiveMap(
  modifier: Modifier = Modifier,
  trail: List<Position> = emptyList(),
  vehicles: List<Position> = emptyList(),
  follow: Boolean = false,
  topInsetDp: Int = 120,
  /** Height covered at the bottom (sheet, buttons): framing keeps markers above it. */
  bottomInsetDp: Int = topInsetDp * 3,
  interactive: Boolean = true,
  /** Start centred here (map picker); the camera centre is reported to [onCenter] whenever the map stops moving. */
  center: Position? = null,
  onCenter: ((Double, Double) -> Unit)? = null,
  /** The rider's own pickup stop. */
  highlight: Stop? = null,
  /** The phone's own location ("you"). */
  me: Position? = null,
) {
  val ctx = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val topPx = with(LocalDensity.current) { topInsetDp.dp.roundToPx() }
  val bottomPx = with(LocalDensity.current) { bottomInsetDp.dp.roundToPx() }
  val sidePx = with(LocalDensity.current) { 48.dp.roundToPx() }
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
        m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s -> addLayers(s, ctx.resources.displayMetrics.density); style = s }
        if (onCenter != null) m.addOnCameraIdleListener { m.cameraPosition.target?.let { onCenter(it.latitude, it.longitude) } }
        map = m
      }
    }
  }, modifier = modifier)

  LaunchedEffect(style, trail, vehicles, highlight, me) {
    val s = style ?: return@LaunchedEffect
    fun pt(lat: Double, lng: Double) = Point.fromLngLat(lng, lat)
    s.source("trail")?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(trail.map { pt(it.lat, it.lng) })))
    s.source("pickup")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(highlight).map { Feature.fromGeometry(pt(it.lat, it.lng)) }))
    s.source("me")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(me).map { Feature.fromGeometry(pt(it.lat, it.lng)) }))
    s.source("vehicle")?.setGeoJson(FeatureCollection.fromFeatures(vehicles.map { v ->
      Feature.fromGeometry(pt(v.lat, v.lng)).apply { addNumberProperty("heading", v.heading ?: 0f) }
    }))
  }

  // Frame the pickup, shuttle and trail once (again when the pickup changes or the first fix arrives).
  LaunchedEffect(map, highlight, vehicles.isEmpty(), trail.size > 1, me == null) {
    val m = map ?: return@LaunchedEffect
    val points = listOfNotNull(highlight).map { LatLng(it.lat, it.lng) } + (trail + vehicles + listOfNotNull(me)).map { LatLng(it.lat, it.lng) }
    when {
      center != null -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.lat, center.lng), 15.0))
      points.size >= 2 -> m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), sidePx, topPx, sidePx, bottomPx))
      points.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 14.0))
      else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(28.57, 77.25), 10.0)) // Delhi NCR
    }
  }
  LaunchedEffect(map, follow, vehicles.firstOrNull(), me == null) {
    val m = map ?: return@LaunchedEffect
    val v = vehicles.firstOrNull() ?: return@LaunchedEffect
    if (!follow) return@LaunchedEffect
    // Keep the rider's pickup (and the rider, when they're near it) in view next to the shuttle; with no pickup, just
    // centre on the shuttle.
    val update = if (highlight != null) CameraUpdateFactory.newLatLngBounds(
      LatLngBounds.Builder().include(LatLng(v.lat, v.lng)).include(LatLng(highlight.lat, highlight.lng))
        .apply { me?.takeIf { distanceKm(it.lat, it.lng, highlight.lat, highlight.lng) < 3 }?.let { include(LatLng(it.lat, it.lng)) } }.build(),
      sidePx, topPx, sidePx, bottomPx,
    ) else CameraUpdateFactory.newLatLngZoom(LatLng(v.lat, v.lng), maxOf(m.cameraPosition.zoom, 12.5))
    m.animateCamera(update, 600)
  }
}

private fun Style.source(id: String) = getSourceAs<GeoJsonSource>(id)

private fun addLayers(s: Style, density: Float) {
  listOf("trail", "pickup", "me", "vehicle").forEach { s.addSource(GeoJsonSource(it)) }
  val primary = "#b8c3ff"
  s.addImage("shuttle", shuttleBitmap(density))
  s.addLayer(LineLayer("trail-line", "trail").withProperties(
    lineColor(primary), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
  ))
  s.addLayer(CircleLayer("pickup-dot", "pickup").withProperties(
    circleRadius(8f), circleColor(primary), circleStrokeColor("#131313"), circleStrokeWidth(3f),
  ))
  s.addLayer(CircleLayer("me-dot", "me").withProperties( // "you": white-ringed light dot, distinct from the pickup pin
    circleRadius(7f), circleColor("#4fc3f7"), circleStrokeColor("#ffffff"), circleStrokeWidth(2.5f),
  ))
  s.addLayer(SymbolLayer("vehicle-icon", "vehicle").withProperties(
    iconImage("shuttle"), iconRotate(get("heading")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
    iconAllowOverlap(true), iconIgnorePlacement(true),
  ))
}

/** Shuttle seen from above (nose up, so rotating by heading points it along the road): blue body, white outline, windscreen. */
private fun shuttleBitmap(density: Float): Bitmap {
  val w = 22 * density; val h = 40 * density; val pad = 3 * density
  val bmp = Bitmap.createBitmap((w + 2 * pad).toInt(), (h + 2 * pad).toInt(), Bitmap.Config.ARGB_8888)
  val c = Canvas(bmp)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  val body = RectF(pad, pad, pad + w, pad + h)
  paint.color = android.graphics.Color.WHITE
  c.drawRoundRect(body, 7 * density, 7 * density, paint)
  body.inset(2.5f * density, 2.5f * density)
  paint.color = android.graphics.Color.parseColor("#2e5bff")
  c.drawRoundRect(body, 5 * density, 5 * density, paint)
  paint.color = android.graphics.Color.parseColor("#dce1ff") // windscreen at the front
  c.drawRoundRect(RectF(body.left + 2.5f * density, body.top + 3 * density, body.right - 2.5f * density, body.top + 10 * density), 2.5f * density, 2.5f * density, paint)
  paint.color = android.graphics.Color.parseColor("#1f45d6") // roof panel
  c.drawRoundRect(RectF(body.left + 3 * density, body.top + 14 * density, body.right - 3 * density, body.bottom - 4 * density), 2 * density, 2 * density, paint)
  return bmp
}
