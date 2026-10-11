package com.nudge.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.nudge.app.data.Plan
import com.nudge.app.data.PlanKind
import com.nudge.app.data.Position
import com.nudge.app.data.PriceTier
import com.nudge.app.data.Role
import com.nudge.app.data.RouteInfo
import com.nudge.app.data.Rules
import com.nudge.app.data.Shuttle
import com.nudge.app.data.StaffMember
import com.nudge.app.data.Stop
import com.nudge.app.data.UpiAccount
import com.nudge.app.data.Validity
import com.nudge.app.data.formatClock
import com.nudge.app.data.parseClock
import com.nudge.app.data.prettyPhone
import com.nudge.app.ui.components.ConfirmDialog
import com.nudge.app.ui.components.EditorSheet
import com.nudge.app.ui.components.FormField
import com.nudge.app.ui.components.GhostButton
import com.nudge.app.ui.components.ListCard
import com.nudge.app.ui.components.LiveMap
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.SectionLabel
import com.nudge.app.ui.components.Segmented
import com.nudge.app.ui.components.StatusChip
import com.nudge.app.ui.components.Switch
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.ToggleRow
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius24 = RoundedCornerShape(24.dp)
private fun rupees(n: Int) = "₹${"%,d".format(n)}"
private fun String.digits() = filter(Char::isDigit)

/** Screen frame for operator lists: header with back, scrolling list, optional floating add button. */
@Composable
private fun ManageFrame(title: String, onBack: () -> Unit, onAdd: (() -> Unit)? = null, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  Box(Modifier.fillMaxSize().background(NudgeColors.Background)) {
    Column(Modifier.fillMaxSize()) {
      ScreenHeader(title, onBack = onBack)
      LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
      )
    }
    if (onAdd != null) Box(
      Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(end = 24.dp, bottom = 24.dp)
        .shadow(16.dp, CircleShape).size(60.dp).background(NudgeColors.PrimaryContainer, CircleShape).tappable(pressedScale = 0.92f, onClick = onAdd),
      contentAlignment = Alignment.Center,
    ) { Symbol("add", size = 28.dp, tint = NudgeColors.OnPrimaryContainer) }
  }
}

@Composable
private fun Hint(text: String) = Text(text, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))

// ── UPI accounts
@Composable
fun UpiAccountsScreen(accounts: List<UpiAccount>, onSave: (UpiAccount, () -> Unit) -> Unit, onDelete: (String) -> Unit, onPrimary: (String) -> Unit, onBack: () -> Unit) {
  var editing by remember { mutableStateOf<UpiAccount?>(null) }
  var deleting by remember { mutableStateOf<UpiAccount?>(null) }
  ManageFrame("UPI accounts", onBack, onAdd = { editing = UpiAccount("", "", "", true, false) }) {
    item { Hint("Riders pay to the primary account. Other active accounts appear as a choice on the payment screen.") }
    items(accounts, key = { it.id }) { a ->
      ListCard(a.vpa, a.payee + if (!a.active) " · hidden" else "", leadingIcon = "account_balance", onClick = { editing = a }) {
        if (a.primary) StatusChip("PRIMARY", NudgeColors.PrimaryContainer.copy(alpha = 0.2f), NudgeColors.Primary)
        else Text("Make primary", style = twText(13f, FontWeight.SemiBold, 18.sp), color = NudgeColors.Primary, modifier = Modifier.clip(CircleShape).tappable { onPrimary(a.id) }.padding(8.dp))
      }
    }
  }
  editing?.let { a ->
    var vpa by remember(a) { mutableStateOf(a.vpa) }
    var payee by remember(a) { mutableStateOf(a.payee) }
    var active by remember(a) { mutableStateOf(a.active) }
    val valid = Regex("^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$").matches(vpa.trim()) && payee.isNotBlank()
    EditorSheet(
      if (a.id.isBlank()) "Add UPI account" else "Edit UPI account", { editing = null },
      { onSave(a.copy(vpa = vpa.trim(), payee = payee.trim(), active = active)) { editing = null } }, valid,
      extraAction = if (a.id.isNotBlank() && !a.primary) ({ GhostButton("Delete", { deleting = a; editing = null }, Modifier.fillMaxWidth(), color = NudgeColors.Error) }) else null,
    ) {
      FormField("UPI ID", vpa, { vpa = it.trim() }, placeholder = "name@bank", keyboard = KeyboardType.Email)
      FormField("Payee name", payee, { payee = it }, placeholder = "As shown in the UPI app")
      if (a.primary) Hint("This is the main account riders pay to. Make another account primary before hiding this one.")
      else ToggleRow("Show to riders", null, active) { active = it }
    }
  }
  deleting?.let { a -> ConfirmDialog("Delete ${a.vpa}?", "Riders will no longer see this UPI ID.", "Delete", { onDelete(a.id) }, { deleting = null }) }
}

