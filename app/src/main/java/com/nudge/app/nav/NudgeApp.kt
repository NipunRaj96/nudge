package com.nudge.app.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nudge.app.data.BoardingStatus
import com.nudge.app.data.NudgeViewModel
import com.nudge.app.data.PassStatus
import com.nudge.app.data.PlanKind
import com.nudge.app.data.Position
import com.nudge.app.data.Ride
import com.nudge.app.data.RideIntent
import com.nudge.app.data.RideStatus
import com.nudge.app.data.Role
import com.nudge.app.data.RouteInfo
import com.nudge.app.data.RouteShape
import com.nudge.app.data.withHeading
import com.nudge.app.data.Stop
import com.nudge.app.data.TripLocationService
import com.nudge.app.data.User
import com.nudge.app.data.distanceKm
import com.nudge.app.ui.admin.AdminFleetScreen
import com.nudge.app.ui.admin.AdminPaymentsScreen
import com.nudge.app.ui.admin.AdminRoutesScreen
import com.nudge.app.ui.admin.MapPickerScreen
import com.nudge.app.ui.admin.PlansAdminScreen
import com.nudge.app.ui.admin.RouteEditorScreen
import com.nudge.app.ui.admin.RoutePlaybackScreen
import com.nudge.app.ui.admin.RulesScreen
import com.nudge.app.ui.admin.ShuttlesScreen
import com.nudge.app.ui.admin.StaffScreen
import com.nudge.app.ui.admin.UpiAccountsScreen
import com.nudge.app.ui.booking.BookRideScreen
import com.nudge.app.ui.booking.DateOption
import com.nudge.app.ui.components.AdminNav
import com.nudge.app.ui.components.AppBottomNav
import com.nudge.app.ui.components.ConfirmDialog
import com.nudge.app.ui.components.DriverNav
import com.nudge.app.ui.components.GhostButton
import com.nudge.app.ui.components.LiveMap
import com.nudge.app.ui.components.PassengerNav
import com.nudge.app.ui.driver.DriverActiveTripScreen
import com.nudge.app.ui.driver.DriverPreTripScreen
import com.nudge.app.ui.login.LoginScreen
import com.nudge.app.ui.otp.OtpScreen
import com.nudge.app.ui.passenger.BoardingPassScreen
import com.nudge.app.ui.passenger.PassengerHomeScreen
import com.nudge.app.ui.passenger.RideHistoryScreen
import com.nudge.app.ui.payment.UpiPayScreen
import com.nudge.app.ui.payment.UpiPayState
import com.nudge.app.ui.plans.ChoosePlanScreen
import com.nudge.app.ui.plans.PlanOption
import com.nudge.app.ui.plans.WalkOn
import com.nudge.app.ui.profile.EmptyState
import com.nudge.app.ui.profile.NameScreen
import com.nudge.app.ui.profile.ProfileHighlight
import com.nudge.app.ui.profile.ProfileRow
import com.nudge.app.ui.profile.ProfileScreen
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.tracking.LiveTrackingScreen
import com.nudge.app.ui.tracking.TrackingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nudge.app.ui.admin.AddRouteSheet
import com.nudge.app.ui.admin.CopyRouteSheet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val SLIDE_MS = 320
private val ease = FastOutSlowInEasing
private val IST = ZoneId.of("Asia/Kolkata")
private fun rupees(n: Int) = "₹${"%,d".format(n)}"

private fun rootFor(u: User) = when {
  u.role == Role.PASSENGER && u.name.isBlank() -> Route.Name
  u.role == Role.PASSENGER -> Route.PassengerRoot
  u.role == Role.DRIVER -> Route.DriverRoot
  else -> Route.AdminRoot
}

/** Phone dialer / SMS / WhatsApp. [phone] is 10 digits or 91XXXXXXXXXX. */
private fun Context.contact(phone: String?, how: String) {
  val digits = phone?.filter(Char::isDigit)?.takeLast(10)
  if (digits.isNullOrEmpty() || digits.length < 10) { Toast.makeText(this, "No phone number yet", Toast.LENGTH_SHORT).show(); return }
  val uri = when (how) {
    "sms" -> Uri.parse("smsto:+91$digits")
    "whatsapp" -> Uri.parse("https://wa.me/91$digits")
    else -> Uri.parse("tel:+91$digits")
  }
  try { startActivity(Intent(if (how == "call") Intent.ACTION_DIAL else Intent.ACTION_VIEW, uri)) } catch (_: Exception) {
    Toast.makeText(this, "No app found for that", Toast.LENGTH_SHORT).show()
  }
}

