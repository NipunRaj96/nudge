package com.nudge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius24 = RoundedCornerShape(24.dp)
private val Radius32 = RoundedCornerShape(32.dp)

/** Labelled single-line input in the app's pill style. */
@Composable
fun FormField(
  label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
  placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    Row(
      Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainer, CircleShape).hairline(Color.White.copy(alpha = 0.1f)).padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
      if (prefix != null) Text(prefix, style = twText(16f, lineHeight = 24.sp), color = NudgeColors.OnSurfaceVariant)
      BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = twText(16f, lineHeight = 24.sp).copy(color = NudgeColors.OnSurface),
        cursorBrush = SolidColor(NudgeColors.Primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        modifier = Modifier.weight(1f),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = twText(16f, lineHeight = 24.sp), color = NudgeColors.OutlineVariant); inner() } },
      )
    }
  }
}

/** A label + description with an on/off switch. */
@Composable
fun ToggleRow(label: String, body: String?, on: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius24).hairline(Color.White.copy(alpha = 0.06f), Radius24).padding(horizontal = 20.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, style = twText(15f, FontWeight.SemiBold, 22.sp), color = NudgeColors.OnSurface)
      if (body != null) Text(body, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
    }
    Switch(on, onChange)
  }
}

/** Pill switch used across operator screens. */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
  val bg by androidx.compose.animation.animateColorAsState(if (on) NudgeColors.PrimaryContainer else NudgeColors.SurfaceContainerHighest, androidx.compose.animation.core.tween(220), label = "track")
  val x by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 2.dp, androidx.compose.animation.core.spring(stiffness = 600f), label = "thumb")
  Box(Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape).background(bg).tappable(pressedScale = 0.95f) { onChange(!on) }) {
    Box(Modifier.padding(start = x, top = 2.dp).size(24.dp).background(Color.White, CircleShape))
  }
}

/** Tappable row card: title, subtitle, optional trailing content. */
@Composable
fun ListCard(title: String, subtitle: String?, modifier: Modifier = Modifier, leadingIcon: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
  Row(
    modifier.fillMaxWidth().clip(Radius32).background(NudgeColors.SurfaceContainerLow).hairline(Color.White.copy(alpha = 0.06f), Radius32)
      .then(if (onClick != null) Modifier.tappable(pressedScale = 0.99f, onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leadingIcon != null) Box(Modifier.size(40.dp).background(NudgeColors.SurfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
      Symbol(leadingIcon, size = 20.dp, tint = NudgeColors.Primary)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = twText(16f, FontWeight.SemiBold, 24.sp), color = NudgeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) Text(subtitle, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    trailing?.invoke()
  }
}

/** Bottom sheet with a title, scrollable form and a save button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
  title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, saveLabel: String = "Save",
  extraAction: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = NudgeColors.SurfaceContainerLow) {
    Column(Modifier.fillMaxWidth().imePadding().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
      Text(title, style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface, modifier = Modifier.padding(bottom = 16.dp))
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
      Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PrimaryButton(saveLabel, onSave, Modifier.fillMaxWidth(), enabled = saveEnabled)
        extraAction?.invoke()
      }
    }
  }
}
