package com.nudge.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free place search (OpenStreetMap Nominatim) for stop names: used when importing a timetable and by the stop search
 * in the route editor. Nominatim allows ~1 request/second with an identifying User-Agent; we stay under that.
 */
object Geocoder {
  private var last = 0L
  /** Bias results to Delhi NCR so "Sector 62" finds Noida, not some other city. */
  private const val VIEWBOX = "76.8,28.9,77.7,28.3"

  data class Place(val name: String, val lat: Double, val lng: Double)

  suspend fun search(query: String, limit: Int = 6): List<Place> = withContext(Dispatchers.IO) {
    val wait = 1100 - (System.currentTimeMillis() - last)
    if (wait > 0) delay(wait)
    last = System.currentTimeMillis()
    runCatching {
      val url = URL(
        "https://nominatim.openstreetmap.org/search?format=json&countrycodes=in&bounded=1&viewbox=$VIEWBOX&limit=$limit&q=" +
          URLEncoder.encode(query, "UTF-8"),
      )
      val conn = (url.openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", "NudgeShuttle/1.0 (operator route editor)")
        connectTimeout = 8000; readTimeout = 8000
      }
      val body = conn.inputStream.bufferedReader().use { it.readText() }
      (Json.parseToJsonElement(body) as JsonArray).map {
        val o = it.jsonObject
        Place(o["display_name"]!!.jsonPrimitive.content, o["lat"]!!.jsonPrimitive.content.toDouble(), o["lon"]!!.jsonPrimitive.content.toDouble())
      }
    }.getOrDefault(emptyList())
  }

  /** Best guess for a stop name, trying the full name then simpler variants. Null when nothing is found. */
  suspend fun find(stopName: String): Pair<Double, Double>? {
    val cleaned = stopName.replace(Regex("(?i)\\b(opp|opposite|gate\\s*(no)?\\.?\\s*-?\\s*\\d+|near)\\b"), " ").replace(Regex("[()/-]"), " ").replace(Regex("\\s+"), " ").trim()
    for (q in listOf(stopName, cleaned, "$cleaned Noida").distinct()) {
      search(q, 1).firstOrNull()?.let { return it.lat to it.lng }
    }
    return null
  }
}