/** App shell: auth flow -> role root. `devStart` is a developer shortcut used by screenshot scripts. */
@Composable
fun NudgeApp(devStart: String? = null, vm: NudgeViewModel = viewModel()) {
  val state by vm.state.collectAsState()
  val nav = remember { Navigator(Route.Login) }
  var phone by rememberSaveable { mutableStateOf("") }
  var phoneError by remember { mutableStateOf(false) }
  var otp by rememberSaveable { mutableStateOf("") }
  // The map picker hands its result back to whoever opened it.
  // Map picker target: which stop of the route draft gets the pin (evening shift?, index).
  var pickerTarget by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
  var discardDraft by remember { mutableStateOf(false) }
  val ctx = LocalContext.current
  fun soon(what: String = "This") = Toast.makeText(ctx, "$what is coming soon", Toast.LENGTH_SHORT).show()

  LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
  // Saved session: skip login once it has been checked.
  LaunchedEffect(state.ready) {
    val u = vm.state.value.user
    if (state.ready && u != null && nav.top == Route.Login) nav.replaceAll(rootFor(u))
    if (state.ready && devStart != null) devJump(devStart, vm, nav)
  }
  // Leaving the route editor with unsaved changes asks first (a non-tech operator loses a lot of typing otherwise).
  BackHandler(enabled = nav.canPop) { if (nav.top is Route.RouteEditor && state.routeDraftDirty) discardDraft = true else nav.pop() }
  if (discardDraft) ConfirmDialog("Discard changes?", "Your edits to this route are not saved yet.", "Discard", { vm.closeDraft(); nav.pop() }, { discardDraft = false }, dismiss = "Keep editing")

  Box(Modifier.fillMaxSize().background(NudgeColors.Background)) {
    if (!state.ready) return@Box
    AnimatedContent(
      targetState = nav.top,
      transitionSpec = {
        val fwd = nav.forward
        val dx = { w: Int -> if (fwd) w / 4 else -w / 4 }
        (slideInHorizontally(tween(SLIDE_MS, easing = ease), dx) + fadeIn(tween(SLIDE_MS, easing = ease)))
          .togetherWith(slideOutHorizontally(tween(SLIDE_MS, easing = ease)) { -dx(it) } + fadeOut(tween(SLIDE_MS / 2)))
          .also { it.targetContentZIndex = if (fwd) 1f else 0f }
      },
      label = "route",
    ) { route ->
      when (route) {
        Route.Login -> LoginScreen(
          phone = phone, onPhoneChange = { phone = it; phoneError = false }, error = phoneError, loading = state.busy,
          onContinue = { if (!state.busy && !vm.startLogin(phone) { otp = ""; nav.push(Route.Otp) }) phoneError = true },
        )
        Route.Otp -> {
          var secs by remember { mutableIntStateOf(30) }
          var round by remember { mutableIntStateOf(0) }
          LaunchedEffect(round) { secs = 30; while (secs > 0) { delay(1000); secs-- } }
          val verify = { vm.verifyOtp(otp, onFail = { otp = "" }) { u -> nav.replaceAll(rootFor(u)) } }
          OtpScreen(
            code = otp, onCodeChange = { otp = it; if (it.length == 6) verify() },
            maskedPhone = state.pendingPhone.let { "+91 ${it.take(2)}XXXXXX${it.takeLast(2)}" }, resendSeconds = secs,
            onBack = { nav.pop() }, onChangeNumber = { nav.pop() }, onVerify = { verify() },
            onResend = { vm.resendOtp { otp = ""; round++ } },
          )
        }
        Route.Name -> {
          var name by rememberSaveable { mutableStateOf("") }
          NameScreen(name, { name = it }, enabled = !state.busy, onSave = { vm.setName(name) { nav.replaceAll(Route.PassengerRoot) } })
        }
        Route.PassengerRoot -> PassengerRoot(vm, nav, ::soon)
        Route.DriverRoot -> DriverRoot(vm, nav)
        Route.AdminRoot -> AdminRoot(vm, nav)
        Route.Plans -> {
          val c = state.catalog
          val passes = c.plans.filter { it.active && it.kind != PlanKind.Single }
          val oneWay = c.oneWay
          LaunchedEffect(Unit) { if (state.selectedPlan == null) (passes.firstOrNull { it.popular } ?: passes.firstOrNull())?.let { vm.selectPlan(it.id) } }
          val sel = state.selectedPlan
          ChoosePlanScreen(
            plans = passes.map { p ->
              PlanOption(
                p.id, p.title, "Valid till ${p.validUntil}", rupees(p.price),
                p.rides?.let { "${rupees(p.price / it)} / ride" } ?: "unlimited rides", p.popular,
                p.tiers.takeIf { it.size > 1 }?.joinToString(" · ") { "${it.fromDay}–${it.toDay}: ${rupees(it.price)}" },
              )
            },
            walkOn = oneWay?.let { WalkOn(it.id, it.title, "${rupees(it.price)} one way", "One ride on the day you travel. Easiest from the Book tab.") },
            selectedId = sel?.id.orEmpty(), onSelect = vm::selectPlan,
            ctaText = if (sel == null) "Choose a plan" else "Continue to pay ${rupees(sel.price)}",
            footnote = if (c.rules.allowPendingBooking) "You can book right after paying. The operator confirms the payment." else "Your pass activates once the operator confirms your payment.",
            onContinue = { if (sel != null) { vm.setRideIntent(null); nav.push(Route.Pay) } }, onBack = { nav.pop() },
          )
        }
        Route.Pay -> PayRoute(vm, nav)
        is Route.BoardingPass -> {
          val ride = vm.ride(route.rideId)
          if (ride == null) LaunchedEffect(Unit) { nav.pop() } else BoardingPassScreen(
            ride = ride, passengerName = state.user?.name.orEmpty(),
            // PLACEHOLDER P-QR-1: payload is the booking id (unguessable uuid), not a signed ticket.
            qrPayload = "nudge:ride:${ride.id}",
            onClose = { nav.pop() }, onTrack = { nav.push(Route.Tracking(ride.id)) }, onCancel = { vm.cancelRide(ride.id) { nav.pop() } },
            cancelNote = if (state.pass.ridesLeft != null) "The ride goes back to your pass. Payments are not refunded." else "Payments are not refunded.",
            // Cancelling is only possible until the cut-off before pickup.
            canCancel = ride.pickupAt?.let { java.time.Instant.now() < it.minusSeconds(state.catalog.rules.cancelCutoffMin * 60L) } ?: true,
            cutoffMin = state.catalog.rules.cancelCutoffMin,
          )
        }
        is Route.Tracking -> {
          val ride = vm.ride(route.rideId)
          if (ride == null) LaunchedEffect(Unit) { nav.pop() } else TrackingRoute(vm, ride, onBack = { nav.pop() })
        }
        is Route.Playback -> {
          val track by produceState(emptyList<Position>(), route.tripId) { value = route.tripId?.let { vm.track(it) }.orEmpty() }
          RoutePlaybackScreen(route.shuttleCode, route.routeName, "Today", track, onBack = { nav.pop() })
        }
        Route.AdminUpi -> UpiAccountsScreen(state.catalog.upi, vm::saveUpi, vm::deleteUpi, vm::setPrimaryUpi, onBack = { nav.pop() })
        Route.AdminPlans -> PlansAdminScreen(state.catalog.plans, vm::savePlan, onBack = { nav.pop() })
        Route.AdminStaff -> StaffScreen(state.staff, vm::loadStaff, vm::upsertStaff, vm::removeStaff, myPhone = state.user?.phone.orEmpty(), onBack = { nav.pop() })
        Route.AdminShuttles -> ShuttlesScreen(state.shuttles, vm::loadShuttles, vm::saveShuttle, onBack = { nav.pop() })
        Route.AdminRules -> RulesScreen(state.catalog.rules, vm::saveRules, onBack = { nav.pop() })
        is Route.RouteEditor -> state.routeDraft?.let { draft ->
          RouteEditorScreen(
            draft, state.shuttles, state.staff, state.importNotes, state.busy, onLoad = { vm.loadShuttles(); vm.loadStaff() },
            onChange = { t -> vm.updateDraft(t) },
            onSave = { vm.saveRoute { vm.closeDraft(); nav.pop() } },
            onDelete = { vm.deleteRoute(draft.id) { vm.closeDraft(); nav.pop() } },
            onPickOnMap = { evening, index -> pickerTarget = evening to index; nav.push(Route.MapPicker) },
            onBack = { if (state.routeDraftDirty) discardDraft = true else { vm.closeDraft(); nav.pop() } },
          )
        } ?: LaunchedEffect(Unit) { nav.pop() }
        Route.MapPicker -> {
          val (evening, index) = pickerTarget ?: (false to -1)
          val stop = state.routeDraft?.stops(evening)?.getOrNull(index)
          if (stop == null) LaunchedEffect(Unit) { nav.pop() } else MapPickerScreen(stop, onPicked = { a, b ->
            vm.updateDraft { r ->
              val list = r.stops(evening).toMutableList().also { it[index] = it[index].copy(lat = a, lng = b) }
              if (evening) r.copy(evening = list) else r.copy(morning = list)
            }
            nav.pop()
          }, onBack = { nav.pop() })
        }
      }
    }
  }
}

