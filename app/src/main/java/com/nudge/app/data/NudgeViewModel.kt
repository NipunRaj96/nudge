package com.nudge.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A ride the rider wants but has no pass for: paying one-way books it right after the UTR is sent. */
data class RideIntent(val route: RouteInfo, val date: LocalDate, val evening: Boolean, val stop: Stop)

/** Rider's Book form. Kept in the ViewModel so it survives visiting Pay / Boarding pass and coming back. */
data class BookDraft(val routeId: String? = null, val pickupName: String? = null, val dateIdx: Int = 0, val evening: Boolean? = null)

data class NudgeState(
  /** False until the saved session (if any) has been checked at startup. */
  val ready: Boolean = false,
  /** User actions in flight; buttons that start one are disabled meanwhile. Background refreshes don't count. */
  val inFlight: Int = 0,
  /** A background reload is running (pull-to-refresh spinner, first-load spinner). */
  val refreshing: Boolean = false,
  /** True once the signed-in role's data has loaded at least once. */
  val loaded: Boolean = false,
  val user: User? = null,
  val pendingPhone: String = "",
  val catalog: Catalog = Catalog(emptyList(), emptyList(), emptyList(), Rules(15, 7, true, "", "")),
  val pass: Pass = Pass(null, null, null, "", PassStatus.None, false),
  val notice: String? = null,
  val rides: List<Ride> = emptyList(),
  val payments: List<PaymentRequest> = emptyList(),
  val trip: DriverTrip? = null,
  val fleet: Pair<FleetStats, List<Vehicle>> = FleetStats(0, 0, 0, 0) to emptyList(),
  val staff: List<StaffMember> = emptyList(),
  val shuttles: List<Shuttle> = emptyList(),
  val selectedPlanId: String? = null,
  val rideIntent: RideIntent? = null,
  /** Bottom-nav tab of the signed-in role (survives opening a detail screen). */
  val tab: Int = 0,
  val book: BookDraft = BookDraft(),
  /** Route being edited / imported by the operator (survives the map picker). */
  val routeDraft: RouteInfo? = null,
  val routeDraftDirty: Boolean = false,
  /** Notes from a PDF import ("Changed 06:35 AM to 06:35 PM", "12 stops need a pin"). */
  val importNotes: List<String> = emptyList(),
) {
  val busy get() = inFlight > 0
  val routes get() = catalog.routes
  val selectedPlan get() = catalog.plans.firstOrNull { it.id == selectedPlanId }
}

/** Screen state + actions for the whole app. All server work goes through [Backend]; errors arrive on [messages]. */
class NudgeViewModel : ViewModel() {
  private val backend get() = Backends.instance
  private val _state = MutableStateFlow(NudgeState())
  val state: StateFlow<NudgeState> = _state.asStateFlow()
  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  /** One-off texts to show as a toast. */
  val messages: SharedFlow<String> = _messages

  init {
    viewModelScope.launch {
      val user = try { backend.restoreSession() } catch (e: BackendError) { say("You're offline. Showing what we can."); null }
      _state.update { it.copy(user = user, ready = true) }
      if (user != null) refresh()
    }
  }

  fun say(text: String) { _messages.tryEmit(text) }

  /** Runs [block] counted as in flight; a [BackendError] becomes a toast. Returns null on failure. */
  private suspend fun <T> attempt(quiet: Boolean = false, background: Boolean = false, block: suspend () -> T): T? {
    _state.update { if (background) it.copy(refreshing = true) else it.copy(inFlight = it.inFlight + 1) }
    return try {
      block()
    } catch (e: BackendError) {
      if (!quiet) say(e.message.orEmpty()); null
    } finally {
      _state.update { if (background) it.copy(refreshing = false) else it.copy(inFlight = (it.inFlight - 1).coerceAtLeast(0)) }
    }
  }

  private fun act(block: suspend () -> Unit) { viewModelScope.launch { attempt(block = block) } }

  /** Like [act] but ignores taps while another request runs (double taps on Pay, Book, Start trip...). */
  private fun once(block: suspend () -> Unit) { if (!_state.value.busy) act(block) }

  fun setTab(t: Int) = _state.update { it.copy(tab = t) }
  fun setBook(d: BookDraft) = _state.update { it.copy(book = d) }

  // ── Auth
  fun validPhone(phone: String) = phone.length == 10 && phone.all(Char::isDigit) && phone[0] in "6789"

