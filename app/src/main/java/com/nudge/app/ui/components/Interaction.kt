package com.nudge.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Tap target with a springy press-scale (Stitch's `active:scale-95/98`), no ripple. Use instead of
 * `Modifier.clickable` for every button/card so touch feedback feels the same everywhere.
 */
fun Modifier.tappable(enabled: Boolean = true, pressedScale: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    if (pressed) pressedScale else 1f,
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
    label = "press",
  )
  this
    .graphicsLayer { scaleX = scale; scaleY = scale }
    .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}