/** Manual UPI: pick account, pay in any UPI app, enter UTR. One-way with a chosen ride books the seat right away. */
@Composable
private fun PayRoute(vm: NudgeViewModel, nav: Navigator) {
  val state by vm.state.collectAsState()
  val ctx = LocalContext.current
  val c = state.catalog
  val plan = state.selectedPlan ?: return LaunchedEffect(Unit) { nav.pop() }
  val intent = state.rideIntent.takeIf { plan.kind == PlanKind.Single }
  val accounts = c.payTo
  var vpa by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.vpa.orEmpty()) }
  val account = accounts.firstOrNull { it.vpa == vpa } ?: accounts.firstOrNull()
  var utr by rememberSaveable { mutableStateOf("") } // survives the trip to the UPI app (process may be killed)
  var copied by remember { mutableStateOf(false) }
  val clip = LocalClipboardManager.current
  if (account == null) {
    EmptyState("account_balance", "Payments are not set up", "The operator hasn't added a UPI ID yet. Please contact ${c.rules.supportName}.") {
      GhostButton("Back", { nav.pop() })
    }
    return
  }
  val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
  val line = when {
    intent != null -> "${plan.title} · ${intent.date.format(dayFmt)} · ${intent.stop.name} at ${intent.stop.time}"
    plan.kind == PlanKind.Single -> "${plan.title} · today"
    else -> "${plan.title} · valid till ${plan.validUntil}"
  }
  UpiPayScreen(
    state = UpiPayState(
      rupees(plan.price), line, account.vpa, account.payee, plan.price,
      note = "Nudge ${plan.title} ${state.user?.phone.orEmpty()}",
      footnote = when {
        intent != null && c.rules.allowPendingBooking -> "Your seat is booked as soon as you send the reference. The operator then checks the payment."
        intent != null -> "Send the reference, then book once the operator confirms the payment."
        c.rules.allowPendingBooking -> "You can book right away. The operator confirms the payment."
        else -> "Your pass activates once the operator confirms your payment."
      },
      accounts = accounts.map { it.vpa },
    ),
    utr = utr, onUtrChange = { utr = it }, copied = copied,
    onCopy = { clip.setText(AnnotatedString(account.vpa)); copied = true },
    onOpenApp = {
      val uri = Uri.parse(UpiPayState(rupees(plan.price), line, account.vpa, account.payee, plan.price, "Nudge ${plan.title}", "").upiUri)
      try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { Toast.makeText(ctx, "No UPI app found on this phone", Toast.LENGTH_SHORT).show() }
    },
    canSubmit = utr.length == 12 && !state.busy,
    onPaid = {
      vm.submitPayment(utr, account.vpa) { rideId ->
        nav.popTo(Route.PassengerRoot)
        if (rideId != null) nav.push(Route.BoardingPass(rideId)) else vm.say("Payment sent. The operator will confirm it.")
      }
    },
    onBack = { nav.pop() },
    onAccount = { vpa = it; copied = false },
  )
}

