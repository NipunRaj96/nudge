package com.nudge.app.ui.plans

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeShapes
import com.nudge.app.ui.theme.NudgeType

data class PlanOption(
  val id: String, val title: String, val validity: String, val price: String, val priceNote: String,
  val popular: Boolean = false,
  /** Day-of-month pricing, e.g. "1–10 ₹3,499 · 11–20 ₹2,499 · 21–31 ₹1,999". */
  val tierNote: String? = null,
)

data class WalkOn(val id: String, val title: String, val priceLabel: String, val note: String)

private val Radius32 = RoundedCornerShape(32.dp)
private val Tertiary = NudgeColors.Tertiary

@Composable
fun ChoosePlanScreen(
  plans: List<PlanOption>,
  walkOn: WalkOn?,
  selectedId: String,
  onSelect: (String) -> Unit,
  ctaText: String,
  footnote: String,
  onContinue: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pulse by rememberInfiniteTransition(label = "dot").animateFloat(1f, 0.5f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "dot")
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    com.nudge.app.ui.components.ScreenHeader("Choose a plan", onBack = onBack)
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Pick a pass, pay by UPI, and book any ride on your route in seconds.", style = NudgeType.BodySm, color = NudgeColors.Secondary)
      }
      Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        plans.forEach { PlanCard(it, it.id == selectedId) { onSelect(it.id) } }
      }
      if (walkOn != null) WalkOnCard(walkOn, walkOn.id == selectedId) { onSelect(walkOn.id) }
      Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Perk("verified_user", "Reserved seat", Modifier.weight(1f))
        Perk("ac_unit", "AC shuttle", Modifier.weight(1f))
      }
    }

    // Docked action bar
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
      Column(
        Modifier.fillMaxWidth().shadow(24.dp, Radius32).background(NudgeColors.SurfaceContainerLowest.copy(alpha = 0.9f), Radius32)
          .hairline(Color.White.copy(alpha = 0.1f), Radius32).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          Modifier.fillMaxWidth().shadow(8.dp, NudgeShapes.Full).clip(NudgeShapes.Full).background(NudgeColors.PrimaryContainer)
            .tappable(onClick = onContinue).padding(horizontal = 24.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(ctaText, style = twText(16f, FontWeight.Bold, 24.sp, 0.025f), color = NudgeColors.OnPrimaryContainer)
          Symbol("arrow_forward", size = 18.dp, tint = NudgeColors.OnPrimaryContainer)
        }
        Text(footnote, style = twText(10f, lineHeight = 15.sp), color = Tertiary)
      }
    }
  }
}

@Composable
private fun Radio(selected: Boolean, size: androidx.compose.ui.unit.Dp, strokeUnselected: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
  if (selected) {
    Box(modifier.size(size).background(NudgeColors.PrimaryContainer.copy(alpha = 0.2f), CircleShape).border(2.dp, NudgeColors.PrimaryContainer, CircleShape), contentAlignment = Alignment.Center) {
      Box(Modifier.size(size / 2).shadow(8.dp, CircleShape, ambientColor = NudgeColors.PrimaryContainer, spotColor = NudgeColors.PrimaryContainer).background(NudgeColors.PrimaryContainer, CircleShape))
    }
  } else {
    Box(modifier.size(size).border(strokeUnselected, NudgeColors.Outline.copy(alpha = 0.5f), CircleShape))
  }
}

@Composable
private fun PlanCard(p: PlanOption, selected: Boolean, onClick: () -> Unit) {
  val bgColor by androidx.compose.animation.animateColorAsState(if (selected) NudgeColors.SurfaceContainer else NudgeColors.SurfaceContainerLow, tween(240), label = "bg")
  val borderColor by androidx.compose.animation.animateColorAsState(if (selected) NudgeColors.PrimaryContainer else Color.White.copy(alpha = 0.06f), tween(240), label = "border")
  Box(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth()
        .then(if (selected) Modifier.shadow(24.dp, Radius32, ambientColor = NudgeColors.PrimaryContainer.copy(alpha = 0.22f), spotColor = NudgeColors.PrimaryContainer.copy(alpha = 0.22f)) else Modifier)
        .clip(Radius32)
        .background(bgColor)
        .border(2.dp, borderColor, Radius32)
        .tappable(pressedScale = 0.98f, onClick = onClick).padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top,
    ) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(p.title, style = twText(16f, FontWeight.Bold, 24.sp), color = if (selected) Color.White else NudgeColors.OnSurface)
        Text(p.validity, style = NudgeType.LabelCaps, color = if (selected) NudgeColors.PrimaryFixedDim else NudgeColors.Secondary)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
          Text(p.price, style = twText(20f, FontWeight.Bold, 28.sp), color = if (selected) Color.White else NudgeColors.OnSurface)
          Text(
            p.priceNote,
            style = twText(12f, if (selected) FontWeight.SemiBold else FontWeight.Medium, 16.sp),
            color = if (selected) NudgeColors.Primary else NudgeColors.Secondary,
            modifier = Modifier.padding(bottom = 2.dp),
          )
        }
        if (p.tierNote != null) Text(p.tierNote, style = twText(11f, FontWeight.Medium, 16.sp), color = NudgeColors.Secondary, modifier = Modifier.padding(top = 2.dp))
      }
      Radio(selected, 24.dp, 2.dp, Modifier.padding(top = 2.dp))
    }
    if (p.popular) {
      Row(
        Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = (-12).dp).shadow(6.dp, CircleShape)
          .background(NudgeColors.PrimaryContainer, CircleShape).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
      ) {
        Symbol("star", size = 12.dp, tint = NudgeColors.OnPrimaryContainer, filled = true)
        Text("MOST POPULAR", style = twText(10f, FontWeight.Bold, 15.sp, 0.05f), color = NudgeColors.OnPrimaryContainer)
      }
    }
  }
}

@Composable
private fun WalkOnCard(w: WalkOn, selected: Boolean, onClick: () -> Unit) {
  Row(
    Modifier.padding(top = 4.dp).fillMaxWidth().clip(Radius32)
      .background(Brush.horizontalGradient(listOf(NudgeColors.SurfaceContainerLow, NudgeColors.SurfaceContainer)))
      .hairline(if (selected) NudgeColors.PrimaryContainer else Color.White.copy(alpha = 0.1f), Radius32)
      .tappable(pressedScale = 0.98f, onClick = onClick).padding(14.dp),
    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(40.dp).background(NudgeColors.SurfaceContainerHighest.copy(alpha = 0.6f), CircleShape).hairline(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
        Symbol("directions_bus", size = 20.dp, tint = NudgeColors.Primary)
      }
      Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(w.title, style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnSurface)
          Text(w.priceLabel, style = twText(12f, FontWeight.Bold, 16.sp), color = NudgeColors.Primary)
        }
        Text(w.note, style = twText(11f), color = Tertiary)
      }
    }
    if (selected) Radio(true, 20.dp, 1.dp) else Box(Modifier.size(20.dp).border(1.dp, NudgeColors.Outline.copy(alpha = 0.5f), CircleShape))
  }
}

@Composable
private fun Perk(icon: String, text: String, modifier: Modifier) {
  Row(
    modifier.background(NudgeColors.SurfaceContainerLowest.copy(alpha = 0.8f), NudgeShapes.Default).hairline(Color.White.copy(alpha = 0.05f), NudgeShapes.Default).padding(10.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Symbol(icon, size = 16.dp, tint = NudgeColors.Primary)
    Text(text, style = twText(11f, FontWeight.Medium), color = NudgeColors.Secondary)
  }
}