  /** Sends the OTP. Returns false (and sends nothing) when the number is invalid. */
  fun startLogin(phone: String, onSent: () -> Unit = {}): Boolean {
    if (!validPhone(phone)) return false
    _state.update { it.copy(pendingPhone = phone) }
    once { backend.sendOtp(phone); onSent() }
    return true
  }

  fun resendOtp(onSent: () -> Unit) = once { backend.sendOtp(_state.value.pendingPhone); say("Code sent again"); onSent() }

  fun verifyOtp(code: String, onFail: () -> Unit, onUser: (User) -> Unit) {
    if (code.length != 6) return say("Enter the 6-digit code")
    if (_state.value.busy) return
    viewModelScope.launch { signIn(code)?.let(onUser) ?: onFail() }
  }

  /** Also used by the debug screen jumper. */
  suspend fun signIn(code: String, phone: String = _state.value.pendingPhone): User? {
    _state.update { it.copy(pendingPhone = phone) }
    val user = attempt { backend.verifyOtp(phone, code) } ?: return null
    _state.update { it.copy(user = user, tab = 0) }
    refresh()
    return user
  }

  fun setName(name: String, onDone: () -> Unit) = once {
    val user = backend.setName(name.trim())
    _state.update { it.copy(user = user) }
    onDone()
  }

  fun logout() = viewModelScope.launch {
    backend.signOut()
    _state.value = NudgeState(ready = true)
  }

  /** Reloads everything the signed-in role looks at. */
  fun refresh() = viewModelScope.launch {
    val user = _state.value.user ?: return@launch
    attempt(background = true) {
      val catalog = backend.catalog()
      when (user.role) {
        Role.PASSENGER -> _state.update {
          it.copy(catalog = catalog, pass = backend.pass(catalog.plans), rides = backend.rides(), notice = runCatching { backend.rejectedNotice() }.getOrNull())
        }
        Role.DRIVER -> _state.update { it.copy(catalog = catalog, trip = backend.driverTrip()) }
        Role.ADMIN -> {
          _state.update { it.copy(catalog = catalog, payments = backend.payments(), fleet = backend.fleet()) }
          syncShapes()
        }
      }
      _state.update { it.copy(loaded = true) }
    }
  }

  // ── Rider
  fun selectPlan(id: String) = _state.update { it.copy(selectedPlanId = id) }

  /** Remember the ride to book after a one-way payment (null = buying a pass from Plans). */
  fun setRideIntent(intent: RideIntent?) = _state.update { it.copy(rideIntent = intent) }

  /**
   * Rider tapped "I have paid". The pass exists at once (pending review). For a one-way payment with a [RideIntent]
   * the seat is booked straight away; [onDone] gets the new ride id (null if no seat was booked).
   */
  fun submitPayment(utr: String, vpa: String?, onDone: (String?) -> Unit) = once {
    val s = _state.value
    val plan = s.selectedPlan ?: return@once say("Pick a plan first")
    val intent = s.rideIntent.takeIf { plan.kind == PlanKind.Single }
    backend.submitPayment(plan.id, utr, vpa, intent?.date)
    // The payment is saved even if the seat can't be booked any more (full / closed): say so instead of losing it.
    val rideId = intent?.let {
      try { backend.bookRide(it.route.id, it.date, it.evening, it.stop.id) } catch (e: BackendError) {
        say("Payment saved, but the seat couldn't be booked: ${e.message} Your one-way ticket is kept for that day."); null
      }
    }
    _state.update { it.copy(pass = backend.pass(it.catalog.plans), rides = backend.rides(), rideIntent = null) }
    onDone(rideId)
  }

  fun bookRide(route: RouteInfo, date: LocalDate, evening: Boolean, stopId: String, onBooked: (String) -> Unit) = once {
    val id = backend.bookRide(route.id, date, evening, stopId)
    _state.update { it.copy(rides = backend.rides(), pass = backend.pass(it.catalog.plans)) }
    onBooked(id)
  }

  fun cancelRide(id: String, onDone: () -> Unit = {}) = once {
    val packRide = _state.value.pass.ridesLeft != null
    backend.cancelRide(id)
    _state.update { it.copy(rides = backend.rides(), pass = backend.pass(it.catalog.plans)) }
    say(if (packRide) "Ride cancelled. The ride is back on your pass." else "Ride cancelled.")
    onDone()
  }