/** Live position of the rider's shuttle -> map, ETA to their pickup, distance, speed, call/message the driver. */
@Composable
private fun TrackingRoute(vm: NudgeViewModel, ride: Ride, onBack: () -> Unit) {
  val state by vm.state.collectAsState()
  val ctx = LocalContext.current
  val route = state.routes.firstOrNull { it.id == ride.routeId }
  val stops = route?.stops(ride.shift == "Evening").orEmpty()
  val pickup = stops.firstOrNull { it.name == ride.pickup }
  val shape by produceState<RouteShape?>(null, ride.routeId, stops) {
    value = vm.routeShape(ride.routeId, ride.shift == "Evening")?.takeIf { it.fits(stops) }
  }
  var help by remember { mutableStateOf(false) }
  val trail by produceState(emptyList<Position>(), ride.tripId) {
    value = vm.track(ride.tripId)
    vm.live(ride.tripId).collect { value = value + it }
  }
  val p = trail.lastOrNull()
  val driver = route?.driverName ?: "Your driver"
  // Re-evaluate every 30 s so "last seen" stays honest when the driver's phone drops off.
  val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
  val staleMin = p?.let { ((now - it.atMillis) / 60_000).toInt() } ?: 0
  val ui = if (p == null || pickup == null) {
    TrackingState("Pickup at ${ride.time}", driver, "Not started yet", ride.shuttleCode, "—", "—")
  } else if (staleMin >= 3) {
    TrackingState("Last seen $staleMin min ago", driver, "Waiting for the driver's location", ride.shuttleCode, "—", "—")
  } else {
    // Road distance and drive time along the route when a road path exists; otherwise straight line at the current speed.
    val byRoad = shape?.eta(p.lat, p.lng, stops.indexOf(pickup))
    val km = byRoad?.first ?: distanceKm(p.lat, p.lng, pickup.lat, pickup.lng)
    val speed = p.speedKmh?.takeIf { it > 3 } ?: 20f
    val mins = (byRoad?.second ?: (km / speed * 60).toInt()).coerceAtLeast(1)
    TrackingState(
      if (km < 0.2) "Arriving now" else "Arriving in $mins min", driver, "Shuttle ${ride.shuttleCode}", ride.shuttleCode,
      "%.1f".format(km), (p.speedKmh ?: 0f).toInt().toString(),
    )
  }
  if (help) ConfirmDialog(
    "Need help?", "Call ${state.catalog.rules.supportName} only if something is wrong with your ride.", "Call office",
    { ctx.contact(state.catalog.rules.supportPhone, "call") }, { help = false }, dismiss = "Close", destructive = false,
  )
  LiveTrackingScreen(
    ui, onBack = onBack, onHelp = { help = true },
    onCall = { ctx.contact(route?.driverPhone, "call") }, onMessage = { ctx.contact(route?.driverPhone, "sms") },
    map = { LiveMap(it, vehicles = listOfNotNull(trail.withHeading()), follow = p != null, highlight = pickup) },
  )
}

