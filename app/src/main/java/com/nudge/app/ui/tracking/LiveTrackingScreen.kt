package com.nudge.app.ui.tracking

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.nudge.app.ui.components.GlassCircleButton
import com.nudge.app.ui.components.InitialsAvatar
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.glass
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeShapes
import com.nudge.app.ui.theme.NudgeType

data class TrackingState(
  val arrivingText: String,
  val driverName: String,
  val driverTag: String,
  val shuttleId: String,
  val distanceKm: String,
  val avgSpeedKmh: String,
)

private val Radius32 = RoundedCornerShape(32.dp)

@Composable
fun LiveTrackingScreen(
  state: TrackingState,
  onBack: () -> Unit,
  onHelp: () -> Unit,
  onCall: () -> Unit,
  onMessage: () -> Unit,
  modifier: Modifier = Modifier,
  map: @Composable (Modifier) -> Unit,
) {
  Box(modifier.fillMaxSize().background(Color.Black)) {
    map(Modifier.fillMaxSize())

    // Header overlay (px-margin-main py-6)
    Row(
      Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp, vertical = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GlassCircleButton("arrow_back", "Back", onBack)
      val pulse by rememberInfiniteTransition(label = "dot").animateFloat(1f, 0.5f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "dot")
      Row(
        Modifier.clip(CircleShape).glass(CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(8.dp).alpha(pulse).background(NudgeColors.Primary, CircleShape))
        Text("LIVE TRACKING", style = NudgeType.LabelCaps.copy(letterSpacing = 0.1.em), color = NudgeColors.OnSurface)
      }
      GlassCircleButton("help", "Help", onHelp)
    }

    // Bottom sheet
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
        Row(
          Modifier
            .shadow(16.dp, CircleShape)
            .background(NudgeColors.PrimaryContainer, CircleShape)
            .hairline()
            .padding(horizontal = 24.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Symbol("airport_shuttle", size = 18.dp, tint = NudgeColors.OnPrimaryContainer)
          Text(state.arrivingText, style = NudgeType.LabelCaps, color = NudgeColors.OnPrimaryContainer)
        }
      }
      val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
      Column(
        Modifier
          .fillMaxWidth()
          .shadow(24.dp, sheetShape)
          .background(NudgeColors.SurfaceContainerLow, sheetShape)
          .border(1.dp, Color.White.copy(alpha = 0.05f), sheetShape)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
      ) {
        Box(
          Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp).width(48.dp).height(6.dp)
            .background(NudgeColors.SurfaceContainerHighest, CircleShape),
        )
        // Driver + shuttle id
        Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
              InitialsAvatar(state.driverName, 64.dp, Modifier.size(64.dp).clip(CircleShape).border(2.dp, NudgeColors.SurfaceContainerHighest, CircleShape))
              Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)
                  .background(NudgeColors.Primary, CircleShape).border(2.dp, NudgeColors.SurfaceContainerLow, CircleShape).padding(4.dp),
              ) { Symbol("star", size = 14.dp, tint = NudgeColors.OnPrimary, filled = true) }
            }
            Column(Modifier.weight(1f)) {
              Text(state.driverName, style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Symbol("verified", size = 16.dp, tint = NudgeColors.OnSurfaceVariant)
                Text(state.driverTag, style = NudgeType.LabelCaps, color = NudgeColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
          Column(
            Modifier.widthIn(min = 80.dp).background(NudgeColors.SurfaceContainerHighest, Radius32)
              .hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text("SHUTTLE ID", style = NudgeType.LabelCaps, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            Text(state.shuttleId, style = twText(16f, FontWeight.Bold, 24.sp), color = NudgeColors.Primary)
          }
        }
        // Bento stats
        Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Stat("distance", "Distance", state.distanceKm, "km", Modifier.weight(1f))
          Stat("speed", "Speed", state.avgSpeedKmh, "km/h", Modifier.weight(1f))
        }
        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Row(
            Modifier.weight(1f).clip(NudgeShapes.Full).background(NudgeColors.Primary).tappable(onClick = onCall).padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Symbol("call", tint = NudgeColors.OnPrimary)
            Text("Call Driver", style = twText(16f, FontWeight.Bold, 24.sp), color = NudgeColors.OnPrimary)
          }
          Box(
            Modifier.size(64.dp).clip(CircleShape).background(NudgeColors.SurfaceContainerHighest).hairline(Color.White.copy(alpha = 0.05f)).tappable(onClick = onMessage),
            contentAlignment = Alignment.Center,
          ) { Symbol("message", tint = NudgeColors.OnSurface) }
        }
      }
    }
  }
}

@Composable
private fun Stat(icon: String, label: String, value: String, unit: String, modifier: Modifier) {
  Column(
    modifier.background(NudgeColors.SurfaceContainerHigh, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Symbol(icon, tint = NudgeColors.Primary, modifier = Modifier.padding(bottom = 4.dp))
    Text(label, style = NudgeType.LabelCaps, color = NudgeColors.OnSurfaceVariant)
    Text(
      androidx.compose.ui.text.buildAnnotatedString {
        append("$value ")
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)); append(unit); pop()
      },
      style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface,
    )
  }
}
