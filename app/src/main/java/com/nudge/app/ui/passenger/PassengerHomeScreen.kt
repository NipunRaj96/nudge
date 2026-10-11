package com.nudge.app.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nudge.app.data.RouteInfo
import com.nudge.app.data.Ride
import com.nudge.app.ui.components.GlowCircle
import com.nudge.app.ui.components.InitialsAvatar
import com.nudge.app.ui.components.SectionLabel
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius40 = RoundedCornerShape(40.dp)
private val Radius32 = RoundedCornerShape(32.dp)

@Composable
fun PassengerHomeScreen(
  userName: String,
  routes: List<RouteInfo>,
  upcoming: Ride?,
  onSearch: (String) -> Unit,
  onRouteClick: (RouteInfo) -> Unit,
  onBookNow: () -> Unit,
  onLiveTrack: () -> Unit,
  onOpenTicket: (Ride) -> Unit,
  onProfile: () -> Unit,
  modifier: Modifier = Modifier,
  /** e.g. a rejected payment: shown on top so the rider knows why seats vanished. */
  notice: String? = null,
) {
  Column(modifier.fillMaxSize().background(NudgeColors.Background).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text("Nudge", style = NudgeType.HeadlineMd.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em), color = NudgeColors.OnBackground)
        Text("HELLO, ${userName.substringBefore(" ").uppercase()}", style = twText(10f, FontWeight.Bold, 15.sp, 0.1f), color = NudgeColors.Primary.copy(alpha = 0.7f))
      }
      InitialsAvatar(
        userName, 40.dp,
        Modifier.size(40.dp).clip(CircleShape).border(2.dp, NudgeColors.Primary, CircleShape).tappable(onClick = onProfile),
      )
    }

    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
      if (notice != null) Row(
        Modifier.fillMaxWidth().clip(Radius32).background(NudgeColors.Error.copy(alpha = 0.12f)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
      ) {
        Symbol("error", size = 20.dp, tint = NudgeColors.Error)
        Text(notice, style = NudgeType.BodySm, color = NudgeColors.OnSurface)
      }
      Hero(onSearch)

      if (routes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SectionLabel("Your routes", Modifier.padding(horizontal = 8.dp))
          routes.forEach { RoutePill(it) { onRouteClick(it) } }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Upcoming journey", Modifier.padding(horizontal = 8.dp))
        if (upcoming != null) UpcomingCard(upcoming, onLiveTrack, onOpenTicket) else EmptyUpcoming(onBookNow)
      }
    }
  }
}

@Composable
private fun Hero(onSearch: (String) -> Unit) {
  var query by remember { mutableStateOf("") }
  Column(
    Modifier
      .fillMaxWidth()
      .shadow(24.dp, Radius40, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
      .background(Color.White, Radius40)
      .heightIn(min = 280.dp)
      .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
  ) {
    Text(
      "Where are you commuting today?",
      style = NudgeType.DisplayLg.copy(lineHeight = 45.sp),
      color = NudgeColors.Background,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(max = 280.dp),
    )
    Box(Modifier.widthIn(max = 384.dp).fillMaxWidth().height(64.dp)) {
      Box(Modifier.fillMaxSize().background(NudgeColors.SurfaceContainerLowest, CircleShape).padding(start = 56.dp, end = 64.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
          value = query, onValueChange = { query = it }, singleLine = true,
          textStyle = NudgeType.BodyLg.copy(color = NudgeColors.OnSurface),
          cursorBrush = SolidColor(NudgeColors.Primary),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
          modifier = Modifier.fillMaxWidth(),
          decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
              if (query.isEmpty()) Text("Search a stop", style = NudgeType.BodyLg, color = Color(0xFF6B7280), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
              inner()
            }
          },
        )
      }
      Symbol("search", size = 24.dp, tint = NudgeColors.Outline, modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp))
      Box(
        Modifier
          .align(Alignment.CenterEnd).padding(end = 8.dp)
          .shadow(10.dp, CircleShape, ambientColor = NudgeColors.Primary.copy(alpha = 0.2f), spotColor = NudgeColors.Primary.copy(alpha = 0.2f))
          .size(48.dp).background(NudgeColors.Primary, CircleShape).tappable(pressedScale = 0.92f) { onSearch(query) },
        contentAlignment = Alignment.Center,
      ) { Symbol("near_me", size = 24.dp, tint = NudgeColors.OnPrimary, filled = true) }
    }
  }
}