/** First load: a spinner, or a retry button when it failed (no network). */
@Composable
private fun LoadingOrRetry(busy: Boolean, onRetry: () -> Unit) {
  if (busy) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
    androidx.compose.material3.CircularProgressIndicator(color = NudgeColors.Primary)
  } else EmptyState("wifi_off", "Couldn't load", "Check your internet connection and try again.") { GhostButton("Try again", onRetry, icon = "refresh") }
}

/** Reload the role's data whenever the app comes back to the foreground. */
@Composable
private fun RefreshOnResume(vm: NudgeViewModel) = LifecycleResumeEffect(Unit) {
  vm.refresh()
  onPauseOrDispose {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(vm: NudgeViewModel, content: @Composable () -> Unit) {
  var pulling by remember { mutableStateOf(false) }
  val state by vm.state.collectAsState()
  LaunchedEffect(state.refreshing) { if (!state.refreshing) pulling = false }
  PullToRefreshBox(isRefreshing = pulling, onRefresh = { pulling = true; vm.refresh() }, modifier = Modifier.fillMaxSize()) { content() }
}

@Composable
private fun PassengerRoot(vm: NudgeViewModel, nav: Navigator, soon: (String) -> Unit) {
  val state by vm.state.collectAsState()
  val ctx = LocalContext.current
  RefreshOnResume(vm)
  val tab = state.tab
  val setTab = vm::setTab
  val draft = state.book
  val activeRoutes = state.routes.filter { it.active && (it.morning.size >= 2 || it.evening.size >= 2) }
  val window = state.catalog.rules.bookingWindowDays.coerceIn(1, 14)
  val today = LocalDate.now(IST)
  val days = remember(window, today) { (0..window).map { today.plusDays(it.toLong()) } }
  val dates = remember(days) { days.map { DateOption(it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), it.dayOfMonth.toString(), it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()) } }
  val dateIdx = draft.dateIdx.coerceIn(0, days.lastIndex)
  val date = days[dateIdx]
  val route = activeRoutes.firstOrNull { it.id == draft.routeId } ?: activeRoutes.firstOrNull()
  // Pickups still ahead: today, stops whose time has passed can't be booked (the shuttle has left).
  val nowTime = java.time.LocalTime.now(IST)
  fun open(r: RouteInfo?, evening: Boolean) = r?.pickups(evening).orEmpty().filter { date != today || com.nudge.app.data.parseClock(it.time).isAfter(nowTime) }
  // Default shift: the one the rider picked, else morning if it still has pickups today, else evening.
  val evening = draft.evening ?: (open(route, false).isEmpty() && open(route, true).isNotEmpty())
  val shift = if (evening) 1 else 0
  val pickups = open(route, evening)
  // Default to the stop the rider used last time on this route/shift (Cityflo-style), else the first open pickup.
  val usual = state.rides.firstOrNull { it.routeId == route?.id && (it.shift == "Evening") == evening }?.pickup
  val pickup = pickups.firstOrNull { it.name == draft.pickupName } ?: pickups.firstOrNull { it.name == usual } ?: pickups.firstOrNull()
  val user = state.user ?: return
  val upcoming = state.rides.firstOrNull { it.status == RideStatus.Upcoming }
  val support = { ctx.contact(state.catalog.rules.supportPhone, "call") }

  Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
      AnimatedContent(
        targetState = tab,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "tab",
      ) { t ->
        when (t) {
          0 -> Refreshable(vm) {
            if (!state.loaded) LoadingOrRetry(state.refreshing) { vm.refresh() } else PassengerHomeScreen(
              userName = user.name, routes = activeRoutes, upcoming = upcoming, notice = state.notice,
              onSearch = { q ->
                val hit = activeRoutes.firstNotNullOfOrNull { r ->
                  listOf(false, true).firstNotNullOfOrNull { ev -> r.pickups(ev).firstOrNull { it.name.contains(q.trim(), true) }?.let { Triple(r, it, ev) } }
                }
                if (hit == null) vm.say("No stop named \"${q.trim()}\". Pick your route below.")
                else { vm.setBook(draft.copy(routeId = hit.first.id, pickupName = hit.second.name, evening = hit.third)); setTab(1) }
              },
              onRouteClick = { vm.setBook(draft.copy(routeId = it.id, pickupName = null)); setTab(1) },
              onBookNow = { setTab(1) }, onLiveTrack = { upcoming?.let { nav.push(Route.Tracking(it.id)) } },
              onOpenTicket = { nav.push(Route.BoardingPass(it.id)) }, onProfile = { setTab(3) },
            )
          }
          1 -> {
            val pass = state.pass
            val oneWay = state.catalog.oneWay
            // A pass only covers days up to its expiry; later days are booked one-way (or with next month's pass).
            val passCovers = pass.canBook && (pass.expiresOn == null || !date.isAfter(pass.expiresOn))
            val (cta, secondary) = when {
              passCovers -> "Confirm booking" to null
              oneWay != null -> "Pay ${rupees(oneWay.price)} & book this ride" to "Get a monthly pass instead"
              else -> "Get a pass to book" to null
            }
            BookRideScreen(
              routes = activeRoutes, selectedRoute = route, onRoute = { vm.setBook(draft.copy(routeId = it.id, pickupName = null)) },
              dates = dates, selectedDate = dateIdx, onDate = { vm.setBook(draft.copy(dateIdx = it, evening = null)) },
              shift = shift, onShift = { vm.setBook(draft.copy(evening = it == 1, pickupName = null)) },
              pickup = pickup, pickups = pickups, onPickup = { vm.setBook(draft.copy(pickupName = it.name)) },
              shiftNote = when {
                route == null -> "No routes are running right now."
                route.stops(evening).size < 2 -> "No ${if (evening) "evening" else "morning"} service on this route."
                pickups.isEmpty() -> "No more ${if (evening) "evening" else "morning"} pickups today. Pick another day."
                else -> null
              },
              passSummary = when (pass.status) {
                PassStatus.Active -> "${pass.plan?.title ?: "Pass"} · " + (pass.ridesLeft?.let { "$it rides left" } ?: "unlimited") + " · till ${pass.validUntil}"
                PassStatus.PendingApproval -> "${pass.plan?.title ?: "Pass"} · payment in review" + if (pass.canBook) " · you can book" else ""
                PassStatus.None -> "No pass yet · pay per ride or get a monthly pass"
              } + if (pass.canBook && !passCovers) " · pass ends ${pass.validUntil}" else "",
              passStatus = pass.status, ctaLabel = cta, ctaEnabled = !state.busy,
              onConfirm = {
                if (route == null || pickup == null) return@BookRideScreen
                when {
                  passCovers -> vm.bookRide(route, date, evening, pickup.id) { nav.push(Route.BoardingPass(it)) }
                  oneWay != null -> { vm.selectPlan(oneWay.id); vm.setRideIntent(RideIntent(route, date, evening, pickup)); nav.push(Route.Pay) }
                  else -> nav.push(Route.Plans)
                }
              },
              secondaryLabel = secondary, onSecondary = { vm.selectPlan(""); nav.push(Route.Plans) },
            )
          }
          2 -> Refreshable(vm) { if (!state.loaded) LoadingOrRetry(state.refreshing) { vm.refresh() } else RideHistoryScreen(state.pass, state.rides, onRenew = { vm.selectPlan(""); nav.push(Route.Plans) }, onOpen = { nav.push(Route.BoardingPass(it.id)) }) }
          else -> ProfileScreen(
            name = user.name, phone = user.phone, roleLabel = "Rider",
            highlight = state.pass.let { p ->
              when (p.status) {
                PassStatus.Active -> ProfileHighlight(p.plan?.title ?: "Pass", (p.ridesLeft?.let { "$it rides left" } ?: "Unlimited rides") + " · till ${p.validUntil}", "Renew",
                  p.ridesTotal?.let { t -> p.ridesLeft?.let { 1f - it.toFloat() / t } }, { vm.selectPlan(""); nav.push(Route.Plans) })
                PassStatus.PendingApproval -> ProfileHighlight("Payment in review", if (p.canBook) "You can book already. The operator is checking your payment." else "Your pass activates once the operator confirms.", null) {}
                PassStatus.None -> ProfileHighlight("No pass yet", "Get a monthly pass, or pay per ride when you book.", "See plans") { vm.selectPlan(""); nav.push(Route.Plans) }
              }
            },
            rows = listOf(
              ProfileRow("receipt_long", "Ride history") { setTab(2) },
              ProfileRow("support_agent", "Call ${state.catalog.rules.supportName.ifBlank { "support" }}") { support() },
              ProfileRow("policy", "Terms & privacy") { soon("Terms") },
            ),
            onLogout = { vm.logout(); nav.replaceAll(Route.Login) },
          )
        }
      }
    }
    AppBottomNav(PassengerNav, tab, setTab)
  }
}

