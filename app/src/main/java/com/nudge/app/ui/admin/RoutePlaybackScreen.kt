package com.nudge.app.ui.admin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.components.CircleIconButton
import com.nudge.app.data.Position
import com.nudge.app.data.Stop
import com.nudge.app.data.distanceKm
import com.nudge.app.ui.components.LiveMap
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.glass
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

/**
 * Operator replay of a trip from its recorded GPS points ([track], oldest first). Full replay takes 40 s at 1x.
 */
@Composable
fun RoutePlaybackScreen(shuttleCode: String, routeName: String, dateLabel: String, track: List<Position>, stops: List<Stop>, road: List<Pair<Double, Double>>?, onBack: () -> Unit, modifier: Modifier = Modifier) {
  var progress by remember { mutableFloatStateOf(0f) }
  var playing by remember { mutableStateOf(false) }
  var speed by remember { mutableStateOf(1) }
  val hasTrack = track.size >= 2

  LaunchedEffect(playing, speed) {
    var last = withFrameNanos { it }
    while (playing) {
      val now = withFrameNanos { it }
      progress = (progress + (now - last) / 1_000_000_000f * 0.025f * speed).coerceAtMost(1f)
      last = now
      if (progress >= 1f) playing = false
    }
  }
  val start = track.firstOrNull()?.atMillis ?: 0L
  val end = track.lastOrNull()?.atMillis ?: 0L
  val at = start + ((end - start) * progress).toLong()
  val shown = remember(track, at) { trailUntil(track, at) }
  val distance = remember(track) { track.zipWithNext { a, b -> distanceKm(a.lat, a.lng, b.lat, b.lng) }.sum() }
  val topSpeed = remember(track) { track.mapNotNull { it.speedKmh }.maxOrNull() }
  val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
  fun fmt(ms: Long) = if (!hasTrack) "--:--" else java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Kolkata")).format(clock)

  Box(modifier.fillMaxSize().background(Color.Black)) {
    LiveMap(stops, Modifier.fillMaxSize(), trail = shown, vehicles = listOfNotNull(shown.lastOrNull()), topInsetDp = 100, road = road)
    Row(
      Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(48.dp).clip(CircleShape).glass(CircleShape).tappable(onClick = onBack), contentAlignment = Alignment.Center) { Symbol("arrow_back", tint = NudgeColors.OnSurface) }
      Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$shuttleCode · $dateLabel", style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), color = NudgeColors.Primary, maxLines = 1)
        Text(routeName, style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnSurface, maxLines = 1)
      }
    }

    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(24.dp, sheet).background(NudgeColors.SurfaceContainerLow, sheet)
        .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Box(Modifier.align(Alignment.CenterHorizontally).width(48.dp).height(6.dp).background(NudgeColors.SurfaceContainerHighest, CircleShape))
      Column {
        if (!hasTrack) Text("No GPS was recorded for this trip yet.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        Slider(
          value = progress, onValueChange = { progress = it; playing = false }, enabled = hasTrack,
          colors = SliderDefaults.colors(thumbColor = NudgeColors.Primary, activeTrackColor = NudgeColors.PrimaryContainer, inactiveTrackColor = NudgeColors.SurfaceContainerHighest),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(fmt(start), style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
          Text(fmt(at), style = twText(14f, FontWeight.Bold, 20.sp), color = NudgeColors.Primary)
          Text(fmt(end), style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(64.dp).alpha(if (hasTrack) 1f else 0.4f).shadow(8.dp, CircleShape).background(NudgeColors.PrimaryContainer, CircleShape).tappable(enabled = hasTrack, pressedScale = 0.92f) {
            if (progress >= 1f) progress = 0f
            playing = !playing
          },
          contentAlignment = Alignment.Center,
        ) { Symbol(if (playing) "pause" else "play_arrow", size = 32.dp, tint = NudgeColors.OnPrimaryContainer, filled = true) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(1, 2, 4).forEach { s ->
            val on = s == speed
            val bg by animateColorAsState(if (on) NudgeColors.Primary else NudgeColors.SurfaceContainerHigh, tween(200), label = "speed")
            Text(
              "${s}x", style = twText(14f, FontWeight.Bold, 20.sp), color = if (on) NudgeColors.OnPrimary else NudgeColors.OnSurfaceVariant,
              modifier = Modifier.clip(CircleShape).background(bg).tappable(pressedScale = 0.94f) { speed = s }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Distance", if (hasTrack) "%.1f km".format(distance) else "—", Modifier.weight(1f))
        Stat("Top speed", topSpeed?.let { "${it.toInt()} km/h" } ?: "—", Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
  Column(modifier.background(NudgeColors.SurfaceContainerHigh, RoundedCornerShape(24.dp)).hairline(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
    Text(value, style = twText(20f, FontWeight.Bold, 28.sp), color = NudgeColors.OnSurface)
  }
}

/** Recorded points up to time [at], ending with an interpolated point so the shuttle glides between fixes. */
private fun trailUntil(track: List<Position>, at: Long): List<Position> {
  val i = track.indexOfLast { it.atMillis <= at }
  if (i < 0) return track.take(1)
  if (i == track.lastIndex) return track
  val a = track[i]
  val b = track[i + 1]
  val f = ((at - a.atMillis).toDouble() / (b.atMillis - a.atMillis).coerceAtLeast(1)).coerceIn(0.0, 1.0)
  return track.subList(0, i + 1) + Position(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f, a.speedKmh, at)
}
