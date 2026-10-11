package com.nudge.app.data

import com.nudge.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** A failure the rider/driver/operator should see as-is (already friendly text). */
class BackendError(message: String) : Exception(message)

private val IST = ZoneId.of("Asia/Kolkata")
private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private fun instant(s: String) = Instant.parse(s).atZone(IST)
private fun clock(t: String) = LocalTime.parse(t).format(timeFmt)
private fun today() = LocalDate.now(IST)

/**
 * Everything the app asks of the server: Supabase Auth (phone OTP via our SMS hook), PostgREST tables + RPCs
 * (business rules live in SQL, see supabase/migrations), Realtime for live GPS.
 */
class Backend(url: String, key: String, private val prefs: android.content.SharedPreferences) {
  private val client = createSupabaseClient(url, key) {
    requestTimeout = 20.seconds // slow 4G on the road
    install(Auth) {
      // Default Android behaviour drops the session while the app is in the background; the driver's GPS service
      // uploads in the background and a file picker pauses the app, so keep the session and keep refreshing it.
      enableLifecycleCallbacks = false
      alwaysAutoRefresh = true
    }
    install(Postgrest)
    install(Realtime)
    install(Functions)
  }
  private val db get() = client.postgrest
  private fun uid() = client.auth.currentUserOrNull()?.id ?: throw BackendError("Please sign in again.")

  /** Every call goes through here so the UI only ever sees [BackendError] with readable text. */
  // IO dispatcher: decoding is too slow for the main thread on cheap phones.
  private suspend fun <T> call(block: suspend () -> T): T = try {
    withContext(Dispatchers.IO) { block() }
  } catch (e: BackendError) {
    throw e
  } catch (e: PostgrestRestException) {
    throw BackendError(when (e.code) { "23505" -> "That already exists."; "23514" -> friendlyError("BAD_INPUT"); else -> friendlyError(e.error) })
  } catch (e: AuthRestException) {
    throw BackendError(if (e.error.contains("otp", true) || e.error.contains("token", true)) friendlyError("BAD_OTP") else e.description ?: friendlyError(null))
  } catch (e: HttpRequestException) {
    throw BackendError(friendlyError(null))
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: Exception) {
    android.util.Log.e("Nudge", "backend call failed", e)
    throw BackendError(friendlyError(null))
  }

  private suspend fun rpc(fn: String, args: JsonObject = buildJsonObject {}) = db.rpc(fn, args)

  // ── Auth. `phone` is the 10-digit Indian mobile number.
  suspend fun sendOtp(phone: String) = call { client.auth.signInWith(OTP) { this.phone = "+91$phone" } }

