package com.nudge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.PlusJakartaSans

/** Tailwind-style text: size in sp, line-height multiple (default 1.5 = Tailwind preflight), tracking in em. */
fun twText(
  size: Float,
  weight: FontWeight = FontWeight.Normal,
  lineHeight: TextUnit = (size * 1.5f).sp,
  tracking: Float = 0f,
) = TextStyle(
  fontFamily = PlusJakartaSans, fontSize = size.sp, fontWeight = weight,
  lineHeight = lineHeight, letterSpacing = tracking.em,
)

/** 1px white/10-style hairline border used all over the Stitch screens. */
fun Modifier.hairline(color: Color = Color.White.copy(alpha = 0.1f), shape: androidx.compose.ui.graphics.Shape = CircleShape) =
  border(BorderStroke(1.dp, color), shape)

/** 40dp circular icon button (bg surface-container-low, border white/10). */
@Composable
fun CircleIconButton(
  icon: String,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconSize: Dp = 20.dp,
  tint: Color = NudgeColors.OnSurface,
) {
  Box(
    modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(NudgeColors.SurfaceContainerLow)
      .hairline()
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Symbol(icon, size = iconSize, tint = tint) }
}

/** Soft radial glow (approximation of a CSS blurred, tinted circle). */
@Composable
fun GlowCircle(diameter: Dp, color: Color, modifier: Modifier = Modifier, solidUntil: Float = 0.0f) {
  Canvas(modifier.size(diameter)) {
    val r = size.minDimension / 2f
    drawCircle(
      Brush.radialGradient(solidUntil to color, 1f to Color.Transparent, center = Offset(r, r), radius = r),
      radius = r, center = Offset(r, r),
    )
  }
}

/** Stitch `.glass-effect` (rgba(26,26,26,.7) + 1px white/8). Backdrop blur has no Compose equivalent; the 70% tint is kept. */
val GlassColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape) =
  background(GlassColor, shape).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)

@Composable
fun GlassCircleButton(icon: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(modifier.size(48.dp).clip(CircleShape).glass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Symbol(icon, tint = NudgeColors.OnSurface)
  }
}

/**
 * CSS `display:flex; justify-content:space-between; align-items:flex-start` with default `flex-shrink:1` for two
 * children: each child keeps its natural width, and when they don't fit both shrink in proportion to their natural
 * widths (so text wraps exactly like it does in the browser). Compose's Row would overflow instead.
 */
@Composable
fun FlexBetweenRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
    val avail = constraints.maxWidth
    val natural = measurables.map { it.maxIntrinsicWidth(Int.MAX_VALUE) }
    val total = natural.sum()
    val widths = if (total <= avail) natural else {
      val over = total - avail
      natural.map { (it - over * it.toFloat() / total).toInt() }
    }
    val placeables = measurables.mapIndexed { i, m ->
      m.measure(androidx.compose.ui.unit.Constraints(minWidth = 0, maxWidth = widths[i].coerceAtLeast(0), minHeight = 0, maxHeight = constraints.maxHeight))
    }
    val h = placeables.maxOfOrNull { it.height } ?: 0
    layout(avail, h) {
      val used = placeables.sumOf { it.width }
      val gap = if (placeables.size > 1) (avail - used) / (placeables.size - 1) else 0
      var x = 0
      placeables.forEach { it.placeRelative(x, 0); x += it.width + gap }
    }
  }
}