// ── Plans & prices
@Composable
fun PlansAdminScreen(plans: List<Plan>, onSave: (Plan, () -> Unit) -> Unit, onBack: () -> Unit) {
  var editing by remember { mutableStateOf<Plan?>(null) }
  ManageFrame("Plans & prices", onBack, onAdd = {
    editing = Plan("plan" + System.currentTimeMillis() % 100000, "", PlanKind.Rides, 20, 0, 0, emptyList(), Validity.MonthEnd, 30, "", false, true, plans.size + 1, "")
  }) {
    item { Hint("Prices apply to new purchases. A price per date range (e.g. 1–10, 11–20, 21–31) overrides the base price.") }
    items(plans, key = { it.id }) { p ->
      val price = if (p.tiers.isEmpty()) rupees(p.basePrice) else p.tiers.joinToString(" · ") { "${it.fromDay}–${it.toDay} ${rupees(it.price)}" }
      ListCard(p.title, "$price · ${validityLabel(p)}${if (!p.active) " · hidden" else ""}", leadingIcon = kindIcon(p.kind), onClick = { editing = p })
    }
  }
  editing?.let { p -> PlanEditor(p, { editing = null }) { onSave(it) { editing = null } } }
}

private fun kindIcon(k: PlanKind) = when (k) { PlanKind.Unlimited -> "all_inclusive"; PlanKind.Rides -> "confirmation_number"; PlanKind.Single -> "directions_bus" }
private fun validityLabel(p: Plan) = when (p.validity) { Validity.MonthEnd -> "till month end"; Validity.SameDay -> "travel day only"; Validity.Days -> "${p.validityDays} days" }

