package com.nudge.app.ui.login

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeShapes
import com.nudge.app.ui.theme.NudgeSpacing
import com.nudge.app.ui.theme.NudgeType

// SVG path of the brand blob, copied from login-signup-e8038.html (viewBox 200x200, translate(100 100)).
private const val BLOB_PATH =
  "M45.7,-78.3C58.9,-71.4,69.2,-58.5,77,-44.6C84.7,-30.7,89.9,-15.3,88.7,-0.7C87.4,13.9,79.7,27.8,70.5,40.1" +
    "C61.3,52.4,50.6,63.1,37.8,70.9C25,78.7,10,83.6,-4.8,80.8C-19.6,78.1,-34.2,67.7,-46.8,56.5" +
    "C-59.4,45.3,-70.1,33.3,-76.3,19.3C-82.5,5.3,-84.3,-10.8,-79.1,-24.8C-73.8,-38.8,-61.6,-50.8,-48.4,-57.8" +
    "C-35.1,-64.8,-20.9,-66.8,-3.8,-65.6C13.2,-64.4,26.5,-59.9,45.7,-78.3Z"

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
  phone: String,
  onPhoneChange: (String) -> Unit,
  onContinue: () -> Unit,
  modifier: Modifier = Modifier,
  error: Boolean = false,
  loading: Boolean = false,
) {
  Box(modifier.fillMaxSize().background(NudgeColors.Background)) {
    AmbientGlows()

    // Outer 24dp page margin (body p-margin-main) and the 3rem "stealth-gradient" card.
    Box(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .imePadding()
        .padding(NudgeSpacing.MarginMain)
        .clip(NudgeShapes.Xl)
        .background(
          Brush.radialGradient(
            0f to Color(0xFF1A1A1A), 1f to NudgeColors.Background,
            center = Offset.Unspecified, radius = Float.POSITIVE_INFINITY,
          ),
        )
        .padding(NudgeSpacing.StackLg),
    ) {
      val ime = WindowInsets.isImeVisible
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // The design is a flex column (justify-between) taller than many phones. Keep the exact spacing when it
        // fits; otherwise shrink only the ornament (min 160dp) and fall back to scrolling (e.g. keyboard open).
        val fixed = 530.dp // all fixed-height content incl. 3-line terms, at design spacing (ornament excluded)
        val ornament = (maxHeight - fixed).coerceIn(160.dp, 256.dp)
        val gap = (maxHeight - fixed - ornament).coerceAtLeast(0.dp)
        // Keyboard open: the brand block collapses so number + Continue sit right above the keyboard.
        // Same call sites either way, so the text field keeps focus.
        Column(
          Modifier.fillMaxSize().then(if (ime) Modifier else Modifier.verticalScroll(rememberScrollState())),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = if (ime) Arrangement.Bottom else Arrangement.Top,
        ) {
          androidx.compose.animation.AnimatedVisibility(!ime) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Spacer(Modifier.height(NudgeSpacing.StackLg))
              Text("Nudge", style = NudgeType.HeadlineMd, fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em, color = NudgeColors.OnBackground)
              Spacer(Modifier.height(NudgeSpacing.StackLg))
              BrandOrnament(ornament)
              Spacer(Modifier.height(NudgeSpacing.StackLg))
            }
          }

          Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NudgeSpacing.StackLg)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
              Text(
                "Welcome back, get moving.",
                style = NudgeType.DisplayLg.copy(lineHeight = 45.sp),
                color = NudgeColors.OnBackground,
                textAlign = TextAlign.Center,
              )
              Spacer(Modifier.height(NudgeSpacing.StackSm))
              Text(
                "Enter your details to find your ride.",
                style = NudgeType.BodyLg,
                color = NudgeColors.OnSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
              )
            }
            PhoneField(phone, onPhoneChange, error, onContinue)
          }

          Spacer(Modifier.height(if (ime) NudgeSpacing.StackMd else gap + NudgeSpacing.StackLg))
          ContinueButton(onContinue, loading)
          if (!ime) {
            Spacer(Modifier.height(NudgeSpacing.StackMd))
            Terms()
          }
        }
      }
    }
  }
}

@Composable
private fun AmbientGlows() {
  // Page-level blurred blobs (opacity .2): primary top-left 50%x50%, primary-container bottom-right 40%x40%.
  Canvas(Modifier.fillMaxSize().alpha(0.07f)) {
    val w = size.width; val h = size.height
    fun blob(color: Color, cx: Float, cy: Float, r: Float, blur: Float) = drawCircle(
      Brush.radialGradient(
        0f to color, (r / (r + blur * 1.5f)) to color, 1f to Color.Transparent,
        center = Offset(cx, cy), radius = r + blur * 1.5f,
      ),
      radius = r + blur * 1.5f, center = Offset(cx, cy),
    )
    blob(NudgeColors.Primary, -0.10f * w + 0.25f * w, -0.10f * h + 0.25f * h, 0.25f * minOf(w, h) * 1.4f, 120.dp.toPx() / 2)
    blob(NudgeColors.PrimaryContainer, 1.10f * w - 0.20f * w, 1.10f * h - 0.20f * h, 0.20f * minOf(w, h) * 1.4f, 100.dp.toPx() / 2)
  }
}

