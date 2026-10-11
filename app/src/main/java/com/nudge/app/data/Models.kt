package com.nudge.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Domain models used by the UI. Display strings are formatted by [Backend] (IST). */
enum class Role { PASSENGER, DRIVER, ADMIN }

data class User(val id: String, val name: String, val phone: String, val role: Role)

enum class PlanKind { Unlimited, Rides, Single }
enum class Validity { MonthEnd, SameDay, Days }

/** Price applies when bought on day-of-month [fromDay]..[toDay]. */
data class PriceTier(val fromDay: Int, val toDay: Int, val price: Int)

data class Plan(
  val id: String, val title: String, val kind: PlanKind, val rides: Int?,
  /** Base price (used when no tier matches). */
  val basePrice: Int,
  /** What it costs if bought today. */
  val price: Int,
  val tiers: List<PriceTier>, val validity: Validity, val validityDays: Int,
  val description: String, val popular: Boolean, val active: Boolean, val sort: Int,
  /** e.g. "31 Oct" for month-end plans bought today. */
  val validUntil: String,
)

data class UpiAccount(val id: String, val vpa: String, val payee: String, val active: Boolean, val primary: Boolean)

data class Rules(
  val cancelCutoffMin: Int, val bookingWindowDays: Int, val allowPendingBooking: Boolean, val supportName: String, val supportPhone: String,
  /** ISO weekdays the shuttle runs (1 = Monday ... 7 = Sunday). */
  val serviceDays: Set<Int> = setOf(1, 2, 3, 4, 5),
) {
  fun runsOn(d: LocalDate) = d.dayOfWeek.value in serviceDays
}

data class Catalog(val plans: List<Plan>, val upi: List<UpiAccount>, val routes: List<RouteInfo>, val rules: Rules) {
  val oneWay get() = plans.firstOrNull { it.kind == PlanKind.Single && it.active }
  val payTo get() = upi.filter { it.active }.sortedByDescending { it.primary }
}

enum class PassStatus { None, PendingApproval, Active }

/** The rider's best pass right now. [canBook] is true for active passes and for pending ones when the operator allows it. */
data class Pass(
  val plan: Plan?, val ridesLeft: Int?, val ridesTotal: Int?, val validUntil: String, val status: PassStatus, val canBook: Boolean,
  /** Last travel day the pass covers (IST); null = no pass. */
  val expiresOn: java.time.LocalDate? = null,
)

/** [time] is the scheduled pickup, "07:57 AM". */
data class Stop(val id: String, val name: String, val lat: Double, val lng: Double, val time: String)

data class RouteInfo(
  val id: String, val name: String, val from: String, val to: String,
  val morning: List<Stop>, val evening: List<Stop>,
  val active: Boolean, val driverId: String?, val driverName: String?, val driverPhone: String?,
  val vehicleId: String?, val shuttleCode: String,
) {
  fun stops(evening: Boolean) = if (evening) this.evening else morning
  /** The last stop of a shift is drop-only. */
  fun pickups(evening: Boolean) = stops(evening).dropLast(1)
  val morningTime get() = morning.firstOrNull()?.time ?: "--"
  val eveningTime get() = evening.firstOrNull()?.time ?: "--"
  val durationMins get() = minutesBetween(morning.firstOrNull()?.time, morning.lastOrNull()?.time)
}

enum class RideStatus { Upcoming, Completed, NoShow, Cancelled }

data class Ride(
  val id: String, val dateLabel: String, val shift: String, val routeName: String, val from: String, val to: String,
  /** Pickup time at the rider's stop. */
  val time: String, val pickup: String, val shuttleCode: String, val status: RideStatus, val tripId: String,
  val routeId: String, val paymentPending: Boolean,
  /** Scheduled pickup instant (for the cancel cut-off). */
  val pickupAt: java.time.Instant? = null,
)

enum class PaymentStatus { Pending, Approved, Rejected }

data class PaymentRequest(
  val id: String, val riderName: String, val phone: String, val planTitle: String, val amount: Int,
  val utr: String, val timeLabel: String, val status: PaymentStatus, val paidTo: String?,
)

enum class BoardingStatus { Waiting, Boarded, NoShow }

data class TripPassenger(
  val id: String, val name: String, val phone: String, val stopName: String, val stopId: String, val status: BoardingStatus,
  val boardedAt: String? = null, val paymentPending: Boolean = false, val oneWay: Boolean = false,
)

data class TripStop(val id: String, val name: String, val time: String)

data class DriverTrip(
  val id: String, val routeCode: String, val routeName: String, val departure: String, val shuttleCode: String,
  val stops: List<TripStop>, val passengers: List<TripPassenger>, val currentStopIndex: Int, val started: Boolean,
  /** null = today; otherwise e.g. "Tomorrow" or "Mon, 12 Oct" (the trip can only be started on its day). */
  val dayLabel: String? = null,
)