@Composable
private fun RoutePill(route: RouteInfo, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(Radius32)
      .background(NudgeColors.SurfaceContainerLow)
      .hairline(Color.White.copy(alpha = 0.05f), Radius32)
      .tappable(pressedScale = 0.98f, onClick = onClick)
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(48.dp).background(NudgeColors.SurfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
      Symbol("airport_shuttle", size = 24.dp, tint = NudgeColors.Primary)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(route.name, style = twText(16f, FontWeight.SemiBold, 24.sp), color = NudgeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("${route.morningTime} · ${route.eveningTime}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Symbol("chevron_right", tint = NudgeColors.OnSurfaceVariant)
  }
}

@Composable
private fun UpcomingCard(ride: Ride, onLiveTrack: () -> Unit, onOpenTicket: (Ride) -> Unit) {
  val (time, meridiem) = ride.time.split(" ").let { it.first() to it.getOrElse(1) { "" } }
  Box(Modifier.fillMaxWidth().clip(Radius40).background(NudgeColors.PrimaryContainer)) {
    GlowCircle(128.dp, NudgeColors.Primary.copy(alpha = 0.22f), Modifier.align(Alignment.TopEnd).offset(x = 32.dp, y = (-32).dp))
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      // Time sits beside the meta line; the route gets the full card width so it never wraps word-by-word.
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
          Row(Modifier.weight(1f).padding(end = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol("airport_shuttle", size = 16.dp, tint = NudgeColors.OnPrimaryContainer, filled = true)
            Text("${ride.routeName} · ${ride.dateLabel}", style = NudgeType.BodySm, color = NudgeColors.OnPrimaryContainer.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(time, style = NudgeType.DisplayLg.copy(lineHeight = 40.sp), color = NudgeColors.OnPrimaryContainer, maxLines = 1)
            Text(meridiem, style = NudgeType.LabelCaps, color = NudgeColors.OnPrimaryContainer.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 6.dp))
          }
        }
        Text(ride.pickup, style = twText(20f, FontWeight.SemiBold, 28.sp), color = NudgeColors.OnPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (ride.paymentPending) Text("Seat held · payment in review", style = NudgeType.BodySm, color = NudgeColors.OnPrimaryContainer.copy(alpha = 0.85f))
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
          Modifier.weight(1f).clip(CircleShape).background(NudgeColors.OnPrimaryContainer).tappable(onClick = onLiveTrack).padding(vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
          Symbol("map", size = 18.dp, tint = NudgeColors.PrimaryContainer)
          Text("Live track", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.PrimaryContainer)
        }
        Row(
          Modifier.weight(1f).clip(CircleShape).hairline(NudgeColors.OnPrimaryContainer.copy(alpha = 0.5f)).tappable { onOpenTicket(ride) }.padding(vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
          Symbol("qr_code_2", size = 18.dp, tint = NudgeColors.OnPrimaryContainer)
          Text("Boarding pass", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnPrimaryContainer)
        }
      }
    }
  }
}

@Composable
private fun EmptyUpcoming(onBookNow: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius40).hairline(Color.White.copy(alpha = 0.05f), Radius40).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(Modifier.size(56.dp).background(NudgeColors.SurfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Symbol("event_available", size = 28.dp, tint = NudgeColors.Primary) }
    Text("No upcoming rides", style = twText(18f, FontWeight.SemiBold, 28.sp), color = NudgeColors.OnSurface)
    Text("Pick a route and a time to reserve your seat.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, textAlign = TextAlign.Center)
    Text(
      "Book a ride", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnPrimaryContainer,
      modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(NudgeColors.PrimaryContainer).tappable(onClick = onBookNow).padding(horizontal = 24.dp, vertical = 12.dp),
    )
  }
}