@Composable
private fun PlanEditor(p: Plan, onDismiss: () -> Unit, onSave: (Plan) -> Unit) {
  var title by remember { mutableStateOf(p.title) }
  var kind by remember { mutableStateOf(p.kind) }
  var rides by remember { mutableStateOf(p.rides?.toString().orEmpty()) }
  var price by remember { mutableStateOf(if (p.basePrice > 0) p.basePrice.toString() else "") }
  var validity by remember { mutableStateOf(p.validity) }
  var days by remember { mutableStateOf(p.validityDays.toString()) }
  var desc by remember { mutableStateOf(p.description) }
  var popular by remember { mutableStateOf(p.popular) }
  var active by remember { mutableStateOf(p.active) }
  val tiers = remember { p.tiers.map { listOf(it.fromDay.toString(), it.toDay.toString(), it.price.toString()) }.toMutableStateList() }
  val parsedTiers = tiers.mapNotNull { t -> val (a, b, c) = t; if (a.toIntOrNull() != null && b.toIntOrNull() != null && c.toIntOrNull() != null) PriceTier(a.toInt(), b.toInt(), c.toInt()) else null }
  val valid = title.isNotBlank() && (price.toIntOrNull() ?: 0) > 0 && (kind != PlanKind.Rides || (rides.toIntOrNull() ?: 0) > 0) &&
    parsedTiers.size == tiers.size && parsedTiers.all { it.fromDay in 1..31 && it.toDay in it.fromDay..31 && it.price > 0 } &&
    parsedTiers.sortedBy { it.fromDay }.zipWithNext().none { (a, b) -> b.fromDay <= a.toDay } && (validity != Validity.Days || (days.toIntOrNull() ?: 0) > 0)
  EditorSheet(if (p.title.isBlank()) "New plan" else "Edit plan", onDismiss, {
    onSave(p.copy(
      title = title.trim(), kind = kind, rides = if (kind == PlanKind.Rides) rides.toInt() else null, basePrice = price.toInt(),
      validity = if (kind == PlanKind.Single) Validity.SameDay else validity, validityDays = days.toIntOrNull() ?: 30,
      description = desc.trim(), popular = popular, active = active, tiers = if (kind == PlanKind.Single) emptyList() else parsedTiers,
    ))
  }, valid) {
    FormField("Name", title, { title = it }, placeholder = "e.g. 24 Rides")
    Segmented(listOf("Ride pack", "Unlimited", "One-way"), when (kind) { PlanKind.Rides -> 0; PlanKind.Unlimited -> 1; PlanKind.Single -> 2 }, {
      kind = listOf(PlanKind.Rides, PlanKind.Unlimited, PlanKind.Single)[it]
    })
    if (kind == PlanKind.Rides) FormField("Rides included", rides, { rides = it.digits().take(3) }, keyboard = KeyboardType.Number)
    FormField("Base price", price, { price = it.digits().take(6) }, keyboard = KeyboardType.Number, prefix = "₹")
    if (kind != PlanKind.Single) {
      Segmented(listOf("Till month end", "Fixed days"), if (validity == Validity.Days) 1 else 0, { validity = if (it == 1) Validity.Days else Validity.MonthEnd })
      if (validity == Validity.Days) FormField("Valid for (days)", days, { days = it.digits().take(3) }, keyboard = KeyboardType.Number)
      SectionLabel("Price by date of purchase")
      tiers.forEachIndexed { i, t ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
          FormField("From day", t[0], { tiers[i] = listOf(it.digits().take(2), t[1], t[2]) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
          FormField("To day", t[1], { tiers[i] = listOf(t[0], it.digits().take(2), t[2]) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
          FormField("Price", t[2], { tiers[i] = listOf(t[0], t[1], it.digits().take(6)) }, Modifier.weight(1.3f), keyboard = KeyboardType.Number)
          Box(Modifier.padding(bottom = 10.dp).size(36.dp).clip(CircleShape).tappable { tiers.removeAt(i) }, contentAlignment = Alignment.Center) { Symbol("close", size = 18.dp, tint = NudgeColors.OnSurfaceVariant) }
        }
      }
      GhostButton("Add date range", { tiers.add(listOf("", "", "")) }, Modifier.fillMaxWidth(), color = NudgeColors.Primary, icon = "add")
    }
    FormField("Short description", desc, { desc = it }, placeholder = "Shown under the price")
    ToggleRow("Most popular badge", null, popular) { popular = it }
    ToggleRow("Show to riders", "Hidden plans can't be bought.", active) { active = it }
  }
}

// ── Staff
@Composable
fun StaffScreen(staff: List<StaffMember>, onLoad: () -> Unit, onUpsert: (String, String, Role, () -> Unit) -> Unit, onRemove: (String) -> Unit, myPhone: String = "", onBack: () -> Unit) {
  LaunchedEffect(Unit) { onLoad() }
  var editing by remember { mutableStateOf<StaffMember?>(null) }
  var removing by remember { mutableStateOf<StaffMember?>(null) }
  ManageFrame("Drivers & staff", onBack, onAdd = { editing = StaffMember(null, "", "", Role.DRIVER, false) }) {
    item { Hint("Add someone by phone number. They log in with OTP and get the driver or operator app. Assign drivers to routes in Routes.") }
    items(staff, key = { it.phone }) { m ->
      ListCard(m.name.ifBlank { "No name" }, "${prettyPhone(m.phone)} · ${if (m.role == Role.ADMIN) "Operator" else "Driver"}${if (!m.joined) " · not logged in yet" else ""}",
        leadingIcon = if (m.role == Role.ADMIN) "admin_panel_settings" else "badge", onClick = { editing = m })
    }
  }
  editing?.let { m ->
    var name by remember(m) { mutableStateOf(m.name) }
    var phone by remember(m) { mutableStateOf(m.phone) }
    var role by remember(m) { mutableStateOf(m.role) }
    EditorSheet(
      if (m.phone.isBlank()) "Add staff" else "Edit ${m.name}", { editing = null }, { onUpsert(phone, name, role) { editing = null } },
      name.isNotBlank() && phone.digits().length == 10 && phone.first() in "6789",
      extraAction = if (m.phone.isNotBlank() && m.phone != myPhone.takeLast(10)) ({ GhostButton("Remove access", { removing = m; editing = null }, Modifier.fillMaxWidth(), color = NudgeColors.Error) }) else null,
    ) {
      FormField("Name", name, { name = it }, placeholder = "Full name")
      // Phone is the login: changing it would make a second person, so it's fixed once added.
      if (m.phone.isBlank()) FormField("Mobile number", phone, { phone = it.digits().takeLast(10) }, keyboard = KeyboardType.Phone, prefix = "+91")
      else ListCard(prettyPhone(m.phone), "To change the number, remove this person and add them again.", leadingIcon = "call")
      if (m.phone != myPhone.takeLast(10)) Segmented(listOf("Driver", "Operator"), if (role == Role.ADMIN) 1 else 0, { role = if (it == 1) Role.ADMIN else Role.DRIVER })
    }
  }
  removing?.let { m -> ConfirmDialog("Remove ${m.name}?", "They become a regular rider and are unassigned from their route.", "Remove", { onRemove(m.phone) }, { removing = null }) }
}

// ── Shuttles
@Composable
fun ShuttlesScreen(shuttles: List<Shuttle>, onLoad: () -> Unit, onSave: (Shuttle, () -> Unit) -> Unit, onBack: () -> Unit) {
  LaunchedEffect(Unit) { onLoad() }
  var editing by remember { mutableStateOf<Shuttle?>(null) }
  ManageFrame("Shuttles", onBack, onAdd = { editing = Shuttle("", "", 16, true) }) {
    item { Hint("Seats limit how many riders can book a trip. Assign a shuttle to each route in Routes.") }
    items(shuttles, key = { it.id }) { s ->
      ListCard(s.code, "${s.seats} seats${if (!s.active) " · out of service" else ""}", leadingIcon = "airport_shuttle", onClick = { editing = s })
    }
  }
  editing?.let { s ->
    var code by remember(s) { mutableStateOf(s.code) }
    var seats by remember(s) { mutableStateOf(s.seats.toString()) }
    var active by remember(s) { mutableStateOf(s.active) }
    EditorSheet(if (s.id.isBlank()) "Add shuttle" else "Edit ${s.code}", { editing = null },
      { onSave(s.copy(code = code, seats = seats.toInt(), active = active)) { editing = null } }, code.isNotBlank() && (seats.toIntOrNull() ?: 0) > 0) {
      FormField("Shuttle number", code, { code = it.uppercase().take(16) }, placeholder = "e.g. UP16 AB 1234")
      FormField("Seats", seats, { seats = it.digits().take(2) }, keyboard = KeyboardType.Number)
      ToggleRow("In service", null, active) { active = it }
    }
  }
}

// ── Rules & support
@Composable
fun RulesScreen(rules: Rules, onSave: (Rules, () -> Unit) -> Unit, onBack: () -> Unit) {
  var cutoff by rememberSaveable { mutableStateOf(rules.cancelCutoffMin.toString()) }
  var window by rememberSaveable { mutableStateOf(rules.bookingWindowDays.toString()) }
  var pending by rememberSaveable { mutableStateOf(rules.allowPendingBooking) }
  var supportName by rememberSaveable { mutableStateOf(rules.supportName) }
  var supportPhone by rememberSaveable { mutableStateOf(rules.supportPhone.takeLast(10)) }
  val problem = when {
    (window.toIntOrNull() ?: 0) !in 1..14 -> "Booking window must be 1 to 14 days."
    cutoff.toIntOrNull() == null -> "Enter the cancel time in minutes (0 = until pickup)."
    supportPhone.length != 10 -> "Enter the 10-digit phone riders call for help."
    else -> null
  }
  val valid = problem == null
  Column(Modifier.fillMaxSize().background(NudgeColors.Background).imePadding()) {
    ScreenHeader("Rules & support", onBack = onBack)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      SectionLabel("Booking")
      FormField("Riders can book up to (days ahead)", window, { window = it.digits().take(2) }, keyboard = KeyboardType.Number)
      FormField("Cancel allowed until (minutes before pickup)", cutoff, { cutoff = it.digits().take(3) }, keyboard = KeyboardType.Number)
      Hint("Cancelling never refunds money. A ride from a pack goes back to the pack.")
      ToggleRow("Book while payment is in review", "Riders who sent a payment reference can take a seat right away. Rejecting the payment cancels their seats.", pending) { pending = it }
      SectionLabel("Support contact")
      FormField("Name shown to riders", supportName, { supportName = it })
      FormField("Phone (only for issues)", supportPhone, { supportPhone = it.digits().takeLast(10) }, keyboard = KeyboardType.Phone, prefix = "+91")
      if (problem != null) Text(problem, style = NudgeType.BodySm, color = Color(0xFFFCD34D), modifier = Modifier.padding(horizontal = 8.dp))
    }
    PrimaryButton("Save", {
      onSave(Rules(cutoff.toInt(), window.toInt(), pending, supportName.trim(), "91$supportPhone")) {}
    }, Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 16.dp), enabled = valid)
  }
}

// ── Route editor
private var lastEditorShift = 0 // which shift tab was open (kept across the map picker)

private fun ascendingProblem(stops: List<Stop>, label: String): String? {
  val bad = stops.zipWithNext().firstOrNull { (a, b) -> parseClock(b.time).isBefore(parseClock(a.time)) } ?: return null
  return "$label: ${bad.second.name} (${bad.second.time}) is earlier than the stop before it (${bad.first.time})."
}

/** Edit a route's details, shuttle, driver and both stop timetables. The draft lives in the ViewModel ([onChange]). */
@Composable
fun RouteEditorScreen(
  route: RouteInfo, shuttles: List<Shuttle>, drivers: List<StaffMember>, notes: List<String>, busy: Boolean, onLoad: () -> Unit,
  onChange: ((RouteInfo) -> RouteInfo) -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
  onPickOnMap: (evening: Boolean, index: Int) -> Unit, onBack: () -> Unit,
) {
  LaunchedEffect(Unit) { onLoad() }
  var shift by remember { mutableStateOf(lastEditorShift) }
  LaunchedEffect(shift) { lastEditorShift = shift }
  val evening = shift == 1
  val stops = route.stops(evening)
  fun setStops(list: List<Stop>) = onChange { r -> if (evening) r.copy(evening = list) else r.copy(morning = list) }
  var editing by remember { mutableStateOf<Pair<Int, Stop>?>(null) } // index (== size for a new stop), stop
  var deleting by remember { mutableStateOf(false) }
  val unpinned = (route.morning + route.evening).count { it.lat == 0.0 && it.lng == 0.0 }
  val problem = when {
    route.name.isBlank() -> "Give the route a name."
    route.morning.size == 1 || route.evening.size == 1 -> "A shift needs at least 2 stops (pickup and drop), or none."
    route.morning.size < 2 && route.evening.size < 2 -> "Add at least 2 stops."
    else -> ascendingProblem(route.morning, "Morning") ?: ascendingProblem(route.evening, "Evening")
      ?: if (unpinned > 0) "$unpinned stop${if (unpinned == 1) " has" else "s have"} no map pin. Tap the red ones to set it." else null
  }

  Column(Modifier.fillMaxSize().background(NudgeColors.Background).imePadding()) {
    ScreenHeader(if (route.id.isBlank()) "New route" else "Edit route", onBack = onBack)
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (notes.isNotEmpty()) item {
        Column(
          Modifier.fillMaxWidth().clip(Radius24).background(NudgeColors.PrimaryContainer.copy(alpha = 0.12f)).padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("Imported from PDF. Please check:", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnSurface)
          notes.forEach { Text("• $it", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant) }
        }
      }
      item { FormField("Route name", route.name, { v -> onChange { it.copy(name = v) } }, placeholder = "e.g. Route 4") }
      item { FormField("Starts from (area)", route.from, { v -> onChange { it.copy(from = v) } }, placeholder = "e.g. Indirapuram") }
      item { FormField("Goes to (area)", route.to, { v -> onChange { it.copy(to = v) } }, placeholder = "e.g. Noida Sector 135") }
      item { SectionLabel("Shuttle") }
      item {
        val active = shuttles.filter { it.active }
        if (active.isEmpty()) Hint("No shuttles yet. Add one in Profile → Shuttles.")
        else ChoiceChips(listOf<Pair<String?, String>>(null to "None") + active.map { it.id to "${it.code} · ${it.seats} seats" }, route.vehicleId) { v -> onChange { it.copy(vehicleId = v) } }
      }
      item { SectionLabel("Driver") }
      item {
        val joined = drivers.filter { it.role == Role.DRIVER && it.id != null }
        if (joined.isEmpty()) Hint("No drivers have logged in yet. Add them in Profile → Drivers & staff; they appear here after their first login.")
        else ChoiceChips(listOf<Pair<String?, String>>(null to "None") + joined.map { it.id to it.name }, route.driverId) { v -> onChange { it.copy(driverId = v) } }
      }
      item { ToggleRow("Route is running", "Riders can only book running routes.", route.active) { v -> onChange { it.copy(active = v) } } }
      item { SectionLabel("Stops & pickup times") }
      item { Segmented(listOf("Morning (${route.morning.size})", "Evening (${route.evening.size})"), shift, { shift = it }) }
      item { Hint("In travel order. The last stop is where riders get off.") }
      if (evening && stops.isEmpty() && route.morning.size >= 2) item {
        GhostButton("Use morning stops in reverse", {
          // Same stops, reverse order; times start at 5:30 PM, 3 min apart. Adjust after.
          var t = java.time.LocalTime.of(17, 30)
          setStops(route.morning.reversed().map { s -> s.copy(id = "", time = formatClock(t)).also { t = t.plusMinutes(3) } })
        }, Modifier.fillMaxWidth(), color = NudgeColors.Primary, icon = "swap_vert")
      }
      itemsIndexed(stops, key = { i, s -> "$shift-$i-${s.id}-${s.name}" }) { i, s ->
        StopRow(i, s, last = i == stops.lastIndex,
          onUp = if (i > 0) ({ setStops(stops.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) else null,
          onDown = if (i < stops.lastIndex) ({ setStops(stops.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }) else null,
          onEdit = { editing = i to s }, onPin = { onPickOnMap(evening, i) })
      }
      item {
        GhostButton("Add stop", {
          val prev = stops.lastOrNull()
          val t = prev?.let { formatClock(parseClock(it.time).plusMinutes(3)) } ?: if (evening) "05:30 PM" else "08:00 AM"
          editing = stops.size to Stop("", "", 0.0, 0.0, t)
        }, Modifier.fillMaxWidth(), color = NudgeColors.Primary, icon = "add_location")
      }
      if (route.id.isNotBlank()) item {
        GhostButton("Delete route", { deleting = true }, Modifier.fillMaxWidth(), color = NudgeColors.Error, icon = "delete")
      }
    }
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (problem != null) Text(problem, style = NudgeType.BodySm, color = Color(0xFFFCD34D))
      PrimaryButton(if (busy) "Saving…" else "Save route", onSave, Modifier.fillMaxWidth(), enabled = problem == null && !busy)
    }
  }

  if (deleting) ConfirmDialog(
    "Delete ${route.name}?", "Riders can no longer book it. If it was ever used, its past rides stay in history.", "Delete", onDelete, { deleting = false },
  )

  editing?.let { (index, s) ->
    StopSheet(
      s, isNew = index >= stops.size, onDismiss = { editing = null },
      onSave = { updated ->
        setStops(stops.toMutableList().also { if (index < it.size) it[index] = updated else it.add(updated) })
        editing = null
      },
      onRemove = { setStops(stops.toMutableList().also { it.removeAt(index) }); editing = null },
      onAddAfter = { updated ->
        val list = stops.toMutableList().also { if (index < it.size) it[index] = updated else it.add(updated) }
        val pos = (index + 1).coerceAtMost(list.size)
        setStops(list)
        editing = pos to Stop("", "", 0.0, 0.0, formatClock(parseClock(updated.time).plusMinutes(2)))
      },
      onPickOnMap = { updated ->
        // Save what's typed, then open the map for this stop.
        val list = stops.toMutableList().also { if (index < it.size) it[index] = updated else it.add(updated) }
        setStops(list)
        editing = null
        onPickOnMap(evening, if (index < stops.size) index else list.lastIndex)
      },
    )
  }
}

/** One stop: name, pickup time (clock picker), and its map pin (search by name or place it by hand). */
@Composable
private fun StopSheet(
  s: Stop, isNew: Boolean, onDismiss: () -> Unit, onSave: (Stop) -> Unit, onRemove: () -> Unit,
  onAddAfter: (Stop) -> Unit, onPickOnMap: (Stop) -> Unit,
) {
  var name by remember(s) { mutableStateOf(s.name) }
  var time by remember(s) { mutableStateOf(s.time) }
  var lat by remember(s) { mutableStateOf(s.lat) }
  var lng by remember(s) { mutableStateOf(s.lng) }
  var results by remember { mutableStateOf<List<com.nudge.app.data.Geocoder.Place>?>(null) }
  var searching by remember { mutableStateOf(false) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val current = { s.copy(name = name.trim(), time = time, lat = lat, lng = lng) }
  EditorSheet(
    if (isNew) "Add stop" else "Edit stop", onDismiss, { onSave(current()) }, name.isNotBlank(),
    extraAction = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Add next", { onAddAfter(current()) }, Modifier.weight(1f), color = NudgeColors.Primary, icon = "add")
        if (!isNew) GhostButton("Remove", onRemove, Modifier.weight(1f), color = NudgeColors.Error, icon = "delete")
      }
    },
  ) {
    FormField("Stop name", name, { name = it }, placeholder = "e.g. Vaishali Metro Gate 2")
    TimeField("Pickup time", time) { time = it }
    SectionLabel("Location")
    val pinned = !(lat == 0.0 && lng == 0.0)
    ListCard(
      if (pinned) "Pin is set" else "No pin yet", if (pinned) "Riders see this spot on the map" else "Search the name, or place the pin yourself",
      leadingIcon = if (pinned) "location_on" else "location_off",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      GhostButton(if (searching) "Searching…" else "Search", {
        if (name.isBlank() || searching) return@GhostButton
        searching = true
        scope.launch { results = com.nudge.app.data.Geocoder.search(name); searching = false }
      }, Modifier.weight(1f), color = NudgeColors.Primary, icon = "search")
      GhostButton("On map", { onPickOnMap(current()) }, Modifier.weight(1f), color = NudgeColors.Primary, icon = "map")
    }
    results?.let { list ->
      if (list.isEmpty()) Hint("Nothing found. Try a shorter name, or place the pin on the map.")
      list.take(5).forEach { p ->
        ListCard(p.name.substringBefore(","), p.name.substringAfter(", ", "").take(60), leadingIcon = "place", onClick = { lat = p.lat; lng = p.lng; results = null })
      }
    }
  }
}

/** Shows a time; tapping opens a clock (no typing "07:57 AM" by hand). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, time: String, onTime: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  ListCard(label, time, leadingIcon = "schedule", onClick = { open = true }) {
    Text("Change", style = twText(13f, FontWeight.SemiBold, 18.sp), color = NudgeColors.Primary)
  }
  if (open) {
    val t = runCatching { parseClock(time) }.getOrDefault(java.time.LocalTime.of(8, 0))
    val st = androidx.compose.material3.rememberTimePickerState(t.hour, t.minute, is24Hour = false)
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { open = false },
      containerColor = NudgeColors.SurfaceContainerHigh,
      confirmButton = { androidx.compose.material3.TextButton({ onTime(formatClock(java.time.LocalTime.of(st.hour, st.minute))); open = false }) { Text("OK", color = NudgeColors.Primary) } },
      dismissButton = { androidx.compose.material3.TextButton({ open = false }) { Text("Cancel", color = NudgeColors.Primary) } },
      text = {
        androidx.compose.material3.TimePicker(
          st,
          colors = androidx.compose.material3.TimePickerDefaults.colors(
            clockDialColor = NudgeColors.SurfaceContainerHighest, selectorColor = NudgeColors.PrimaryContainer,
            timeSelectorSelectedContainerColor = NudgeColors.PrimaryContainer, timeSelectorSelectedContentColor = Color.White,
            timeSelectorUnselectedContainerColor = NudgeColors.SurfaceContainerHighest, timeSelectorUnselectedContentColor = NudgeColors.OnSurface,
            periodSelectorSelectedContainerColor = NudgeColors.PrimaryContainer, periodSelectorSelectedContentColor = Color.White,
            periodSelectorUnselectedContentColor = NudgeColors.OnSurfaceVariant, clockDialUnselectedContentColor = NudgeColors.OnSurface,
          ),
        )
      },
    )
  }
}

/** Add a route: from a PDF timetable, by copying a route with shifted times, or by hand. */
@Composable
fun AddRouteSheet(onDismiss: () -> Unit, onImport: () -> Unit, onCopy: () -> Unit, onManual: () -> Unit) {
  @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
  androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NudgeColors.SurfaceContainerLow) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Add a route", style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface)
      ListCard("From a PDF timetable", "Pick the route PDF (the one you share with riders). Stops and times are filled in for you.", leadingIcon = "picture_as_pdf", onClick = onImport)
      ListCard("Copy a route", "Same stops as an existing route, all times moved earlier or later.", leadingIcon = "content_copy", onClick = onCopy)
      ListCard("Add stops myself", "Type each stop and pick its time.", leadingIcon = "edit", onClick = onManual)
    }
  }
}