@Composable
private fun DriverRoot(vm: NudgeViewModel, nav: Navigator) {
  val state by vm.state.collectAsState()
  RefreshOnResume(vm)
  val tab = state.tab
  val user = state.user ?: return
  val trip = state.trip
  val ctx = LocalContext.current
  var gpsOk by remember { mutableStateOf(true) }
  // GPS sharing follows the trip state (also resumes after the app is reopened mid-trip).
  LaunchedEffect(trip?.id, trip?.started) {
    gpsOk = TripLocationService.hasPermission(ctx) && ctx.getSystemService(android.location.LocationManager::class.java).isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    if (trip?.started == true && TripLocationService.hasPermission(ctx)) TripLocationService.start(ctx, trip.id)
    else if (trip?.started != true) TripLocationService.stop(ctx)
  }
  // While a trip runs, pick up riders who booked or cancelled meanwhile.
  LaunchedEffect(trip?.id, trip?.started) {
    while (trip?.started == true) { delay(30_000); vm.refreshTrip() }
  }
  val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    gpsOk = TripLocationService.hasPermission(ctx)
    if (trip?.started != true) vm.startTrip() else if (gpsOk) TripLocationService.start(ctx, trip.id)
  }
  var askBattery by remember { mutableStateOf(false) }
  if (askBattery) BatteryPrompt { askBattery = false; askLocation.launch(TripLocationService.permissions) }
  var blockLogout by remember { mutableStateOf(false) }
  if (blockLogout) ConfirmDialog("Trip still running", "End the trip first, then log out.", "OK", {}, { blockLogout = false }, dismiss = "Close", destructive = false)
  Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
      AnimatedContent(targetState = tab to trip?.started, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "driver") { (t, started) ->
        when {
          t == 1 -> ProfileScreen(
            user.name, user.phone, "Driver", null,
            listOf(ProfileRow("support_agent", "Call ${state.catalog.rules.supportName.ifBlank { "office" }}") { ctx.contact(state.catalog.rules.supportPhone, "call") }),
            onLogout = { if (trip?.started == true) blockLogout = true else { TripLocationService.stop(ctx); vm.logout(); nav.replaceAll(Route.Login) } },
          )
          !state.loaded -> Refreshable(vm) { LoadingOrRetry(state.refreshing) { vm.refresh() } }
          trip == null -> Refreshable(vm) {
            EmptyState("event_busy", "No more trips today", "Your next trip shows up here once the operator assigns you to a route.") {
              GhostButton("Refresh", { vm.refresh() }, icon = "refresh")
            }
          }
          started != true -> Refreshable(vm) {
            DriverPreTripScreen(trip, onStartTrip = {
              if (state.busy) return@DriverPreTripScreen
              // Battery limits kill GPS on many phones: ask (again) on every start until it's allowed.
              if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) askBattery = true
              else askLocation.launch(TripLocationService.permissions)
            }, onCall = { ctx.contact(it.phone, "call") })
          }
          else -> DriverActiveTripScreen(
            trip,
            onBoard = { vm.setBoarding(it.id, BoardingStatus.Boarded) },
            onNoShow = { vm.setBoarding(it.id, BoardingStatus.NoShow) },
            onMoveStop = vm::moveStop, onEndTrip = vm::endTrip,
            onCall = { ctx.contact(it.phone, "call") },
            onUndo = { vm.setBoarding(it.id, BoardingStatus.Waiting) },
            busy = state.busy,
            locationWarning = if (gpsOk) null else "Location is off. Riders can't see the shuttle.",
            onFixLocation = {
              if (!TripLocationService.hasPermission(ctx)) askLocation.launch(TripLocationService.permissions)
              else ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
          )
        }
      }
    }
    AppBottomNav(DriverNav, tab, vm::setTab)
  }
}