  suspend fun verifyOtp(phone: String, code: String) = call {
    client.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = "+91$phone", token = code)
    loadMe()
  }

  /** Saved login. Offline (dead zone, flight mode) the last known profile is used instead of logging the person out. */
  suspend fun restoreSession(): User? = withContext(Dispatchers.IO) {
    client.auth.awaitInitialization()
    if (client.auth.currentSessionOrNull() == null) return@withContext null
    try { call { loadMe() } } catch (e: BackendError) { cachedUser() ?: throw e }
  }

  private suspend fun loadMe() = db.from("profiles").select { filter { eq("id", uid()) } }.decodeSingle<ProfileRow>().toUser().also { u ->
    prefs.edit().putString("me", "${u.id}|${u.role.name}|${u.phone}|${u.name}").apply()
  }

  private fun cachedUser() = prefs.getString("me", null)?.split("|", limit = 4)?.takeIf { it.size == 4 }?.let { (id, role, phone, name) ->
    User(id, name, phone, Role.valueOf(role))
  }

  suspend fun signOut() {
    prefs.edit().remove("me").apply()
    withContext(Dispatchers.IO) { runCatching { client.auth.signOut() } }
  }

  suspend fun setName(name: String) = call {
    db.from("profiles").update({ set("name", name) }) { filter { eq("id", uid()) } }
    loadMe()
  }

  // ── Shared catalogue (riders see active items; the operator edits everything)
  suspend fun catalog() = call {
    val s = db.from("settings").select().decodeSingle<SettingsRow>()
    val plans = db.from("plans").select(Columns.raw("*, plan_price_tiers(from_day,to_day,price)")) { order("sort", Order.ASCENDING) }.decodeList<PlanRow>()
    val upi = db.from("upi_accounts").select { order("created_at", Order.ASCENDING) }.decodeList<UpiRow>()
    val routes = db.from("routes")
      .select(Columns.raw("*, stops(id,seq,shift,name,lat,lng,pickup_time), vehicle:vehicles(code), driver:profiles(name,phone)")) {
        filter { eq("archived", false); eq("stops.archived", false) }
        order("name", Order.ASCENDING)
      }
      .decodeList<RouteRow>()
    Catalog(plans.map { it.toPlan() }, upi.map { it.toUpi() }, routes.map { it.toRoute() }, s.toRules())
  }

  // ── Rider
  suspend fun pass(plans: List<Plan>) = call {
    val now = Instant.now()
    val rows = db.from("passes").select {
      filter { eq("rider_id", uid()); neq("status", "rejected") }
      order("expires_at", Order.ASCENDING)
    }.decodeList<PassRow>()
    val usable = rows.filter { Instant.parse(it.expiresAt) > now && (it.ridesLeft == null || it.ridesLeft > 0) }
    val p = usable.firstOrNull { it.status == "active" && plans.planFor(it)?.kind != PlanKind.Single }
      ?: usable.firstOrNull { it.status == "active" } ?: usable.firstOrNull()
    if (p == null) return@call Pass(null, null, null, "", PassStatus.None, false)
    val allowPending = db.from("settings").select().decodeSingle<SettingsRow>().allowPendingBooking
    val pending = p.status == "pending"
    val last = instant(p.expiresAt).minusSeconds(1)
    Pass(plans.planFor(p), p.ridesLeft, p.ridesTotal, last.format(shortDate),
      if (pending) PassStatus.PendingApproval else PassStatus.Active, !pending || allowPending, last.toLocalDate())
  }

  /** A payment the operator rejected in the last 7 days (shown so the rider knows why seats vanished). */
  suspend fun rejectedNotice() = call {
    db.from("payments").select(Columns.raw("amount,utr,resolved_at,plan:plans(title)")) {
      filter { eq("rider_id", uid()); eq("status", "rejected"); gte("resolved_at", Instant.now().minusSeconds(7 * 86400).toString()) }
      order("resolved_at", Order.DESCENDING); limit(1)
    }.decodeList<RejectedRow>().firstOrNull()?.let {
      "Your ₹${it.amount} payment${it.utr?.let { u -> " (ref $u)" }.orEmpty()} was not accepted by the operator. Seats booked with it were cancelled. Please pay again or call the office."
    }
  }

  private fun List<Plan>.planFor(p: PassRow) = firstOrNull { it.id == p.planId }

  suspend fun rides() = call {
    val rows = db.from("bookings").select(
      Columns.raw("id,status,trip:trips(id,route_id,service_date,shift,status,route:routes(name,from_label,to_label),vehicle:vehicles(code)),stop:stops(name,pickup_time),pass:passes(status)"),
    ) { filter { eq("rider_id", uid()) }; order("created_at", Order.DESCENDING); limit(100) }.decodeList<BookingRow>()
    val (upcoming, past) = rows.map { it.toRide() }.partition { it.first.status == RideStatus.Upcoming }
    upcoming.sortedBy { it.second }.map { it.first } + past.sortedByDescending { it.second }.map { it.first }
  }

  /** Records the UTR; the pass exists immediately (pending until the operator approves). [validOn] = travel day for one-way. */
  suspend fun submitPayment(planId: String, utr: String, vpa: String?, validOn: LocalDate?) = call {
    rpc("submit_payment", buildJsonObject {
      put("p_plan", planId); put("p_utr", utr); put("p_vpa", vpa); put("p_valid_on", validOn?.toString())
    })
    Unit
  }

  suspend fun bookRide(routeId: String, date: LocalDate, evening: Boolean, stopId: String) = call {
    rpc("book_ride", buildJsonObject {
      put("p_route", routeId); put("p_date", date.toString()); put("p_shift", if (evening) "evening" else "morning"); put("p_stop", stopId)
    }).decodeAs<String>()
  }

  suspend fun cancelRide(rideId: String) = call { rpc("cancel_booking", buildJsonObject { put("p_booking", rideId) }); Unit }

  // ── Driver. Trip passenger ids are booking ids.
  suspend fun driverTrip(): DriverTrip? = call {
    val t = rpc("driver_next_trip").decodeAs<TripRow>()
    if (t.id == null) return@call null
    val route = db.from("routes").select(Columns.raw("name,stops(id,name,seq,shift,pickup_time,archived)")) { filter { eq("id", t.routeId!!) } }
      .decodeSingle<RouteStopsRow>()
    val code = t.vehicleId?.let { id -> db.from("vehicles").select { filter { eq("id", id) } }.decodeSingleOrNull<VehicleRow>()?.code } ?: "—"
    val manifest = rpc("trip_manifest", buildJsonObject { put("p_trip", t.id) }).decodeList<ManifestRow>()
    val stops = route.stops.filter { it.shift == t.shift && !it.archived }.sortedBy { it.seq }
    DriverTrip(
      id = t.id, routeCode = code, routeName = "${route.name} · ${if (t.shift == "evening") "Evening" else "Morning"}",
      departure = instant(t.departsAt!!).format(timeFmt), shuttleCode = code,
      stops = stops.map { TripStop(it.id, it.name, LocalTime.parse(it.pickupTime).format(timeFmt)) },
      passengers = manifest.map {
        TripPassenger(it.bookingId, it.riderName, it.riderPhone.takeLast(10), it.stopName, it.stopId, it.status.toBoarding(),
          it.boardedAt?.let { b -> instant(b).format(timeFmt) }, it.passStatus == "pending", it.planKind == "single")
      },
      currentStopIndex = (t.currentStop ?: 0).coerceAtMost(stops.lastIndex.coerceAtLeast(0)), started = t.status == "live",
    )
  }

  private suspend fun tripCall(fn: String, tripId: String) = call { rpc(fn, buildJsonObject { put("p_trip", tripId) }); Unit }
  suspend fun startTrip(tripId: String) = tripCall("start_trip", tripId)
  /** [from] makes it idempotent: a double tap or a retry after a timeout never skips a stop. [step] -1 = previous stop. */
  suspend fun moveStop(tripId: String, from: Int, step: Int) = call {
    rpc("advance_stop", buildJsonObject { put("p_trip", tripId); put("p_from", from); put("p_step", step) }); Unit
  }
  suspend fun endTrip(tripId: String) = tripCall("end_trip", tripId)

  suspend fun setBoarding(bookingId: String, status: BoardingStatus) = call {
    val s = when (status) { BoardingStatus.Boarded -> "boarded"; BoardingStatus.NoShow -> "no_show"; BoardingStatus.Waiting -> "booked" }
    rpc("set_boarding", buildJsonObject { put("p_booking", bookingId); put("p_status", s) })
    Unit
  }

  suspend fun sendPosition(tripId: String, lat: Double, lng: Double, speedKmh: Float?, heading: Float?) = call {
    db.from("positions").insert(NewPosition(tripId, lat, lng, speedKmh, heading))
    Unit
  }

  // ── Operator
  suspend fun payments() = call {
    db.from("payments").select(Columns.raw("id,amount,utr,upi_vpa,status,created_at,rider:profiles!rider_id(name,phone),plan:plans(title)")) {
      order("created_at", Order.DESCENDING); limit(300)
    }.decodeList<PaymentRow>().map { it.toPayment() }
  }

  suspend fun resolvePayment(id: String, approve: Boolean) = call {
    rpc("resolve_payment", buildJsonObject { put("p_payment", id); put("p_approve", approve) })
    Unit
  }

  suspend fun setRouteActive(routeId: String, active: Boolean) = call {
    db.from("routes").update({ set("active", active) }) { filter { eq("id", routeId) } }
    Unit
  }

  suspend fun fleet() = call {
    val vehicles = db.from("vehicles").select { order("code", Order.ASCENDING) }.decodeList<VehicleRow>()
    val trips = db.from("trips").select(Columns.raw("id,status,shift,route_id,departs_at,vehicle_id,route:routes(name),bookings(count)")) {
      filter { eq("service_date", today().toString()); neq("bookings.status", "cancelled") }
    }.decodeList<FleetTripRow>()
    val now = Instant.now()
    val list = vehicles.map { v ->
      val mine = trips.filter { it.vehicleId == v.id }.sortedBy { it.departsAt }
      val t = mine.firstOrNull { it.status == "live" } ?: mine.firstOrNull { it.status == "scheduled" } ?: mine.lastOrNull()
      val late = t?.takeIf { it.status == "scheduled" }?.let { java.time.Duration.between(Instant.parse(it.departsAt), now).toMinutes() } ?: 0
      val (state, chip) = when {
        t?.status == "live" -> VehicleState.OnRoute to "ON ROUTE"
        late > 0 -> VehicleState.Delayed to "DELAYED ${late}m"
        t?.status == "done" -> VehicleState.Standby to "DONE"
        else -> VehicleState.Standby to "STANDBY"
      }
      val pos = t?.takeIf { it.status == "live" }?.let { live ->
        db.from("positions").select { filter { eq("trip_id", live.id) }; order("recorded_at", Order.DESCENDING); limit(1) }.decodeList<PositionRow>().firstOrNull()?.toPosition()
      }
      Vehicle(v.code, t?.route?.name ?: "No trip today", state, chip, t?.id, pos, t?.routeId, t?.shift == "evening")
    }
    FleetStats(trips.count { it.status == "live" }, trips.sumOf { it.bookings.firstOrNull()?.count ?: 0 }, vehicles.count { it.active }, vehicles.size) to list
  }

  suspend fun financeCsv() = call {
    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
    val rows = db.from("payments").select(Columns.raw("amount,utr,upi_vpa,created_at,resolved_at,rider:profiles!rider_id(name,phone),plan:plans(title)")) {
      filter { eq("status", "approved") }; order("created_at", Order.DESCENDING)
    }.decodeList<FinanceRow>()
    csv(listOf(financeHeader) + rows.map {
      listOf(instant(it.createdAt).format(stamp), it.resolvedAt?.let { r -> instant(r).format(stamp) }.orEmpty(), it.rider.name, it.rider.phone.takeLast(10),
        it.plan?.title.orEmpty(), it.amount.toString(), it.utr ?: "cash", it.upiVpa.orEmpty())
    })
  }

  /** Creates or updates a plan and replaces its price tiers (one transaction on the server). */
  suspend fun savePlan(p: Plan) = call {
    rpc("save_plan", buildJsonObject {
      put("p_plan", buildJsonObject {
        put("id", p.id); put("title", p.title); put("kind", p.kind.name.lowercase()); put("rides", p.rides); put("price", p.basePrice)
        put("validity", when (p.validity) { Validity.MonthEnd -> "month_end"; Validity.SameDay -> "same_day"; Validity.Days -> "days" })
        put("validity_days", p.validityDays); put("description", p.description); put("popular", p.popular); put("active", p.active); put("sort", p.sort)
        put("tiers", buildJsonArray { p.tiers.forEach { t -> addJsonObject { put("from_day", t.fromDay); put("to_day", t.toDay); put("price", t.price) } } })
      })
    })
    Unit
  }

  suspend fun saveUpi(a: UpiAccount) = call {
    val row = UpiWrite(a.vpa.trim(), a.payee.trim(), a.active)
    if (a.id.isBlank()) db.from("upi_accounts").insert(row) else db.from("upi_accounts").update(row) { filter { eq("id", a.id) } }
    Unit
  }

  suspend fun deleteUpi(id: String) = call { db.from("upi_accounts").delete { filter { eq("id", id) } }; Unit }

  suspend fun setPrimaryUpi(id: String) = call { rpc("set_primary_upi", buildJsonObject { put("p_id", id) }); Unit }

  suspend fun saveRules(r: Rules) = call {
    db.from("settings").update(RulesWrite(r.cancelCutoffMin, r.bookingWindowDays, r.allowPendingBooking, r.supportName.trim(), r.supportPhone.filter(Char::isDigit))) {
      filter { eq("id", 1) }
    }
    Unit
  }

  suspend fun staff() = call { rpc("staff_list").decodeList<StaffRow>().map { it.toStaff() } }

  suspend fun upsertStaff(phone: String, name: String, role: Role) = call {
    rpc("upsert_staff", buildJsonObject { put("p_phone", phone); put("p_name", name); put("p_role", if (role == Role.ADMIN) "admin" else "driver") })
    Unit
  }

  suspend fun removeStaff(phone: String) = call { rpc("remove_staff", buildJsonObject { put("p_phone", phone) }); Unit }

  suspend fun shuttles() = call {
    db.from("vehicles").select { order("code", Order.ASCENDING) }.decodeList<VehicleRow>().map { Shuttle(it.id, it.code, it.seats, it.active) }
  }

  suspend fun saveShuttle(s: Shuttle) = call {
    val row = VehicleWrite(s.code.trim().uppercase(), s.seats, s.active)
    if (s.id.isBlank()) db.from("vehicles").insert(row) else db.from("vehicles").update(row) { filter { eq("id", s.id) } }
    Unit
  }

  /** Creates (blank id) or updates a route with both stop lists in one transaction. Returns the route id. */
  suspend fun saveRoute(r: RouteInfo) = call {
    fun list(stops: List<Stop>) = buildJsonArray {
      stops.forEach { s ->
        addJsonObject {
          put("id", s.id.ifBlank { null }); put("name", s.name.trim()); put("lat", s.lat); put("lng", s.lng); put("time", parseClock(s.time).toString())
        }
      }
    }
    rpc("save_route", buildJsonObject {
      put("p_route", buildJsonObject {
        put("id", r.id.ifBlank { null }); put("name", r.name.trim()); put("from_label", r.from.trim()); put("to_label", r.to.trim())
        put("active", r.active); put("vehicle_id", r.vehicleId); put("driver_id", r.driverId)
        put("morning", list(r.morning)); put("evening", list(r.evening))
      })
    }).decodeAs<String>()
  }

  /** "deleted" when it was never used, otherwise "archived" (kept for history, hidden everywhere). */
  suspend fun deleteRoute(id: String) = call { rpc("delete_route", buildJsonObject { put("p_route", id) }).decodeAs<String>() }

  suspend fun copyRoute(id: String, name: String, shiftMinutes: Int) = call {
    rpc("copy_route", buildJsonObject { put("p_route", id); put("p_name", name); put("p_shift_minutes", shiftMinutes) }).decodeAs<String>()
  }

  /** Cash / offline payment recorded by the operator: the rider gets an active pass straight away. */
  suspend fun grantPass(phone: String, planId: String, amount: Int?, validOn: LocalDate?) = call {
    rpc("grant_pass", buildJsonObject { put("p_phone", phone); put("p_plan", planId); put("p_amount", amount); put("p_valid_on", validOn?.toString()) })
    Unit
  }

  /** Stop names + pins already used on any route: imported timetables reuse them instead of asking for pins again. */
  suspend fun knownStops() = call {
    db.from("stops").select(Columns.raw("name,lat,lng")) { limit(2000) }.decodeList<KnownStopRow>().associate { TimetableParser.normalize(it.name) to (it.lat to it.lng) }
  }

  // ── Tracking
  /** Road path for a route's shift, or null until the operator side has computed one (see [syncRouteShapes]). */
  suspend fun routeShape(routeId: String, evening: Boolean) = call {
    db.from("route_shapes").select { filter { eq("route_id", routeId); eq("shift", if (evening) "evening" else "morning") } }
      .decodeSingleOrNull<ShapeRow>()?.let { r -> RouteShape(r.coords.map { it[1] to it[0] }, r.wayPoints, r.legSeconds, r.legMeters) }
  }

  /** Operator only: recompute road paths for shifts whose stops changed (no-op server-side without an ORS key). */
  suspend fun syncRouteShapes() = call { client.functions.invoke("route-shape") }

  // ponytail: PostgREST caps a response at 1000 rows (~80 min at one point / 5 s); page it if trips get longer.
  suspend fun track(tripId: String) = call {
    db.from("positions").select { filter { eq("trip_id", tripId) }; order("recorded_at", Order.ASCENDING) }
      .decodeList<PositionRow>().map { it.toPosition() }
  }

  fun live(tripId: String): Flow<Position> = flow<Position> {
    val ch = client.channel("trip-$tripId-${System.nanoTime()}")
    val inserts = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
      table = "positions"
      filter("trip_id", FilterOperator.EQ, tripId)
    }
    ch.subscribe()
    try {
      emitAll(inserts.map { it.decodeRecord<PositionRow>().toPosition() })
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      android.util.Log.w("Nudge", "live updates stopped", e) // weak network: the screen keeps the last known point
    } finally {
      withContext(NonCancellable) { client.realtime.removeChannel(ch) }
    }
  }.flowOn(Dispatchers.IO)
}