/** Copy a route: pick it, name the new one, choose how many minutes later (or earlier) it runs. */
@Composable
fun CopyRouteSheet(routes: List<RouteInfo>, busy: Boolean, onDismiss: () -> Unit, onCopy: (RouteInfo, String, Int) -> Unit) {
  var from by remember { mutableStateOf(routes.firstOrNull()) }
  var name by remember { mutableStateOf("") }
  var minutes by remember { mutableStateOf(20) }
  EditorSheet("Copy a route", onDismiss, { from?.let { onCopy(it, name.trim(), minutes) } }, from != null && name.isNotBlank() && !busy, saveLabel = if (busy) "Copying…" else "Create copy") {
    SectionLabel("Copy from")
    ChoiceChips(routes.map { it to "${it.name} · ${it.morningTime}" }, from) { from = it }
    FormField("New route name", name, { name = it }, placeholder = "e.g. Route 4")
    SectionLabel("Runs")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      GhostButton("−5", { minutes -= 5 }, Modifier.weight(1f))
      Text(
        when { minutes == 0 -> "Same time"; minutes > 0 -> "$minutes min later"; else -> "${-minutes} min earlier" },
        style = twText(16f, FontWeight.Bold, 24.sp), color = NudgeColors.OnSurface, modifier = Modifier.weight(2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
      GhostButton("+5", { minutes += 5 }, Modifier.weight(1f))
    }
    from?.let { r ->
      val first = r.morning.firstOrNull() ?: r.evening.firstOrNull()
      if (first != null) Hint("First pickup: ${first.time} → ${formatClock(parseClock(first.time).plusMinutes(minutes.toLong()))}")
    }
  }
}

@Composable
private fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { (value, label) ->
      val on = value == selected
      Text(
        label, style = twText(14f, FontWeight.SemiBold, 20.sp), maxLines = 1,
        color = if (on) NudgeColors.OnPrimaryContainer else NudgeColors.OnSurfaceVariant,
        modifier = Modifier.clip(CircleShape).background(if (on) NudgeColors.PrimaryContainer else NudgeColors.SurfaceContainer)
          .tappable(pressedScale = 0.95f) { onPick(value) }.padding(horizontal = 16.dp, vertical = 10.dp),
      )
    }
  }
}