/** Who drives a route on one day and shift ([driverId] null = nobody); [started] trips can't be changed. */
data class DayDriver(val date: LocalDate, val evening: Boolean, val driverId: String?, val started: Boolean)

enum class VehicleState { OnRoute, Delayed, Standby }
data class Vehicle(
  val code: String, val description: String, val state: VehicleState, val chip: String, val tripId: String? = null, val position: Position? = null,
)

/** A shuttle as the operator edits it. */
data class Shuttle(val id: String, val code: String, val seats: Int, val active: Boolean)

/** [id] is the profile id once they have logged in (needed to assign a driver to a route). */
data class StaffMember(val id: String?, val phone: String, val name: String, val role: Role, val joined: Boolean)

/** [heading] = direction of travel in degrees from north (GPS bearing), when known. */
data class Position(val lat: Double, val lng: Double, val speedKmh: Float?, val atMillis: Long, val heading: Float? = null)

data class FleetStats(val activeTrips: Int, val bookingsToday: Int, val fleetUp: Int, val fleetTotal: Int)

private val clock12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
fun parseClock(t: String): LocalTime = LocalTime.parse(t.uppercase(Locale.ENGLISH), clock12)
fun formatClock(t: LocalTime): String = t.format(clock12)

fun minutesBetween(a: String?, b: String?): Int =
  if (a == null || b == null) 0 else java.time.Duration.between(parseClock(a), parseClock(b)).toMinutes().toInt().let { if (it < 0) it + 1440 else it }

/** "+91 98765 43210" for display; tel: links use the raw digits. */
fun prettyPhone(p: String) = p.takeLast(10).let { if (it.length == 10) "+91 ${it.take(5)} ${it.drop(5)}" else p }

/** Compass bearing in degrees (0 = north) from [a] to [b]; null when they are the same point. */
fun bearingDeg(a: Position, b: Position): Float? {
  if (a.lat == b.lat && a.lng == b.lng) return null
  val la1 = Math.toRadians(a.lat); val la2 = Math.toRadians(b.lat); val dLng = Math.toRadians(b.lng - a.lng)
  val y = Math.sin(dLng) * Math.cos(la2)
  val x = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dLng)
  return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
}

/** Last position with a heading: the GPS bearing, else the direction from the previous point. */
fun List<Position>.withHeading(): Position? = lastOrNull()?.let { p ->
  p.heading?.let { return p }
  p.copy(heading = asReversed().drop(1).firstNotNullOfOrNull { bearingDeg(it, p) })
}

/** Great-circle distance in km. */
fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
  val r = 6371.0
  val dLat = Math.toRadians(lat2 - lat1)
  val dLng = Math.toRadians(lng2 - lng1)
  val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
  return 2 * r * Math.asin(Math.sqrt(a))
}

/**
 * Road path of one shift (from OpenRouteService via the `route-shape` edge function): [coords] as (lat, lng) pairs,
 * [wayPoints] = index in [coords] of each stop, drive time/distance between consecutive stops.
 */
class RouteShape(val coords: List<Pair<Double, Double>>, val wayPoints: List<Int>, val legSeconds: List<Int>, val legMeters: List<Int>) {
  /** Only valid for the stop list it was computed for; a stale shape (stops edited, not yet recomputed) is ignored. */
  fun fits(stops: List<Stop>) = wayPoints.size == stops.size && stops.indices.all { i ->
    coords[wayPoints[i]].let { (la, ln) -> distanceKm(la, ln, stops[i].lat, stops[i].lng) < 1.0 }
  }

  /**
   * Road distance (km) and drive time (min) from the shuttle at ([lat], [lng]) to stop [stop]; null when the shuttle
   * is more than 1 km off the route (detour, depot), where the caller falls back to straight-line.
   */
  fun eta(lat: Double, lng: Double, stop: Int): Pair<Double, Int>? {
    val end = wayPoints.getOrNull(stop) ?: return null
    // ponytail: nearest point on the path up to the pickup; a route that loops back past itself could match the wrong pass.
    val v = (0..end).minBy { coords[it].let { (la, ln) -> distanceKm(la, ln, lat, lng) } }
    if (coords[v].let { (la, ln) -> distanceKm(la, ln, lat, lng) } > 1.0) return null
    var km = 0.0
    var secs = 0.0
    for (i in v until end) {
      val leg = wayPoints.indexOfLast { it <= i }.coerceIn(0, legSeconds.lastIndex)
      val d = coords[i].let { (a, b) -> coords[i + 1].let { (c, e) -> distanceKm(a, b, c, e) } }
      km += d
      secs += d * 1000 * legSeconds[leg] / legMeters[leg].coerceAtLeast(1)
    }
    val stopsBetween = wayPoints.count { it in (v + 1) until end }
    return km to ((secs + stopsBetween * 30) / 60).toInt() // ponytail: 30 s dwell per stop, no live traffic
  }
}