object Backends {
  private lateinit var app: android.content.Context
  /** Called from MainActivity / the GPS service before first use (storage for the offline profile cache). */
  fun init(context: android.content.Context) { if (!::app.isInitialized) app = context.applicationContext }
  val instance: Backend by lazy {
    check(BuildConfig.SUPABASE_URL.isNotBlank()) { "Set nudge.supabaseUrl / nudge.supabaseKey in local.properties" }
    Backend(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, app.getSharedPreferences("nudge", android.content.Context.MODE_PRIVATE))
  }
}

/** Price if bought on [day] (IST): first matching tier, else the base price. Mirrors SQL plan_price(). */
fun priceOn(base: Int, tiers: List<PriceTier>, day: LocalDate) = tiers.sortedBy { it.fromDay }.firstOrNull { day.dayOfMonth in it.fromDay..it.toDay }?.price ?: base

/** RFC 4180 CSV: quote every field, double inner quotes. Leading = + - @ are prefixed so Excel never runs them as formulas. */
fun csv(rows: List<List<String>>) = rows.joinToString("\r\n") { row ->
  row.joinToString(",") { v -> "\"" + (if (v.isNotEmpty() && v[0] in "=+-@") "'$v" else v).replace("\"", "\"\"") + "\"" }
}

val financeHeader = listOf("Paid on", "Approved on", "Rider", "Phone", "Plan", "Amount (INR)", "UTR", "Paid to")

