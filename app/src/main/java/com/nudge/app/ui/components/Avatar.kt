package com.nudge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nudge.app.ui.theme.NudgeColors

/** Circle with initials, used where Stitch shows stock avatar photos (we never ship remote stock images). */
@Composable
fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
  val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
  Box(modifier.background(NudgeColors.SurfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
    androidx.compose.material3.Text(
      initials,
      style = twText(size.value * 0.38f, androidx.compose.ui.text.font.FontWeight.Bold),
      color = NudgeColors.Primary,
    )
  }
}

