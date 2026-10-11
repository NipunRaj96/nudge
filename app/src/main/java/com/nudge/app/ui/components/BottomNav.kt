package com.nudge.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeColors

data class NavItem(val icon: String, val label: String)

val PassengerNav = listOf(NavItem("home", "Home"), NavItem("confirmation_number", "Book"), NavItem("receipt_long", "Rides"), NavItem("person", "Profile"))
val DriverNav = listOf(NavItem("route", "Trip"), NavItem("person", "Profile"))
val AdminNav = listOf(NavItem("space_dashboard", "Fleet"), NavItem("route", "Routes"), NavItem("payments", "Payments"), NavItem("person", "Profile"))

/** The single app-wide bottom nav (Stitch's labelled style: 32dp top corners, tinted active pill, 12/600 labels). */
@Composable
fun AppBottomNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
  Row(
    modifier
      .fillMaxWidth()
      .clip(shape)
      .background(NudgeColors.SurfaceContainerLowest)
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEachIndexed { i, item -> NavButton(item, i == selected) { onSelect(i) } }
  }
}

@Composable
private fun RowScope.NavButton(item: NavItem, active: Boolean, onClick: () -> Unit) {
  val pill by animateColorAsState(if (active) NudgeColors.PrimaryContainer.copy(alpha = 0.14f) else Color.Transparent, tween(220), label = "pill")
  val tint by animateColorAsState(if (active) NudgeColors.Primary else NudgeColors.OnSurfaceVariant, tween(220), label = "tint")
  Column(
    Modifier
      .weight(1f)
      .widthIn(max = 96.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(pill)
      .tappable(pressedScale = 0.94f, onClick = onClick)
      .padding(vertical = 8.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Symbol(item.icon, size = 24.dp, tint = tint, filled = active)
    Text(item.label, style = twText(12f, if (active) FontWeight.Bold else FontWeight.SemiBold, 16.sp, 0.02f), color = tint, maxLines = 1)
  }
}
