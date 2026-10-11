package com.nudge.app

import com.nudge.app.data.PriceTier
import com.nudge.app.data.RouteShape
import com.nudge.app.data.Stop
import com.nudge.app.data.csv
import com.nudge.app.data.minutesBetween
import com.nudge.app.data.priceOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Client-side logic that mirrors the server (server rules: supabase/tests/smoke.mjs). */
class PureLogicTest {
  private val tiers = listOf(PriceTier(1, 10, 3499), PriceTier(11, 20, 2499), PriceTier(21, 31, 1999))

  @Test fun monthlyPriceDependsOnDayOfMonth() {
    assertEquals(3499, priceOn(9999, tiers, LocalDate.of(2026, 10, 10)))
    assertEquals(2499, priceOn(9999, tiers, LocalDate.of(2026, 10, 11)))
    assertEquals(1999, priceOn(9999, tiers, LocalDate.of(2026, 10, 31)))
    assertEquals(129, priceOn(129, emptyList(), LocalDate.of(2026, 10, 31)))
  }

  @Test fun csvQuotesAndDefusesFormulas() {
    assertEquals("\"a\",\"b \"\"x\"\"\",\"'=1+1\"", csv(listOf(listOf("a", "b \"x\"", "=1+1"))))
  }

  @Test fun durationAcrossTimes() {
    assertEquals(95, minutesBetween("07:42 AM", "09:17 AM"))
    assertEquals(0, minutesBetween(null, "09:17 AM"))
  }

  @Test fun roadEta() {
    // Three stops 0.01° of latitude (~1.11 km) apart, a path vertex every 0.005°, 2 min per leg.
    val coords = (0..4).map { 28.60 + it * 0.005 to 77.30 }
    val shape = RouteShape(coords, listOf(0, 2, 4), listOf(120, 120), listOf(1112, 1112))
    val stops = listOf(0, 2, 4).map { Stop("s$it", "S$it", coords[it].first, 77.30, "08:00 AM") }
    assertTrue(shape.fits(stops))
    assertFalse(shape.fits(stops.dropLast(1)))
    assertFalse(shape.fits(stops.mapIndexed { i, s -> if (i == 1) s.copy(lat = s.lat + 0.02) else s }))

    val (km, mins) = shape.eta(28.6001, 77.30, 2)!!
    assertEquals(2.22, km, 0.02)
    assertEquals(4, mins) // 240 s driving + 30 s at the middle stop
    assertEquals(0, shape.eta(28.62, 77.30, 2)!!.second) // at the pickup
    assertNull(shape.eta(28.60, 77.33, 2)) // ~3 km off the route
  }
}