/** Ask to let GPS run with the screen off (Xiaomi/Oppo/Vivo kill it otherwise). Shown on Start trip while still limited. */
@Composable
private fun BatteryPrompt(onDone: () -> Unit) {
  val ctx = LocalContext.current
  ConfirmDialog(
    "Keep trips running", "So riders can see the shuttle with your screen off, allow Nudge to run without battery limits. Find Nudge in the list and choose \"Don't optimise\" / \"No restrictions\". Then come back and tap Start trip.",
    "Open settings", { try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { onDone() } },
    onDone, dismiss = "Start anyway", destructive = false,
  )
}

@Composable
private fun AdminRoot(vm: NudgeViewModel, nav: Navigator) {
  val state by vm.state.collectAsState()
  RefreshOnResume(vm)
  val tab = state.tab
  val user = state.user ?: return
  val ctx = LocalContext.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    if (uri != null) vm.exportFinance { text -> ctx.contentResolver.openOutputStream(uri)?.use { it.write(("\uFEFF" + text).toByteArray()) } }
  }
  var addRoute by remember { mutableStateOf(false) }
  var copyRoute by remember { mutableStateOf(false) }
  var reading by remember { mutableStateOf(false) }
  val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    } ?: "New route"
    reading = true
    scope.launch {
      val text = runCatching { com.nudge.app.data.pdfText(ctx, uri) }.getOrElse { reading = false; vm.say("Couldn't read that PDF."); return@launch }
      vm.importTimetable(text, name) { nav.push(Route.RouteEditor("")) }
      reading = false
    }
  }
  if (addRoute) AddRouteSheet(
    onDismiss = { addRoute = false },
    onImport = { addRoute = false; pickPdf.launch(arrayOf("application/pdf")) },
    onCopy = { addRoute = false; copyRoute = true },
    onManual = {
      addRoute = false
      vm.editRoute(RouteInfo("", "", "", "", emptyList(), emptyList(), true, null, null, null, null, "—"))
      nav.push(Route.RouteEditor(""))
    },
  )
  if (copyRoute) CopyRouteSheet(state.routes, state.busy, onDismiss = { copyRoute = false }) { from, name, minutes ->
    vm.copyRoute(from.id, name, minutes) { id ->
      copyRoute = false
      vm.state.value.routes.firstOrNull { it.id == id }?.let { vm.editRoute(it); nav.push(Route.RouteEditor(id)) }
    }
  }
  if (reading) com.nudge.app.ui.components.ProgressDialog("Reading the timetable and finding each stop on the map. This can take a minute for a new area.")
  Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
      AnimatedContent(targetState = tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "admin") { t ->
        when (t) {
          0 -> Refreshable(vm) {
            AdminFleetScreen(state.fleet.first, state.fleet.second, onVehicle = { v ->
              nav.push(Route.Playback(v.code, v.description, v.tripId))
            })
          }
          1 -> AdminRoutesScreen(
            state.routes, onToggle = { r, v -> vm.toggleRoute(r.id, v) },
            onAdd = { addRoute = true }, onEdit = { vm.editRoute(it); nav.push(Route.RouteEditor(it.id)) },
          )
          2 -> Refreshable(vm) {
            AdminPaymentsScreen(
              state.payments, onResolve = { p, ok -> vm.resolvePayment(p.id, ok) }, onExport = { saveCsv.launch("nudge-payments-${LocalDate.now(IST)}.csv") },
              plans = state.catalog.plans.filter { it.active }, busy = state.busy,
              onGrant = { phone, plan, amount, day, done -> vm.grantPass(phone, plan.id, amount, day, done) },
            )
          }
          else -> ProfileScreen(
            user.name, user.phone, "Operator", null,
            listOf(
              ProfileRow("account_balance", "UPI accounts") { nav.push(Route.AdminUpi) },
              ProfileRow("sell", "Plans & prices") { nav.push(Route.AdminPlans) },
              ProfileRow("group", "Drivers & staff") { nav.push(Route.AdminStaff) },
              ProfileRow("airport_shuttle", "Shuttles") { nav.push(Route.AdminShuttles) },
              ProfileRow("tune", "Rules & support contact") { nav.push(Route.AdminRules) },
            ),
            onLogout = { vm.logout(); nav.replaceAll(Route.Login) },
          )
        }
      }
    }
    AppBottomNav(AdminNav, tab, vm::setTab)
  }
}