/** Server error codes (raised by the SQL functions) to text people can act on. */
fun friendlyError(code: String?): String = when (code) {
  "NO_PASS" -> "You need a pass or a one-way ticket for this day."
  "ALREADY_BOOKED" -> "You already have a seat on this shuttle."
  "TRIP_FULL" -> "This shuttle is full. Try another route or shift."
  "TRIP_CLOSED" -> "Booking for this pickup has closed."
  "ROUTE_INACTIVE" -> "This route is not running right now."
  "DATE_OUT_OF_WINDOW" -> "That date is outside the booking window."
  "BAD_STOP" -> "Pick a valid pickup point."
  "TOO_LATE" -> "Too late to cancel this ride."
  "NOT_CANCELLABLE" -> "This ride can no longer be cancelled."
  "BAD_UTR" -> "Enter the 12-digit UTR from your UPI app."
  "UTR_USED" -> "This UTR has already been used."
  "NO_PLAN" -> "That plan is no longer available."
  "ALREADY_RESOLVED" -> "Someone already handled this payment."
  "FORBIDDEN" -> "You are not allowed to do that."
  "BAD_STATE" -> "The trip changed. Go back and open it again."
  "NOT_FOUND" -> "Not found. It may have been removed."
  "BAD_OTP" -> "Wrong or expired code. Try again."
  "BAD_INPUT" -> "Some details are missing or invalid."
  "CANT_REMOVE_SELF" -> "You can't remove your own operator access."
  "STOP_HAS_BOOKINGS" -> "A removed stop has bookings. Rename it instead of deleting."
  "NO_STOPS" -> "This route has no stops for that shift yet."
  else -> "Something went wrong. Check your connection and try again."
}