@Composable
private fun StopRow(i: Int, s: Stop, last: Boolean, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onEdit: () -> Unit, onPin: () -> Unit) {
  val pinned = !(s.lat == 0.0 && s.lng == 0.0)
  Row(
    Modifier.fillMaxWidth().clip(Radius24).background(NudgeColors.SurfaceContainerLow).hairline(if (pinned) Color.White.copy(alpha = 0.05f) else NudgeColors.Error.copy(alpha = 0.5f), Radius24)
      .tappable(pressedScale = 0.99f, onClick = onEdit).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("${i + 1}", style = twText(12f, FontWeight.Bold, 16.sp), color = NudgeColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
    Column(Modifier.weight(1f)) {
      Text(s.name, style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.time + if (last) " · drop" else "", style = NudgeType.BodySm, color = NudgeColors.Primary)
        if (!pinned) Text("Set pin", style = twText(12f, FontWeight.Bold, 16.sp), color = NudgeColors.Error, modifier = Modifier.clip(CircleShape).tappable(onClick = onPin).padding(horizontal = 6.dp, vertical = 2.dp))
      }
    }
    Arrow("keyboard_arrow_up", onUp)
    Arrow("keyboard_arrow_down", onDown)
  }
}

@Composable
private fun Arrow(icon: String, onClick: (() -> Unit)?) {
  Box(
    Modifier.size(36.dp).clip(CircleShape).then(if (onClick != null) Modifier.tappable(onClick = onClick) else Modifier),
    contentAlignment = Alignment.Center,
  ) { Symbol(icon, size = 20.dp, tint = if (onClick != null) NudgeColors.OnSurface else NudgeColors.OutlineVariant) }
}

/** Full-screen map with a fixed centre pin: pan the map under the pin, then confirm. */
@Composable
fun MapPickerScreen(start: Stop, onPicked: (Double, Double) -> Unit, onBack: () -> Unit) {
  var lat by remember { mutableStateOf(start.lat) }
  var lng by remember { mutableStateOf(start.lng) }
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    LiveMap(emptyList(), Modifier.fillMaxSize(), center = Position(start.lat, start.lng, null, 0), onCenter = { a, b -> lat = a; lng = b }, topInsetDp = 100)
    Box(Modifier.align(Alignment.Center).padding(bottom = 40.dp)) { Symbol("location_on", size = 48.dp, tint = NudgeColors.PrimaryContainer, filled = true) }
    ScreenHeader(start.name.ifBlank { "Pick location" }, Modifier.background(NudgeColors.Background.copy(alpha = 0.85f)), onBack = onBack)
    Column(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(NudgeColors.SurfaceContainerLow, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
        .windowInsetsPadding(WindowInsets.navigationBars).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Move the map so the pin sits on the pickup spot.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      Text("%.5f, %.5f".format(lat, lng), style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnSurface)
      PrimaryButton("Use this location", { onPicked(lat, lng) }, Modifier.fillMaxWidth())
    }
  }
}
