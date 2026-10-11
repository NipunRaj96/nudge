package com.nudge.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.FleetStats
import com.nudge.app.data.Vehicle
import com.nudge.app.data.VehicleState
import com.nudge.app.ui.components.LiveMap
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
private val Green400 = Color(0xFF4ADE80)
private val Green900a = Color(0xFF14532D).copy(alpha = 0.35f)

@Composable
fun AdminFleetScreen(stats: FleetStats, vehicles: List<Vehicle>, onVehicle: (Vehicle) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Fleet")
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Active trips", stats.activeTrips.toString(), NudgeColors.Primary, Modifier.weight(1f))
        Stat("Bookings today", stats.bookingsToday.toString(), NudgeColors.OnSurface, Modifier.weight(1f))
      }
      Column(
        Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainer, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Shuttles available", style = NudgeType.BodyLg, color = NudgeColors.OnSurfaceVariant)
          Text("${stats.fleetUp}/${stats.fleetTotal}", style = twText(20f, FontWeight.Bold, 28.sp), color = NudgeColors.OnSurface)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
          Box(Modifier.fillMaxWidth(if (stats.fleetTotal > 0) stats.fleetUp.toFloat() / stats.fleetTotal else 0f).height(4.dp).background(NudgeColors.Primary))
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          SectionLabel("Live map")
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(NudgeColors.Primary, CircleShape))
            Text("LIVE", style = NudgeType.LabelCaps, color = NudgeColors.Primary)
          }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).clip(Radius32).hairline(Color.White.copy(alpha = 0.1f), Radius32)) {
          LiveMap(Modifier.fillMaxSize(), vehicles = vehicles.mapNotNull { it.position }, topInsetDp = 40, interactive = false)
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Shuttles", Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        vehicles.forEach { VehicleRow(it) { onVehicle(it) } }
      }
    }
  }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
  Column(
    modifier.background(NudgeColors.SurfaceContainer, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(label, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 1)
    Text(value, style = NudgeType.DisplayLg, color = color)
  }
}


@Composable
private fun VehicleRow(v: Vehicle, onClick: () -> Unit) {
  val (iconBg, iconFg, icon) = when (v.state) {
    VehicleState.OnRoute -> Triple(Color.White.copy(alpha = 0.05f), NudgeColors.Primary, "directions_bus")
    VehicleState.Delayed -> Triple(NudgeColors.ErrorContainer.copy(alpha = 0.3f), NudgeColors.Error, "warning")
    VehicleState.Standby -> Triple(Color.White.copy(alpha = 0.05f), NudgeColors.OnSurfaceVariant, "local_parking")
  }
  val (chipBg, chipFg) = when (v.state) {
    VehicleState.OnRoute -> Green900a to Green400
    VehicleState.Delayed -> NudgeColors.ErrorContainer to NudgeColors.OnErrorContainer
    VehicleState.Standby -> NudgeColors.SecondaryContainer to NudgeColors.OnSecondaryContainer
  }
  Row(
    Modifier.fillMaxWidth().clip(Radius32).background(NudgeColors.SurfaceContainer).hairline(Color.White.copy(alpha = 0.05f), Radius32).tappable(pressedScale = 0.98f, onClick = onClick).padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(48.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) { Symbol(icon, tint = iconFg, filled = true) }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(v.code, style = twText(16f, FontWeight.SemiBold, 24.sp), color = NudgeColors.OnSurface)
      Text(v.description, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 1)
    }
    StatusChip(v.chip, chipBg, chipFg)
  }
}