// ── Rows as PostgREST returns them
@Serializable private data class ProfileRow(val id: String, val phone: String, val name: String, val role: String) {
  fun toUser() = User(id, name, phone.takeLast(10), role.toRole())
}

private fun String.toRole() = when (this) { "driver" -> Role.DRIVER; "admin" -> Role.ADMIN; else -> Role.PASSENGER }

@Serializable private data class SettingsRow(
  @SerialName("cancel_cutoff_min") val cancelCutoffMin: Int, @SerialName("booking_window_days") val bookingWindowDays: Int,
  @SerialName("allow_pending_booking") val allowPendingBooking: Boolean, @SerialName("support_name") val supportName: String,
  @SerialName("support_phone") val supportPhone: String,
) {
  fun toRules() = Rules(cancelCutoffMin, bookingWindowDays, allowPendingBooking, supportName, supportPhone)
}

@Serializable private data class RulesWrite(
  @SerialName("cancel_cutoff_min") val cancelCutoffMin: Int, @SerialName("booking_window_days") val bookingWindowDays: Int,
  @SerialName("allow_pending_booking") val allowPendingBooking: Boolean, @SerialName("support_name") val supportName: String,
  @SerialName("support_phone") val supportPhone: String,
)

@Serializable private data class TierRow(@SerialName("from_day") val fromDay: Int, @SerialName("to_day") val toDay: Int, val price: Int)

