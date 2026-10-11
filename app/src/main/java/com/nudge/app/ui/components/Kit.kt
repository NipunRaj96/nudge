package com.nudge.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeShapes
import com.nudge.app.ui.theme.NudgeType

/** Standard 64dp header: optional back button, title, optional trailing slot. Respects the status bar inset. */
@Composable
fun ScreenHeader(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) CircleIconButton("arrow_back", "Back", onBack)
      Text(title, style = NudgeType.HeadlineMd.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em), color = NudgeColors.OnBackground)
    }
    trailing?.invoke()
  }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
  Text(text.uppercase(), style = NudgeType.LabelCaps.copy(letterSpacing = 0.1.em), color = NudgeColors.Outline, modifier = modifier)

/** Small pill status badge (COMPLETED / NO-SHOW / ON ROUTE ...). */
@Composable
fun StatusChip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, icon: String? = null) {
  Row(
    modifier.background(bg, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Symbol(icon, size = 14.dp, tint = fg)
    Text(text, style = twText(12f, FontWeight.Bold, 16.sp, 0.02f), color = fg, maxLines = 1)
  }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, enabled: Boolean = true) {
  val bg by animateColorAsState(if (enabled) NudgeColors.PrimaryContainer else NudgeColors.SurfaceContainerHighest, tween(200), label = "btn")
  val fg = if (enabled) NudgeColors.OnPrimaryContainer else NudgeColors.Outline
  Row(
    modifier.heightIn(min = 56.dp).clip(NudgeShapes.Full).background(bg).tappable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, style = twText(16f, FontWeight.SemiBold, 24.sp), color = fg, maxLines = 1)
    if (icon != null) Symbol(icon, size = 20.dp, tint = fg)
  }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = NudgeColors.OnSurface, icon: String? = null) {
  Row(
    modifier.heightIn(min = 56.dp).clip(NudgeShapes.Full).hairline(color.copy(alpha = 0.35f)).tappable(onClick = onClick).padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Symbol(icon, size = 20.dp, tint = color)
    Text(text, style = twText(16f, FontWeight.SemiBold, 24.sp), color = color, maxLines = 1)
  }
}

/** Animated, tappable segmented control (used for Morning/Evening and payment filters). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLowest, CircleShape).hairline(Color.White.copy(alpha = 0.05f)).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    options.forEachIndexed { i, label ->
      val on = i == selected
      val bg by animateColorAsState(if (on) NudgeColors.PrimaryContainer else Color.Transparent, tween(220), label = "seg")
      val fg by animateColorAsState(if (on) NudgeColors.OnPrimaryContainer else NudgeColors.OnSurfaceVariant, tween(220), label = "segfg")
      Box(
        Modifier.weight(1f).clip(CircleShape).background(bg).tappable(pressedScale = 0.98f) { onSelect(i) }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) { Text(label, style = twText(14f, FontWeight.SemiBold, 20.sp), color = fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp)) }
    }
  }
}

/** Yes/no confirmation for anything that can't be undone. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Keep", destructive: Boolean = true) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NudgeColors.SurfaceContainerHigh,
    title = { Text(title, style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface) },
    text = { Text(body, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant) },
    confirmButton = { androidx.compose.material3.TextButton({ onDismiss(); onConfirm() }) { Text(confirm, color = if (destructive) NudgeColors.Error else NudgeColors.Primary) } },
    dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(dismiss, color = NudgeColors.Primary) } },
  )
}

/** Blocking "please wait" for slow, multi-step work (e.g. importing a timetable). */
@Composable
fun ProgressDialog(text: String) {
  androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
    Row(
      Modifier.background(NudgeColors.SurfaceContainerHigh, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).padding(24.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
      androidx.compose.material3.CircularProgressIndicator(color = NudgeColors.Primary, modifier = Modifier.padding(4.dp))
      Text(text, style = NudgeType.BodySm, color = NudgeColors.OnSurface)
    }
  }
}