  fun ride(id: String) = _state.value.rides.firstOrNull { it.id == id }

  // ── Driver
  /** Reloads the trip quietly (manifest polling while a trip runs). */
  fun refreshTrip() = viewModelScope.launch {
    attempt(quiet = true, background = true) { _state.update { it.copy(trip = backend.driverTrip()) } }
  }

  private fun tripAction(block: suspend (DriverTrip) -> Unit) = once {
    val trip = _state.value.trip ?: return@once
    try { block(trip) } finally { _state.update { it.copy(trip = runCatching { backend.driverTrip() }.getOrDefault(it.trip)) } }
  }

  fun startTrip() = tripAction { backend.startTrip(it.id) }

  /** Updates the card immediately; if the server call fails the card goes back and the driver is told. */
  fun setBoarding(id: String, status: BoardingStatus) {
    val before = _state.value.trip?.passengers?.firstOrNull { it.id == id } ?: return
    _state.update { s -> s.copy(trip = s.trip?.copy(passengers = s.trip.passengers.map { if (it.id == id) it.copy(status = status) else it })) }
    viewModelScope.launch {
      val ok = attempt { backend.setBoarding(id, status) } != null
      if (!ok) _state.update { s -> s.copy(trip = s.trip?.copy(passengers = s.trip.passengers.map { if (it.id == id) before else it })) }
      refreshTrip()
    }
  }

  fun moveStop(step: Int) = tripAction { backend.moveStop(it.id, it.currentStopIndex, step) }
  fun endTrip() = tripAction { backend.endTrip(it.id) }

  // ── Operator
  fun resolvePayment(id: String, approve: Boolean) = once {
    try { backend.resolvePayment(id, approve) } finally { _state.update { it.copy(payments = backend.payments()) } }
  }

  /** Cash / offline payment (also handy for testing): the rider gets an active pass at once. */
  fun grantPass(phone: String, planId: String, amount: Int?, validOn: LocalDate?, onDone: () -> Unit) = once {
    backend.grantPass(phone, planId, amount, validOn)
    _state.update { it.copy(payments = backend.payments()) }
    say("Pass added")
    onDone()
  }

  fun toggleRoute(id: String, active: Boolean) {
    _state.update { s -> s.copy(catalog = s.catalog.copy(routes = s.catalog.routes.map { if (it.id == id) it.copy(active = active) else it })) }
    act {
      try { backend.setRouteActive(id, active) } finally { _state.update { it.copy(catalog = backend.catalog()) } }
    }
  }

  /** Writes the finance CSV to a file the operator picked (Storage Access Framework). */
  fun exportFinance(write: (String) -> Unit) = once {
    val text = backend.financeCsv()
    withContext(Dispatchers.IO) { write(text) }
    say("Exported. Open the file in Excel or Google Sheets.")
  }

  /** Runs an operator edit, reloads the catalogue, then calls [onDone]. */
  private fun edit(onDone: () -> Unit = {}, block: suspend () -> Unit) = once {
    block()
    _state.update { it.copy(catalog = backend.catalog()) }
    onDone()
  }

  fun savePlan(p: Plan, onDone: () -> Unit) = edit(onDone) { backend.savePlan(p); say("Plan saved") }
  fun saveUpi(a: UpiAccount, onDone: () -> Unit) = edit(onDone) { backend.saveUpi(a); say("Saved") }
  fun deleteUpi(id: String) = edit { backend.deleteUpi(id) }
  fun setPrimaryUpi(id: String) = edit { backend.setPrimaryUpi(id) }
  fun saveRules(r: Rules, onDone: () -> Unit) = edit(onDone) { backend.saveRules(r); say("Saved") }

  fun loadStaff() = act { _state.update { it.copy(staff = backend.staff()) } }
  fun upsertStaff(phone: String, name: String, role: Role, onDone: () -> Unit) = once {
    backend.upsertStaff(phone, name, role)
    _state.update { it.copy(staff = backend.staff()) }
    say("Saved. They can log in with this number now.")
    onDone()
  }
  fun removeStaff(phone: String) = once {
    backend.removeStaff(phone)
    _state.update { it.copy(staff = backend.staff(), catalog = backend.catalog()) }
  }