@Serializable private data class PlanRow(
  val id: String, val title: String, val kind: String, val rides: Int? = null, val price: Int, val validity: String,
  @SerialName("validity_days") val validityDays: Int, val description: String = "", val popular: Boolean = false,
  val active: Boolean = true, val sort: Int = 0, @SerialName("plan_price_tiers") val tiers: List<TierRow> = emptyList(),
) {
  fun toPlan(): Plan {
    val v = when (validity) { "same_day" -> Validity.SameDay; "days" -> Validity.Days; else -> Validity.MonthEnd }
    val until = when (v) {
      Validity.MonthEnd -> YearMonth.from(today()).atEndOfMonth().format(shortDate)
      Validity.SameDay -> "the travel day"
      Validity.Days -> today().plusDays(validityDays.toLong()).format(shortDate)
    }
    val t = tiers.map { PriceTier(it.fromDay, it.toDay, it.price) }.sortedBy { it.fromDay }
    return Plan(id, title, when (kind) { "unlimited" -> PlanKind.Unlimited; "single" -> PlanKind.Single; else -> PlanKind.Rides },
      rides, price, priceOn(price, t, today()), t, v, validityDays, description, popular, active, sort, until)
  }
}

@Serializable private data class UpiRow(
  val id: String, val vpa: String, @SerialName("payee_name") val payeeName: String, val active: Boolean, @SerialName("is_primary") val isPrimary: Boolean,
) {
  fun toUpi() = UpiAccount(id, vpa, payeeName, active, isPrimary)
}

