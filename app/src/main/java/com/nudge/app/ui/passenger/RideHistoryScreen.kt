package com.nudge.app.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.Pass
import com.nudge.app.data.PassStatus
import com.nudge.app.data.Ride
import com.nudge.app.data.RideStatus
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
private val Green = Color(0xFF4ADE80)
private val Red = Color(0xFFF87171)
private val Amber = Color(0xFFFCD34D)

@Composable
fun RideHistoryScreen(pass: Pass, rides: List<Ride>, onRenew: () -> Unit, onOpen: (Ride) -> Unit, modifier: Modifier = Modifier) {
  val upcoming = rides.filter { it.status == RideStatus.Upcoming }
  val past = rides.filter { it.status != RideStatus.Upcoming }
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Rides")
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { PassCard(pass, onRenew) }
      if (upcoming.isNotEmpty()) {
        item { SectionLabel("Upcoming", Modifier.padding(start = 8.dp, top = 16.dp)) }
        items(upcoming, key = { it.id }) { RideCard(it, Modifier.animateItem(), onOpen) }
      }
      item { SectionLabel("Past rides", Modifier.padding(start = 8.dp, top = 16.dp)) }
      if (past.isEmpty()) item { Text("Completed rides will show up here.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(8.dp)) }
      items(past, key = { it.id }) { RideCard(it, Modifier.animateItem(), onOpen) }
    }
  }
}

@Composable
private fun PassCard(pass: Pass, onRenew: () -> Unit) {
  val total = pass.ridesTotal
  val progress by animateFloatAsState(
    if (total != null && pass.ridesLeft != null) 1f - pass.ridesLeft.toFloat() / total else 0f, tween(500), label = "used",
  )
  Column(
    Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius32).hairline(Color.White.copy(alpha = 0.06f), Radius32).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    when (pass.status) {
      PassStatus.None -> {
        Text("No active pass", style = twText(18f, FontWeight.SemiBold, 28.sp), color = NudgeColors.OnSurface)
        Text("Get a monthly pass, or pay for a single ride when you book.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
        Text("Choose a plan", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.Primary, modifier = Modifier.tappable(onClick = onRenew))
      }
      PassStatus.PendingApproval -> {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text(pass.plan?.title ?: "Pass", style = twText(18f, FontWeight.SemiBold, 28.sp), color = NudgeColors.OnSurface, modifier = Modifier.weight(1f))
          StatusChip("IN REVIEW", Amber.copy(alpha = 0.14f), Amber, icon = "hourglass_top")
        }
        Text(
          if (pass.canBook) "You can book already. The operator is checking your payment." else "You can book once the operator confirms your payment.",
          style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant,
        )
      }
      PassStatus.Active -> {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text(pass.plan?.title ?: "Pass", style = twText(18f, FontWeight.SemiBold, 28.sp), color = NudgeColors.OnSurface)
            Text("Valid until ${pass.validUntil}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
          }
          StatusChip(if (pass.ridesLeft != null) "${pass.ridesLeft} left" else "Unlimited", NudgeColors.PrimaryContainer.copy(alpha = 0.2f), NudgeColors.Primary)
        }
        if (total != null) Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
          Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(NudgeColors.Primary))
        }
        Text("Renew pass", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.Primary, modifier = Modifier.tappable(onClick = onRenew))
      }
    }
  }
}

@Composable
private fun RideCard(r: Ride, modifier: Modifier, onOpen: (Ride) -> Unit) {
  val (label, bg, fg, icon) = when (r.status) {
    RideStatus.Upcoming -> if (r.paymentPending) Quad("IN REVIEW", Amber.copy(alpha = 0.14f), Amber, "hourglass_top")
      else Quad("UPCOMING", NudgeColors.PrimaryContainer.copy(alpha = 0.2f), NudgeColors.Primary, "schedule")
    RideStatus.Completed -> Quad("COMPLETED", Green.copy(alpha = 0.12f), Green, "check_circle")
    RideStatus.NoShow -> Quad("NO-SHOW", Red.copy(alpha = 0.12f), Red, "error")
    RideStatus.Cancelled -> Quad("CANCELLED", Color.White.copy(alpha = 0.08f), NudgeColors.OnSurfaceVariant, "cancel")
  }
  Row(
    modifier.fillMaxWidth().clip(Radius32).background(NudgeColors.SurfaceContainerLow).hairline(Color.White.copy(alpha = 0.05f), Radius32)
      .tappable(pressedScale = 0.98f) { onOpen(r) }.padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        r.routeName, style = twText(16f, FontWeight.SemiBold, 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (r.status == RideStatus.Cancelled) NudgeColors.OnSurfaceVariant else NudgeColors.OnSurface,
        textDecoration = if (r.status == RideStatus.Cancelled) TextDecoration.LineThrough else null,
      )
      Text("${r.dateLabel} · ${r.time}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 1)
      Text(r.pickup, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    StatusChip(label, bg, fg, icon = icon)
  }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: String)
