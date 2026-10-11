package com.nudge.app.ui.admin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.RouteInfo
import com.nudge.app.ui.components.InitialsAvatar
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius32 = RoundedCornerShape(32.dp)

@Composable
fun AdminRoutesScreen(
  routes: List<RouteInfo>,
  onToggle: (RouteInfo, Boolean) -> Unit,
  onAdd: () -> Unit,
  modifier: Modifier = Modifier,
  onEdit: (RouteInfo) -> Unit = {},
) {
  var filter by remember { mutableStateOf(0) }
  val chips = listOf("All (${routes.size})", "Active (${routes.count { it.active }})", "Inactive (${routes.count { !it.active }})")
  val shown = when (filter) { 1 -> routes.filter { it.active }; 2 -> routes.filter { !it.active }; else -> routes }
  Box(modifier.fillMaxSize().background(NudgeColors.Background)) {
    Column(Modifier.fillMaxSize()) {
      ScreenHeader("Routes")
      Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        chips.forEachIndexed { i, c ->
          val on = i == filter
          val bg by animateColorAsState(if (on) NudgeColors.PrimaryContainer else NudgeColors.SurfaceContainer, tween(220), label = "chip")
          Text(
            c, style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), maxLines = 1, softWrap = false,
            color = if (on) NudgeColors.OnPrimaryContainer else NudgeColors.OnSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(bg).tappable(pressedScale = 0.95f) { filter = i }.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }
      LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(shown, key = { it.id }) { RouteCard(it, Modifier.animateItem().tappable(pressedScale = 0.99f) { onEdit(it) }) { v -> onToggle(it, v) } }
      }
    }
    Box(
      Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp).shadow(16.dp, CircleShape).size(60.dp)
        .background(NudgeColors.PrimaryContainer, CircleShape).tappable(pressedScale = 0.92f, onClick = onAdd),
      contentAlignment = Alignment.Center,
    ) { Symbol("add", size = 28.dp, tint = NudgeColors.OnPrimaryContainer) }
  }
}

@Composable
private fun RouteCard(r: RouteInfo, modifier: Modifier, onToggle: (Boolean) -> Unit) {
  Column(
    modifier.fillMaxWidth().background(Color(0xFF1A1A1A), Radius32).hairline(Color.White.copy(alpha = 0.08f), Radius32).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(48.dp).background(NudgeColors.Primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Symbol("airport_shuttle", tint = NudgeColors.Primary, filled = true) }
      Column(Modifier.weight(1f)) {
        Text(r.name, style = twText(16f, FontWeight.Bold, 24.sp), color = NudgeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${r.morning.size} stops · ${r.durationMins} min · ${if (r.vehicleId == null) "no shuttle" else r.shuttleCode}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      }
      SwitchPill(r.active, onToggle)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (r.driverName != null) InitialsAvatar(r.driverName, 28.dp, Modifier.size(28.dp).clip(CircleShape))
        else Box(Modifier.size(28.dp).background(NudgeColors.SurfaceContainerHigh, CircleShape).hairline(), contentAlignment = Alignment.Center) { Symbol("person", size = 14.dp) }
        Text(r.driverName ?: "No driver yet", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      }
      Text("${r.morningTime} · ${r.eveningTime}", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.Primary)
    }
  }
}

/** w-11 h-6 toggle with an animated thumb. */
@Composable
private fun SwitchPill(on: Boolean, onChange: (Boolean) -> Unit) {
  val bg by animateColorAsState(if (on) NudgeColors.PrimaryContainer else NudgeColors.SurfaceContainerHighest, tween(220), label = "track")
  val x by animateDpAsState(if (on) 22.dp else 2.dp, spring(stiffness = 600f), label = "thumb")
  Box(Modifier.width(48.dp).height(28.dp).clip(CircleShape).background(bg).tappable(pressedScale = 0.95f) { onChange(!on) }) {
    Box(Modifier.offset(x = x, y = 2.dp).size(24.dp).background(Color.White, CircleShape))
  }
}