/**
 * Developer shortcut: `adb shell am start ... --es screen <name>` signs in a demo account (OTP 123456 against the local
 * Supabase test numbers) and opens the screen.
 */
private suspend fun devJump(name: String, vm: NudgeViewModel, nav: Navigator) {
  suspend fun login(phone: String): Boolean {
    runCatching { com.nudge.app.data.Backends.instance.sendOtp(phone) } // a brand-new number needs the OTP request first
    return vm.signIn("123456", phone)?.also { nav.replaceAll(rootFor(it)) } != null
  }
  suspend fun waitFor(check: () -> Boolean) { repeat(40) { if (!check()) delay(250) } }
  when (name) {
    "otp" -> { vm.startLogin("9876543210"); nav.push(Route.Otp) }
    "home", "book", "rides", "profile" -> login("9876543210")
    "newrider" -> login("9000000099")
    "plans" -> if (login("9000000099")) { waitFor { vm.state.value.catalog.plans.isNotEmpty() }; nav.push(Route.Plans) }
    "pay" -> if (login("9000000099")) {
      waitFor { vm.state.value.catalog.plans.isNotEmpty() }
      vm.state.value.catalog.plans.firstOrNull { it.popular }?.let { vm.selectPlan(it.id) }
      nav.push(Route.Pay)
    }
    "pass", "track" -> if (login("9876543210")) {
      waitFor { vm.state.value.rides.isNotEmpty() }
      vm.state.value.rides.firstOrNull { it.status == RideStatus.Upcoming }?.let { nav.push(if (name == "pass") Route.BoardingPass(it.id) else Route.Tracking(it.id)) }
    }
    "driver", "active" -> if (login("7000000001") && name == "active") { waitFor { vm.state.value.trip != null }; vm.startTrip() }
    "admin", "payments", "routes" -> login("7000000002")
    "upi", "adminplans", "staff", "shuttles", "rules", "routeedit" -> if (login("7000000002")) {
      waitFor { vm.state.value.catalog.routes.isNotEmpty() }
      nav.push(when (name) {
        "upi" -> Route.AdminUpi; "adminplans" -> Route.AdminPlans; "staff" -> Route.AdminStaff
        "shuttles" -> Route.AdminShuttles; "rules" -> Route.AdminRules
        else -> vm.state.value.catalog.routes.first().let { vm.editRoute(it); Route.RouteEditor(it.id) }
      })
    }
    "playback" -> if (login("7000000002")) nav.push(Route.Playback("SH-1", "Route 1", null))
  }
}
