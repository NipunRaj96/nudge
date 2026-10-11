package com.nudge.app.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.DriverTrip
import com.nudge.app.ui.components.GlowCircle
import com.nudge.app.ui.components.InitialsAvatar
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.SectionLabel
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius32 = RoundedCornerShape(32.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriverPreTripScreen(trip: DriverTrip, onStartTrip: () -> Unit, modifier: Modifier = Modifier, onCall: (com.nudge.app.data.TripPassenger) -> Unit = {}) {
  val byStop = trip.stops.map { stop -> stop to trip.passengers.filter { it.stopName == stop.name } }.filter { it.second.isNotEmpty() }
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Today's trip")
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Box(Modifier.fillMaxWidth().clip(Radius32).background(Color(0xFF1A1A1A)).hairline(Color.White.copy(alpha = 0.08f), Radius32)) {
          GlowCircle(128.dp, NudgeColors.PrimaryContainer.copy(alpha = 0.2f), Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp))
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("SHUTTLE ${trip.routeCode}", style = NudgeType.LabelCaps, color = NudgeColors.Primary)
              Text(trip.routeName, style = NudgeType.HeadlineMd, color = NudgeColors.OnBackground)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Chip("schedule", trip.departure)
              Chip("group", "${trip.passengers.size} rider${if (trip.passengers.size == 1) "" else "s"}")
              Chip("directions_bus", trip.shuttleCode)
            }
          }
        }
      }
      item { SectionLabel("Passengers by stop", Modifier.padding(start = 8.dp, top = 24.dp, bottom = 4.dp)) }
      if (byStop.isEmpty()) item(key = "empty") {
        Text("No riders booked yet. Pull down to refresh.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
      }
      byStop.forEach { (stop, riders) ->
        item(key = "h-${stop.name}") {
          Row(Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol("location_on", size = 16.dp, tint = NudgeColors.Primary)
            Text("${stop.name} · ${stop.time}", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnSurface)
          }
        }
        items(riders, key = { it.id }) { r ->
          Row(
            Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
          ) {
            InitialsAvatar(r.name, 40.dp, Modifier.size(40.dp).clip(CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(r.name, style = NudgeType.BodyLg, color = NudgeColors.OnSurface, maxLines = 1)
              RiderTags(r)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(NudgeColors.SurfaceContainerHighest).tappable { onCall(r) }, contentAlignment = Alignment.Center) {
              Symbol("call", size = 18.dp, tint = NudgeColors.Primary)
            }
          }
        }
      }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
      PrimaryButton("Start trip", onStartTrip, Modifier.fillMaxWidth(), icon = "arrow_forward")
    }
  }
}

@Composable
private fun Chip(icon: String, text: String) {
  Row(
    Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape).hairline().padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Symbol(icon, size = 18.dp, tint = NudgeColors.Primary)
    Text(text, style = NudgeType.BodySm, color = NudgeColors.OnSurface)
  }
}