  fun loadShuttles() = act { _state.update { it.copy(shuttles = backend.shuttles()) } }
  fun saveShuttle(s: Shuttle, onDone: () -> Unit) = once {
    backend.saveShuttle(s)
    _state.update { it.copy(shuttles = backend.shuttles(), catalog = backend.catalog()) }
    onDone()
  }

  // ── Route editor (draft lives here so the map picker and back navigation never lose it)
  // ── Road paths (OpenRouteService, computed server-side). Cached per route+shift for the session.
  private val shapes = mutableMapOf<String, RouteShape?>()

  suspend fun routeShape(routeId: String, evening: Boolean): RouteShape? = "$routeId/$evening".let { k ->
    if (k in shapes) shapes[k] else runCatching { backend.routeShape(routeId, evening) }.getOrNull().also { shapes[k] = it }
  }

  /** Recompute road paths after routes change; failures only mean straight lines until the next try. */
  private fun syncShapes() = viewModelScope.launch {
    runCatching { backend.syncRouteShapes() }
    shapes.clear()
  }

  fun editRoute(r: RouteInfo, notes: List<String> = emptyList()) = _state.update { it.copy(routeDraft = r, routeDraftDirty = r.id.isBlank(), importNotes = notes) }
  fun updateDraft(transform: (RouteInfo) -> RouteInfo) = _state.update { s -> s.routeDraft?.let { s.copy(routeDraft = transform(it), routeDraftDirty = true) } ?: s }
  fun closeDraft() = _state.update { it.copy(routeDraft = null, routeDraftDirty = false, importNotes = emptyList()) }

  fun saveRoute(onDone: () -> Unit) = once {
    val r = _state.value.routeDraft ?: return@once
    val id = backend.saveRoute(r)
    _state.update { it.copy(catalog = backend.catalog(), routeDraft = r.copy(id = id), routeDraftDirty = false, importNotes = emptyList()) }
    say("Route saved")
    syncShapes()
    onDone()
  }

  fun deleteRoute(id: String, onDone: () -> Unit) = once {
    val how = backend.deleteRoute(id)
    _state.update { it.copy(catalog = backend.catalog()) }
    say(if (how == "deleted") "Route deleted" else "Route removed. Past rides on it are kept in history.")
    onDone()
  }

  fun copyRoute(id: String, name: String, shiftMinutes: Int, onDone: (String) -> Unit) = once {
    val newId = backend.copyRoute(id, name, shiftMinutes)
    _state.update { it.copy(catalog = backend.catalog()) }
    say("Route copied")
    syncShapes()
    onDone(newId)
  }

  /**
   * Turns timetable text (from a PDF) into a new route draft. Stops already used on other routes keep their pins;
   * the rest are looked up on OpenStreetMap, and anything not found is flagged for the operator to pin.
   */
  fun importTimetable(text: String, fileName: String, onReady: () -> Unit) = once {
    val parsed = TimetableParser.parse(text)
    if (parsed.morning.size < 2 && parsed.evening.size < 2) throw BackendError("Couldn't find stop names with times in this PDF. Try another file or add the route by hand.")
    val known = backend.knownStops()
    var missing = 0
    val geo = mutableMapOf<String, Pair<Double, Double>?>()
    suspend fun stop(p: ParsedStop): Stop {
      val key = TimetableParser.normalize(p.name)
      val pin = known[key] ?: geo.getOrPut(key) { Geocoder.find(p.name) }
      if (pin == null) missing++
      return Stop("", p.name, pin?.first ?: 0.0, pin?.second ?: 0.0, formatClock(p.time))
    }
    val morning = parsed.morning.map { stop(it) }
    val evening = parsed.evening.map { stop(it) }
    val name = fileName.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
      .lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifBlank { "New route" }
    val from = morning.firstOrNull()?.name ?: evening.lastOrNull()?.name ?: ""
    val to = morning.lastOrNull()?.name ?: evening.firstOrNull()?.name ?: ""
    val notes = parsed.warnings + listOfNotNull(if (missing > 0) "$missing stops need a map pin (marked in red). Tap each to set it." else null)
    _state.update {
      it.copy(routeDraft = RouteInfo("", name, from, to, morning, evening, true, null, null, null, null, "—"), routeDraftDirty = true, importNotes = notes)
    }
    onReady()
  }

  // ── Tracking (screens collect these directly)
  suspend fun track(tripId: String) = runCatching { backend.track(tripId) }.getOrDefault(emptyList())
  fun live(tripId: String) = backend.live(tripId)
}
