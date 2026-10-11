package com.nudge.app.ui.otp

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.components.CircleIconButton
import com.nudge.app.ui.components.GlowCircle
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeShapes
import com.nudge.app.ui.theme.NudgeType

private val OtpBoxBg = Color(0xFF1C1B1B)
private val Radius2xl = RoundedCornerShape(16.dp) // Tailwind default rounded-2xl (not overridden by Stitch config)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtpScreen(
  code: String,
  onCodeChange: (String) -> Unit,
  maskedPhone: String,
  resendSeconds: Int,
  onBack: () -> Unit,
  onChangeNumber: () -> Unit,
  onVerify: () -> Unit,
  modifier: Modifier = Modifier,
  onResend: () -> Unit = {},
) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }

  Box(modifier.fillMaxSize().background(NudgeColors.SurfaceContainerLowest)) {
    // Ambient glows (rgba(46,91,255,.18) -> transparent), top-centre 384dp and right 256dp @60%.
    GlowCircle(384.dp, NudgeColors.PrimaryContainer.copy(alpha = 0.18f), Modifier.align(Alignment.TopCenter).padding(top = 0.dp))
    GlowCircle(256.dp, NudgeColors.PrimaryContainer.copy(alpha = 0.18f * 0.6f), Modifier.align(Alignment.CenterEnd).padding(end = 0.dp))

    Column(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .imePadding()
        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // ── Header: top nav (h-12)
      Row(
        Modifier.fillMaxWidth().height(48.dp).padding(top = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircleIconButton("arrow_back", "Go back", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
            1f, 0.5f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "dot",
          )
          Box(Modifier.size(8.dp).alpha(pulse).background(NudgeColors.PrimaryContainer, CircleShape))
          Text("Nudge", style = twText(18f, FontWeight.ExtraBold, 28.sp, -0.025f), color = NudgeColors.OnSurface)
        }
        Spacer(Modifier.size(48.dp)) // keeps the brand centred (the design's help button had no action)
      }

      // ── Content (centered vertically)
      val keyboard = WindowInsets.isImeVisible
      Column(
        Modifier.weight(1f).fillMaxWidth().then(if (keyboard) Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier).padding(vertical = 8.dp),
        verticalArrangement = if (keyboard) Arrangement.Bottom else Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Badge (hidden while typing so the code boxes and button stay above the keyboard)
        if (!keyboard) Box(Modifier.padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
          GlowCircle(96.dp, NudgeColors.PrimaryContainer.copy(alpha = 0.2f), solidUntil = 0.4f)
          Box(
            Modifier
              .shadow(10.dp, Radius2xl)
              .size(64.dp)
              .background(NudgeColors.SurfaceContainer, Radius2xl)
              .hairline(shape = Radius2xl),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              Modifier
                .size(44.dp)
                .background(NudgeColors.PrimaryContainer.copy(alpha = 0.15f), CircleShape)
                .hairline(NudgeColors.PrimaryContainer.copy(alpha = 0.3f)),
              contentAlignment = Alignment.Center,
            ) { Symbol("mark_email_read", size = 26.dp, tint = NudgeColors.Primary, filled = true) }
          }
        }

        // Title block
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            "Verify your number",
            style = NudgeType.HeadlineMd.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em),
            color = NudgeColors.OnBackground,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
          ) {
            Text("We sent a 6-digit code to", style = NudgeType.BodySm, color = NudgeColors.Secondary)
            Text(maskedPhone, style = NudgeType.BodySm.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.025.em), color = NudgeColors.OnSurface)
            Row(
              Modifier.padding(start = 4.dp).tappable(onClick = onChangeNumber),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Change", style = twText(12f, FontWeight.SemiBold, 16.sp), color = NudgeColors.Primary)
              Spacer(Modifier.width(2.dp))
              Symbol("edit", size = 14.dp, tint = NudgeColors.Primary)
            }
          }
        }

        // OTP boxes (real input is a hidden field that owns focus + keyboard)
        Box(Modifier.widthIn(max = 384.dp).fillMaxWidth().padding(bottom = 24.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { i ->
              OtpBox(
                char = code.getOrNull(i),
                active = i == code.length.coerceAtMost(5) && code.length < 6,
                modifier = Modifier.weight(1f),
              )
            }
          }
          BasicTextField(
            value = code,
            onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = NudgeType.BodyLg.copy(color = Color.Transparent),
            modifier = Modifier.matchParentSize().alpha(0.01f).focusRequester(focus),
          )
        }

        // Resend + security badge
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            Modifier
              .background(NudgeColors.SurfaceContainerLow, CircleShape)
              .hairline(Color.White.copy(alpha = 0.05f))
              .then(if (resendSeconds == 0) Modifier.tappable(onClick = onResend) else Modifier)
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Symbol(if (resendSeconds == 0) "refresh" else "schedule", size = 15.dp, tint = NudgeColors.Primary)
            Text(
              buildAnnotatedString {
                if (resendSeconds == 0) {
                  withStyle(SpanStyle(color = NudgeColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em)) { append("Resend code") }
                } else {
                  append("Resend code in ")
                  withStyle(SpanStyle(color = NudgeColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em)) {
                    append("00:%02d".format(resendSeconds))
                  }
                }
              },
              style = NudgeType.LabelCaps.copy(fontWeight = FontWeight.Medium),
              color = NudgeColors.OnSurfaceVariant,
            )
          }
        }
      }

      // ── Footer
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VerifyButton(onVerify)
      }
    }
  }
}

@Composable
private fun OtpBox(char: Char?, active: Boolean, modifier: Modifier) {
  val shape = Radius2xl
  val caret by rememberInfiniteTransition(label = "caret").animateFloat(
    1f, 0f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "caret",
  )
  Box(
    modifier
      .height(56.dp)
      .then(if (active) Modifier.border(2.dp, NudgeColors.PrimaryContainer.copy(alpha = 0.3f), shape) else Modifier)
      .padding(if (active) 0.dp else 0.dp)
      .background(if (active) NudgeColors.SurfaceContainerHigh else OtpBoxBg, shape)
      .then(
        if (active) Modifier.border(2.dp, NudgeColors.Primary, shape) else Modifier.hairline(shape = shape),
      ),
    contentAlignment = Alignment.Center,
  ) {
    when {
      char != null -> Text(char.toString(), style = twText(20f, FontWeight.Bold, 28.sp), color = NudgeColors.OnSurface)
      active -> Box(Modifier.width(2.dp).height(24.dp).alpha(caret).background(NudgeColors.Primary, CircleShape))
    }
  }
}

@Composable
private fun VerifyButton(onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Row(
    Modifier
      .fillMaxWidth()
      .scale(if (pressed) 0.98f else 1f)
      .shadow(10.dp, NudgeShapes.Full, ambientColor = NudgeColors.PrimaryContainer.copy(alpha = 0.2f), spotColor = NudgeColors.PrimaryContainer.copy(alpha = 0.2f))
      .clip(NudgeShapes.Full)
      .background(NudgeColors.PrimaryContainer)
      .clickable(interaction, indication = null, onClick = onClick)
      .padding(vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Verify and continue", style = NudgeType.BodyLg.copy(fontWeight = FontWeight.SemiBold), color = NudgeColors.OnPrimaryContainer)
    Symbol("arrow_forward", size = 20.dp, tint = NudgeColors.OnPrimaryContainer)
  }
}