@Serializable private data class UpiWrite(val vpa: String, @SerialName("payee_name") val payeeName: String, val active: Boolean)

@Serializable private data class StopRow(
  val id: String = "", val seq: Int, val shift: String = "morning", val name: String, val lat: Double = 0.0, val lng: Double = 0.0,
  @SerialName("pickup_time") val pickupTime: String = "00:00:00", val archived: Boolean = false,
) {
  fun toStop() = Stop(id, name, lat, lng, clock(pickupTime))
}

@Serializable private data class CodeRow(val code: String)
@Serializable private data class NameRow(val name: String)
@Serializable private data class NamePhoneRow(val name: String, val phone: String)

@Serializable private data class RouteRow(
  val id: String, val name: String, @SerialName("from_label") val fromLabel: String, @SerialName("to_label") val toLabel: String,
  val active: Boolean, @SerialName("vehicle_id") val vehicleId: String? = null, @SerialName("driver_id") val driverId: String? = null,
  val stops: List<StopRow> = emptyList(), val vehicle: CodeRow? = null, val driver: NamePhoneRow? = null,
) {
  fun toRoute() = RouteInfo(
    id, name, fromLabel, toLabel,
    stops.filter { it.shift == "morning" }.sortedBy { it.seq }.map { it.toStop() },
    stops.filter { it.shift == "evening" }.sortedBy { it.seq }.map { it.toStop() },
    active, driverId, driver?.name?.ifBlank { null }, driver?.phone?.takeLast(10), vehicleId, vehicle?.code ?: "—",
  )
}

@Serializable private data class RouteStopsRow(val name: String, val stops: List<StopRow>)

@Serializable private data class PassRow(
  @SerialName("plan_id") val planId: String? = null, @SerialName("rides_left") val ridesLeft: Int? = null,
  @SerialName("rides_total") val ridesTotal: Int? = null, @SerialName("expires_at") val expiresAt: String, val status: String,
)

@Serializable private data class LabelsRow(val name: String, @SerialName("from_label") val fromLabel: String, @SerialName("to_label") val toLabel: String)
@Serializable private data class StopTimeRow(val name: String, @SerialName("pickup_time") val pickupTime: String)
@Serializable private data class StatusRow(val status: String)

@Serializable private data class BookingTrip(
  val id: String, @SerialName("route_id") val routeId: String, @SerialName("service_date") val serviceDate: String, val shift: String,
  val status: String, val route: LabelsRow, val vehicle: CodeRow? = null,
)

@Serializable private data class BookingRow(val id: String, val status: String, val trip: BookingTrip, val stop: StopTimeRow, val pass: StatusRow? = null) {
  fun toRide(): Pair<Ride, Instant> {
    val evening = trip.shift == "evening"
    val from = if (evening) trip.route.toLabel else trip.route.fromLabel
    val to = if (evening) trip.route.fromLabel else trip.route.toLabel
    val pickupAt = LocalDate.parse(trip.serviceDate).atTime(LocalTime.parse(stop.pickupTime)).atZone(IST)
    val st = when (status) {
      "cancelled" -> RideStatus.Cancelled
      "no_show" -> RideStatus.NoShow
      // boarded on a live trip is still trackable; a trip nobody ran in the app (3 h past pickup) is history, not "upcoming"
      else -> if (trip.status == "done" || trip.status == "cancelled" || (trip.status == "scheduled" && pickupAt.toInstant() < Instant.now().minusSeconds(3 * 3600))) RideStatus.Completed else RideStatus.Upcoming
    }
    val ride = Ride(id, pickupAt.format(dateFmt), if (evening) "Evening" else "Morning", trip.route.name, from, to,
      pickupAt.format(timeFmt), stop.name, trip.vehicle?.code ?: "—", st, trip.id, trip.routeId, pass?.status == "pending", pickupAt.toInstant())
    return ride to pickupAt.toInstant()
  }
}

