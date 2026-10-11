package com.nudge.app.ui.driver

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.BoardingStatus
import com.nudge.app.data.DriverTrip
import com.nudge.app.data.TripPassenger
import com.nudge.app.ui.components.ConfirmDialog
import com.nudge.app.ui.components.InitialsAvatar
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.SectionLabel
import com.nudge.app.ui.components.StatusChip
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius32 = RoundedCornerShape(32.dp)

@Composable
fun DriverActiveTripScreen(
  trip: DriverTrip,
  onBoard: (TripPassenger) -> Unit,
  onNoShow: (TripPassenger) -> Unit,
  onMoveStop: (Int) -> Unit,
  onEndTrip: () -> Unit,
  modifier: Modifier = Modifier,
  onCall: (TripPassenger) -> Unit = {},
  onUndo: (TripPassenger) -> Unit = {},
  busy: Boolean = false,
  /** Shown when GPS sharing is off (permission denied / location turned off). */
  locationWarning: String? = null,
  onFixLocation: () -> Unit = {},
) {
  val stop = trip.stops.getOrNull(trip.currentStopIndex)
  if (stop == null) {
    // Operator removed the stops of a running trip: still let the driver finish.
    Column(modifier.fillMaxSize().background(NudgeColors.Background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
      Text("This trip has no stops any more.", style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface)
      Text("Call the office if this is unexpected.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      PrimaryButton("End trip", onEndTrip, Modifier.fillMaxWidth(), enabled = !busy)
    }
    return
  }
  val last = trip.currentStopIndex == trip.stops.lastIndex
  val stopIndex = trip.stops.withIndex().associate { (i, s) -> s.id to i }
  fun indexOf(p: TripPassenger) = stopIndex[p.stopId] ?: trip.stops.indexOfFirst { it.name == p.stopName }
  val atStop = trip.passengers.filter { indexOf(it) == trip.currentStopIndex }
  // Riders still waiting at stops already passed: easy to board them late or mark no-show.
  val missed = trip.passengers.filter { it.status == BoardingStatus.Waiting && indexOf(it) in 0 until trip.currentStopIndex }
  val boarded = trip.passengers.count { it.status == BoardingStatus.Boarded }
  val waitingAll = trip.passengers.count { it.status == BoardingStatus.Waiting }
  val waitingHere = atStop.count { it.status == BoardingStatus.Waiting }
  var confirmEnd by remember { mutableStateOf(false) }
  var confirmNext by remember { mutableStateOf(false) }

  if (confirmEnd) ConfirmDialog(
    "End this trip?",
    if (waitingAll == 0) "Everyone is marked. GPS sharing stops." else "$waitingAll rider${if (waitingAll == 1) " has" else "s have"} not boarded and will be marked no-show (their ride is used).",
    "End trip", onEndTrip, { confirmEnd = false }, dismiss = "Keep going",
  )
  if (confirmNext) ConfirmDialog(
    "Leave this stop?", "$waitingHere rider${if (waitingHere == 1) " is" else "s are"} still waiting here. You can still board them later from \"Not boarded yet\".",
    "Next stop", { onMoveStop(1) }, { confirmNext = false }, dismiss = "Stay", destructive = false,
  )

  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Trip in progress")
    if (locationWarning != null) Row(
      Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(Radius32).background(Color(0xFFFCD34D).copy(alpha = 0.14f)).tappable(onClick = onFixLocation).padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
      Symbol("location_off", size = 20.dp, tint = Color(0xFFFCD34D))
      Text(locationWarning, style = NudgeType.BodySm, color = Color(0xFFFCD34D), modifier = Modifier.weight(1f))
      Text("FIX", style = twText(13f, FontWeight.Bold, 18.sp, 0.05f), color = Color(0xFFFCD34D))
    }
    Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("STOP ${trip.currentStopIndex + 1} OF ${trip.stops.size}", style = NudgeType.LabelCaps.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.1f, androidx.compose.ui.unit.TextUnitType.Em)), color = NudgeColors.Primary)
        Text(stop.name, style = twText(26f, FontWeight.ExtraBold, 34.sp, -0.02f), color = NudgeColors.OnBackground)
        Text("Scheduled ${stop.time}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      }
      if (trip.currentStopIndex > 0) Column(
        Modifier.clip(RoundedCornerShape(20.dp)).tappable(enabled = !busy) { onMoveStop(-1) }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Symbol("undo", size = 20.dp, tint = NudgeColors.OnSurfaceVariant)
        Text("Previous", style = twText(11f, FontWeight.SemiBold, 14.sp), color = NudgeColors.OnSurfaceVariant)
      }
    }
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "here") { SectionLabel("Boarding here", Modifier.padding(start = 8.dp, top = 8.dp)) }
      if (atStop.isEmpty()) item(key = "none") { Text("No pickups at this stop.", style = NudgeType.BodyLg, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(8.dp)) }
      items(atStop, key = { it.id }) { PassengerCard(it, Modifier.animateItem(), onBoard, onNoShow, onCall, onUndo) }
      if (missed.isNotEmpty()) {
        item(key = "missed") { SectionLabel("Not boarded yet · earlier stops", Modifier.padding(start = 8.dp, top = 16.dp)) }
        items(missed, key = { "m" + it.id }) { PassengerCard(it, Modifier.animateItem(), onBoard, onNoShow, onCall, onUndo, showStop = true) }
      }
      // Already marked at earlier stops: kept visible so a wrong tap can be undone.
      val done = trip.passengers.filter { it.status != BoardingStatus.Waiting && indexOf(it) in 0 until trip.currentStopIndex }
      if (done.isNotEmpty()) {
        item(key = "done") { SectionLabel("Marked at earlier stops", Modifier.padding(start = 8.dp, top = 16.dp)) }
        items(done, key = { "d" + it.id }) { PassengerCard(it, Modifier.animateItem(), onBoard, onNoShow, onCall, onUndo, showStop = true) }
      }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius32).hairline(NudgeColors.OutlineVariant, Radius32).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Symbol("groups", tint = NudgeColors.Primary, filled = true)
          Text("$boarded of ${trip.passengers.size} boarded", style = twText(16f, FontWeight.Bold, 24.sp), color = NudgeColors.OnSurface)
        }
        if (!last) Text("Next · ${trip.stops[trip.currentStopIndex + 1].time}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      }
      PrimaryButton(
        if (last) "End trip" else "Next stop",
        { if (last) confirmEnd = true else if (waitingHere > 0) confirmNext = true else onMoveStop(1) },
        Modifier.fillMaxWidth(), icon = "arrow_forward", enabled = !busy,
      )
      if (!last) Text(
        "End trip early", style = twText(13f, FontWeight.SemiBold, 18.sp), color = NudgeColors.OnSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).tappable { confirmEnd = true }.padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun PassengerCard(
  p: TripPassenger, modifier: Modifier, onBoard: (TripPassenger) -> Unit, onNoShow: (TripPassenger) -> Unit, onCall: (TripPassenger) -> Unit,
  onUndo: (TripPassenger) -> Unit, showStop: Boolean = false,
) {
  val done = p.status != BoardingStatus.Waiting
  val bg by animateColorAsState(if (done) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.06f), tween(240), label = "bg")
  Column(
    modifier.fillMaxWidth().alpha(if (done) 0.7f else 1f).clip(Radius32).background(bg).hairline(Color.White.copy(alpha = 0.1f), Radius32).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      InitialsAvatar(p.name, 48.dp, Modifier.size(48.dp).clip(CircleShape))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(p.name, style = twText(16f, FontWeight.Bold, 24.sp), color = NudgeColors.OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          when (p.status) {
            BoardingStatus.Waiting -> if (showStop) "Waiting at ${p.stopName}" else "Waiting"
            BoardingStatus.Boarded -> "Boarded ${p.boardedAt.orEmpty()} · tap Undo if wrong".replace("  ", " ")
            BoardingStatus.NoShow -> "No-show · tap Undo if wrong"
          },
          style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant,
        )
        RiderTags(p)
      }
      if (!done) {
        Text(
          "BOARD", style = twText(14f, FontWeight.Bold, 20.sp, 0.05f), color = NudgeColors.OnPrimary,
          modifier = Modifier.clip(CircleShape).background(NudgeColors.Primary).tappable(pressedScale = 0.94f) { onBoard(p) }.padding(horizontal = 20.dp, vertical = 12.dp),
        )
      } else Text(
        "UNDO", style = twText(13f, FontWeight.Bold, 18.sp, 0.05f), color = NudgeColors.Primary,
        modifier = Modifier.clip(CircleShape).tappable { onUndo(p) }.padding(horizontal = 12.dp, vertical = 10.dp),
      )
    }
    if (!done) Row(Modifier.padding(start = 64.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      SmallAction("call", "Call", NudgeColors.Primary) { onCall(p) }
      SmallAction("person_off", "Mark no-show", NudgeColors.OnSurfaceVariant) { onNoShow(p) }
    }
  }
}

@Composable
private fun SmallAction(icon: String, label: String, tint: Color, onClick: () -> Unit) {
  Row(
    Modifier.clip(CircleShape).tappable(pressedScale = 0.95f, onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Symbol(icon, size = 16.dp, tint = tint)
    Text(label, style = twText(13f, FontWeight.SemiBold, 18.sp), color = tint)
  }
}

/** "One-way" and "To verify" (payment not yet confirmed by the office) so the driver knows whom to check. */
@Composable
fun RiderTags(p: TripPassenger) {
  if (!p.oneWay && !p.paymentPending) return
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    if (p.oneWay) StatusChip("ONE-WAY", NudgeColors.PrimaryContainer.copy(alpha = 0.2f), NudgeColors.Primary)
    if (p.paymentPending) StatusChip("TO VERIFY", Color(0xFFFCD34D).copy(alpha = 0.14f), Color(0xFFFCD34D))
  }
}