@Composable
private fun BrandOrnament(diameter: Dp = 256.dp) {
  // Tailwind animate-pulse keyframes only set opacity at 50% (.5); 0%/100% fall back to the element's
  // opacity-20, so the blob breathes 0.2 -> 0.5 -> 0.2 over 2s with cubic-bezier(.4,0,.6,1).
  val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
    initialValue = 0.2f, targetValue = 0.5f,
    animationSpec = infiniteRepeatable(tween(1000, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)), RepeatMode.Reverse),
    label = "pulse",
  )
  val path = remember { PathParser().parsePathString(BLOB_PATH).toPath() }
  Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().alpha(pulse)) {
      val s = size.width / 200f
      withTransform({ scale(s, s, Offset.Zero); translate(100f, 100f) }) { drawPath(path, NudgeColors.Primary) }
    }
    Box(
      Modifier
        .shadow(40.dp, CircleShape, ambientColor = Color.White.copy(alpha = 0.15f), spotColor = Color.White.copy(alpha = 0.15f))
        .background(Color.White, CircleShape)
        .padding(24.dp),
    ) {
      Symbol("near_me", size = 40.dp, tint = NudgeColors.PrimaryContainer, filled = true)
    }
  }
}

@Composable
private fun PhoneField(phone: String, onChange: (String) -> Unit, error: Boolean, onDone: () -> Unit) = Column {
  var focused by remember { mutableStateOf(false) }
  val borderColor = when { error -> NudgeColors.Error; focused -> NudgeColors.Primary.copy(alpha = 0.5f); else -> Color.White.copy(alpha = 0.1f) }
  Row(
    Modifier
      .fillMaxWidth()
      .then(if (focused) Modifier.shadow(20.dp, NudgeShapes.Lg, ambientColor = NudgeColors.Primary.copy(alpha = 0.1f), spotColor = NudgeColors.Primary.copy(alpha = 0.1f)) else Modifier)
      .background(NudgeColors.SurfaceContainer, NudgeShapes.Lg)
      .border(BorderStroke(1.dp, borderColor), NudgeShapes.Lg)
      .padding(horizontal = 24.dp, vertical = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("+91", style = NudgeType.BodyLg, color = NudgeColors.OnSurfaceVariant)
    Spacer(Modifier.width(16.dp))
    Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.1f)))
    Spacer(Modifier.width(16.dp))
    BasicTextField(
      value = phone,
      onValueChange = { onChange(it.filter(Char::isDigit).take(10)) },
      singleLine = true,
      textStyle = NudgeType.BodyLg.copy(color = NudgeColors.OnBackground),
      cursorBrush = SolidColor(NudgeColors.Primary),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = androidx.compose.ui.text.input.ImeAction.Go),
      keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onDone() }),
      modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
      decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (phone.isEmpty()) Text("Mobile Number", style = NudgeType.BodyLg, color = NudgeColors.OnSurfaceVariant.copy(alpha = 0.4f))
          inner()
        }
      },
    )
  }
  if (error) Text("Enter a valid 10-digit mobile number", style = NudgeType.BodySm, color = NudgeColors.Error, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
}

@Composable
private fun ContinueButton(onClick: () -> Unit, loading: Boolean = false) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Row(
    Modifier
      .fillMaxWidth()
      .scale(if (pressed) 0.98f else 1f)
      .shadow(30.dp, NudgeShapes.Full, ambientColor = Color(0xFFB8C1FF).copy(alpha = 0.2f), spotColor = Color(0xFFB8C1FF).copy(alpha = 0.2f))
      .clip(NudgeShapes.Full)
      .background(NudgeColors.Primary)
      .clickable(interaction, indication = null, onClick = onClick)
      .padding(vertical = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Stitch applies no weight/size to this label (font-headline-md only sets the family) => 16sp regular.
    // Colour: Stitch's own class (text-on-primary-fixed) is missing from its Tailwind config, so the HTML falls
    // back to light grey on lavender (unreadable). We use the intended on-primary-fixed token. See decisions.md.
    Text(if (loading) "Sending code…" else "Continue", style = NudgeType.BodyLg, color = NudgeColors.OnPrimaryFixed)
    Symbol("arrow_forward", size = 24.dp, tint = NudgeColors.OnPrimaryFixed)
  }
}

@Composable
private fun Terms() {
  val text = buildAnnotatedString {
    append("By continuing you agree to our\n") // two balanced lines instead of an orphaned "Policy"
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("Terms") }
    append(" and ")
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("Privacy Policy") }
  }
  Text(
    text,
    // Stitch uses font-label-caps (family only) here, so size/weight inherit: 16px regular.
    style = NudgeType.BodyLg,
    color = NudgeColors.OnSurfaceVariant.copy(alpha = 0.5f),
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(horizontal = 24.dp),
  )
}