@Serializable private data class TripRow(
  val id: String? = null, @SerialName("route_id") val routeId: String? = null, val shift: String? = null,
  @SerialName("departs_at") val departsAt: String? = null, @SerialName("vehicle_id") val vehicleId: String? = null,
  val status: String? = null, @SerialName("current_stop") val currentStop: Int? = null,
)

@Serializable private data class VehicleRow(val id: String, val code: String, val seats: Int = 16, val active: Boolean = true)
@Serializable private data class VehicleWrite(val code: String, val seats: Int, val active: Boolean)

@Serializable private data class KnownStopRow(val name: String, val lat: Double, val lng: Double)
@Serializable private data class RejectedRow(val amount: Int, val utr: String? = null, val plan: TitleRow? = null)

@Serializable private data class ManifestRow(
  @SerialName("booking_id") val bookingId: String, @SerialName("stop_id") val stopId: String = "", @SerialName("rider_name") val riderName: String, @SerialName("rider_phone") val riderPhone: String = "",
  @SerialName("stop_name") val stopName: String, val status: String, @SerialName("boarded_at") val boardedAt: String? = null,
  @SerialName("pass_status") val passStatus: String = "active", @SerialName("plan_kind") val planKind: String = "rides",
)

private fun String.toBoarding() = when (this) { "boarded" -> BoardingStatus.Boarded; "no_show" -> BoardingStatus.NoShow; else -> BoardingStatus.Waiting }

@Serializable private data class NewPosition(
  @SerialName("trip_id") val tripId: String, val lat: Double, val lng: Double,
  @SerialName("speed_kmh") val speedKmh: Float?, val heading: Float?,
)

@Serializable private data class PositionRow(
  val lat: Double, val lng: Double, @SerialName("speed_kmh") val speedKmh: Float? = null, @SerialName("recorded_at") val recordedAt: String,
) {
  fun toPosition() = Position(lat, lng, speedKmh, Instant.parse(recordedAt).toEpochMilli())
}

@Serializable private data class TitleRow(val title: String)

@Serializable private data class PaymentRow(
  val id: String, val amount: Int, val utr: String? = null, @SerialName("upi_vpa") val upiVpa: String? = null, val status: String,
  @SerialName("created_at") val createdAt: String, val rider: NamePhoneRow, val plan: TitleRow? = null,
) {
  fun toPayment(): PaymentRequest {
    val at = instant(createdAt)
    val label = when (at.toLocalDate()) {
      today() -> at.format(timeFmt)
      today().minusDays(1) -> "Yesterday"
      else -> at.format(shortDate)
    }
    val st = when (status) { "approved" -> PaymentStatus.Approved; "rejected" -> PaymentStatus.Rejected; else -> PaymentStatus.Pending }
    return PaymentRequest(id, rider.name.ifBlank { "Rider ${rider.phone.takeLast(4)}" }, rider.phone.takeLast(10),
      plan?.title.orEmpty(), amount, utr ?: "", label, st, upiVpa)
  }
}

@Serializable private data class FinanceRow(
  val amount: Int, val utr: String? = null, @SerialName("upi_vpa") val upiVpa: String? = null, @SerialName("created_at") val createdAt: String,
  @SerialName("resolved_at") val resolvedAt: String? = null, val rider: NamePhoneRow, val plan: TitleRow? = null,
)

@Serializable private data class StaffRow(val id: String? = null, val phone: String, val name: String, val role: String, val joined: Boolean) {
  fun toStaff() = StaffMember(id, phone.takeLast(10), name, role.toRole(), joined)
}

@Serializable private data class CountRow(val count: Int)
@Serializable private data class FleetTripRow(
  val id: String, val status: String, val shift: String = "morning", @SerialName("route_id") val routeId: String? = null, @SerialName("departs_at") val departsAt: String, @SerialName("vehicle_id") val vehicleId: String? = null,
  val route: NameRow? = null, val bookings: List<CountRow> = emptyList(),
)

@Serializable
private data class ShapeRow(
  val coords: List<List<Double>>,
  @SerialName("way_points") val wayPoints: List<Int>,
  @SerialName("leg_seconds") val legSeconds: List<Int>,
  @SerialName("leg_meters") val legMeters: List<Int>,
)
