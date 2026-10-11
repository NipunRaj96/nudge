package com.nudge.app.ui.booking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.PassStatus
import com.nudge.app.data.RouteInfo
import com.nudge.app.data.Stop
import com.nudge.app.ui.components.GhostButton
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.Segmented
import com.nudge.app.ui.components.SectionLabel
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

data class DateOption(val dow: String, val day: String, val month: String) {
  val label get() = "${dow.lowercase().replaceFirstChar { it.uppercase() }}, $day ${month.lowercase().replaceFirstChar { it.uppercase() }}"
}

private val Radius32 = RoundedCornerShape(32.dp)

/**
 * Book a ride: date -> morning/evening -> route -> pickup point (with its scheduled time) -> confirm.
 * One decision per row; selectors collapse to a single line. Without a pass the main action is "pay one-way & book",
 * with a quiet link to the monthly plans.
 */
@Composable
fun BookRideScreen(
  routes: List<RouteInfo>,
  selectedRoute: RouteInfo?,
  onRoute: (RouteInfo) -> Unit,
  dates: List<DateOption>,
  selectedDate: Int,
  onDate: (Int) -> Unit,
  shift: Int,
  onShift: (Int) -> Unit,
  pickup: Stop?,
  /** Pickups the rider can still book (today: only those not yet passed). */
  pickups: List<Stop>,
  onPickup: (Stop) -> Unit,
  passSummary: String,
  passStatus: PassStatus,
  ctaLabel: String,
  ctaEnabled: Boolean,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
  secondaryLabel: String? = null,
  /** Why nothing can be booked for this choice (no evening service, all pickups passed...). */
  shiftNote: String? = null,
  onSecondary: () -> Unit = {},
) {
  var routeOpen by remember { mutableStateOf(false) }
  var pickupOpen by remember { mutableStateOf(false) }
  val evening = shift == 1
  val stops = selectedRoute?.stops(evening).orEmpty()
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Book a ride")
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Date", Modifier.padding(horizontal = 24.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          dates.forEachIndexed { i, d -> DatePill(d, i == selectedDate, Modifier.width(76.dp)) { onDate(i) } }
        }
      }

      Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Segmented(listOf("Morning", "Evening"), shift, onShift)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SelectorRow("Route", selectedRoute?.name ?: "Choose a route", "route", routeOpen) { routeOpen = !routeOpen; pickupOpen = false }
          Column(Modifier.animateContentSize(tween(240))) {
            if (routeOpen) Options(routes.map { it.name to "From ${if (evening) it.eveningTime else it.morningTime}" }, selectedRoute?.name) { name ->
              routes.firstOrNull { it.name == name }?.let(onRoute); routeOpen = false
            }
          }
          SelectorRow("Pickup point", pickup?.name ?: "Choose a stop", "location_on", pickupOpen) { pickupOpen = !pickupOpen; routeOpen = false }
          Column(Modifier.animateContentSize(tween(240))) {
            if (pickupOpen) Options(pickups.map { it.name to it.time }, pickup?.name, scrollable = true) { name ->
              pickups.firstOrNull { it.name == name }?.let(onPickup); pickupOpen = false
            }
          }
        }

        // Pickup summary: when and where to stand, and where the shuttle ends up
        Row(
          Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(20.dp),
          horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("PICKUP", style = NudgeType.LabelCaps, color = NudgeColors.OnSurfaceVariant)
            Text(pickup?.time ?: "--", style = twText(32f, FontWeight.Bold, 40.sp, -0.02f), color = NudgeColors.Primary)
            stops.lastOrNull()?.let { Text("Reaches ${it.name} by ${it.time}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 2) }
          }
          Symbol("directions_bus", size = 32.dp, tint = NudgeColors.Primary)
        }

        if (shiftNote != null) Text(shiftNote, style = NudgeType.BodySm, color = Color(0xFFFCD34D), modifier = Modifier.padding(horizontal = 4.dp))
        PassLine(passSummary, passStatus)
        Spacer(Modifier.height(8.dp))
      }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      PrimaryButton(ctaLabel, onConfirm, Modifier.fillMaxWidth(), icon = "arrow_forward", enabled = ctaEnabled && selectedRoute != null && pickup != null)
      if (secondaryLabel != null) GhostButton(secondaryLabel, onSecondary, Modifier.fillMaxWidth(), color = NudgeColors.Primary)
    }
  }
}

