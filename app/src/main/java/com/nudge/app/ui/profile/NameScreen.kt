package com.nudge.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

/** First login of a new rider: the name the driver sees on the manifest. */
@Composable
fun NameScreen(name: String, onNameChange: (String) -> Unit, enabled: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  val valid = name.trim().length >= 2
  Column(modifier.fillMaxSize().background(NudgeColors.Background).imePadding()) {
    ScreenHeader("Welcome to Nudge")
    Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Spacer(Modifier.padding(top = 8.dp))
      Text("What should we call you?", style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface)
      Text("Your driver sees this name when you board.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
      Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).background(NudgeColors.SurfaceContainer, CircleShape).hairline(Color.White.copy(alpha = 0.1f))
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
      ) {
        Symbol("person", size = 20.dp, tint = NudgeColors.OnSurfaceVariant)
        BasicTextField(
          value = name, onValueChange = { onNameChange(it.take(40)) }, singleLine = true,
          textStyle = twText(16f, lineHeight = 24.sp).copy(color = NudgeColors.OnSurface),
          cursorBrush = SolidColor(NudgeColors.Primary),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { if (valid && enabled) onSave() }),
          modifier = Modifier.weight(1f).focusRequester(focus),
          decorationBox = { inner -> Box { if (name.isEmpty()) Text("Full name", style = twText(16f, lineHeight = 24.sp), color = NudgeColors.OutlineVariant); inner() } },
        )
      }
    }
    PrimaryButton(
      "Continue", onSave, Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 16.dp),
      enabled = valid && enabled,
    )
  }
}

/** Centered icon + text + optional action, for "nothing here yet" states. */
@Composable
fun EmptyState(icon: String, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
  Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
    Box(Modifier.background(NudgeColors.SurfaceContainerHigh, CircleShape).padding(20.dp)) { Symbol(icon, size = 32.dp, tint = NudgeColors.Primary) }
    Text(title, style = twText(18f, FontWeight.Bold, 26.sp), color = NudgeColors.OnSurface)
    Text(body, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    action?.invoke()
  }
}