@Composable
private fun DatePill(d: DateOption, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val bg by animateColorAsState(if (active) NudgeColors.Primary else NudgeColors.SurfaceContainerHigh, tween(220), label = "bg")
  val fg by animateColorAsState(if (active) Color(0xFF001356) else NudgeColors.OnSurfaceVariant, tween(220), label = "fg")
  Column(
    modifier.clip(Radius32).background(bg).then(if (active) Modifier else Modifier.hairline(Color.White.copy(alpha = 0.05f), Radius32))
      .tappable(pressedScale = 0.96f, onClick = onClick).padding(vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(d.dow, style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), color = fg.copy(alpha = 0.75f))
    Text(d.day, style = twText(24f, FontWeight.Bold, 32.sp), color = fg)
    Text(d.month, style = twText(12f, FontWeight.Medium, 16.sp), color = fg.copy(alpha = 0.75f))
  }
}

@Composable
private fun SelectorRow(label: String, value: String, icon: String, open: Boolean, onClick: () -> Unit) {
  val rot by animateFloatAsState(if (open) 180f else 0f, tween(240), label = "chev")
  Row(
    Modifier.fillMaxWidth().clip(Radius32).background(NudgeColors.SurfaceContainerLow)
      .hairline(if (open) NudgeColors.Primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f), Radius32)
      .tappable(pressedScale = 0.99f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(40.dp).background(NudgeColors.SurfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Symbol(icon, size = 20.dp, tint = NudgeColors.Primary) }
    Column(Modifier.weight(1f)) {
      Text(label, style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), color = NudgeColors.OnSurfaceVariant)
      Text(value, style = twText(16f, FontWeight.SemiBold, 24.sp), color = NudgeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Symbol("expand_more", tint = NudgeColors.OnSurfaceVariant, modifier = Modifier.rotate(rot))
  }
}

@Composable
private fun Options(items: List<Pair<String, String>>, selected: String?, scrollable: Boolean = false, onPick: (String) -> Unit) {
  Column(
    Modifier.padding(top = 8.dp).fillMaxWidth().background(NudgeColors.SurfaceContainerLowest, Radius32).hairline(Color.White.copy(alpha = 0.06f), Radius32)
      .then(if (scrollable) Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()) else Modifier).padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (items.isEmpty()) Text("Nothing available yet", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(12.dp))
    items.forEach { (item, trailing) ->
      val on = item == selected
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (on) NudgeColors.PrimaryContainer.copy(alpha = 0.16f) else Color.Transparent)
          .tappable(pressedScale = 0.99f) { onPick(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(item, style = twText(14f, if (on) FontWeight.SemiBold else FontWeight.Normal, 20.sp), color = if (on) NudgeColors.Primary else NudgeColors.OnSurface, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(trailing, style = twText(13f, FontWeight.Medium, 18.sp), color = if (on) NudgeColors.Primary else NudgeColors.OnSurfaceVariant)
      }
    }
  }
}

@Composable
private fun PassLine(text: String, status: PassStatus) {
  val (icon, tint) = when (status) {
    PassStatus.Active -> "verified" to NudgeColors.Primary
    PassStatus.PendingApproval -> "hourglass_top" to Color(0xFFFCD34D)
    PassStatus.None -> "confirmation_number" to NudgeColors.OnSurfaceVariant
  }
  Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Symbol(icon, size = 18.dp, tint = tint, filled = status == PassStatus.Active)
    Text(text, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
  }
}
