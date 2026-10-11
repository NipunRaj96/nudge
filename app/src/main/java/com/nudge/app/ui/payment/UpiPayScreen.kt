package com.nudge.app.ui.payment

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.components.GhostButton
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.QrCode
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

data class UpiPayState(
  val amountLabel: String, val planLine: String, val upiId: String, val payeeName: String, val amountRupees: Int,
  /** Shown in the payer's UPI app and the operator's bank statement. */
  val note: String,
  val footnote: String,
  /** All UPI ids the rider may pay to (operator decides); the selected one is [upiId]. */
  val accounts: List<String> = listOf(upiId),
) {
  /** Standard UPI deep link: any UPI app can scan or open this. */
  val upiUri: String get() = android.net.Uri.Builder().scheme("upi").authority("pay")
    .appendQueryParameter("pa", upiId).appendQueryParameter("pn", payeeName).appendQueryParameter("am", amountRupees.toString())
    .appendQueryParameter("cu", "INR").appendQueryParameter("tn", note).build().toString()
}

private val Radius32 = RoundedCornerShape(32.dp)
private val Zinc100 = Color(0xFFF4F4F5)
private val Zinc200 = Color(0xFFE4E4E7)
private val Zinc800 = Color(0xFF27272A)

/** Manual UPI payment: show the operator's QR, the rider pays in any UPI app, then enters the 12-digit UTR. */
@Composable
fun UpiPayScreen(
  state: UpiPayState,
  utr: String,
  onUtrChange: (String) -> Unit,
  copied: Boolean,
  onCopy: () -> Unit,
  onOpenApp: () -> Unit,
  canSubmit: Boolean,
  onPaid: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onAccount: (String) -> Unit = {},
) {
  Column(modifier.fillMaxSize().background(NudgeColors.Background).imePadding()) {
    ScreenHeader("Pay ${state.amountLabel}", onBack = onBack)
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(state.planLine, style = NudgeType.BodyLg, color = NudgeColors.OnSurfaceVariant)
      if (state.accounts.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Pay to", style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
          com.nudge.app.ui.components.Segmented(state.accounts, state.accounts.indexOf(state.upiId).coerceAtLeast(0), { onAccount(state.accounts[it]) })
        }
      }

      Column(
        Modifier.fillMaxWidth().shadow(16.dp, Radius32).background(Color.White, Radius32).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        QrCode(state.upiUri, 184.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(state.payeeName, style = twText(16f, FontWeight.Bold, 24.sp), color = Color(0xFF18181B), textAlign = TextAlign.Center)
          Text("Scan with any UPI app", style = twText(13f, FontWeight.Medium, 18.sp), color = Color(0xFF52525B), textAlign = TextAlign.Center)
        }
        Row(
          Modifier.widthIn(max = 320.dp).fillMaxWidth().background(Zinc100, CircleShape).hairline(Zinc200).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(state.upiId, style = twText(14f, FontWeight.SemiBold, 20.sp), color = Zinc800, modifier = Modifier.weight(1f), maxLines = 1)
          Row(
            Modifier.shadow(2.dp, CircleShape).background(Color.White, CircleShape).tappable(pressedScale = 0.94f, onClick = onCopy).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
          ) {
            Symbol(if (copied) "check" else "content_copy", size = 14.dp, tint = NudgeColors.PrimaryContainer)
            Text(if (copied) "COPIED" else "COPY", style = twText(12f, FontWeight.Bold, 16.sp, 0.05f), color = NudgeColors.PrimaryContainer)
          }
        }
      }

      GhostButton("Open UPI app", onOpenApp, Modifier.fillMaxWidth(), color = NudgeColors.Primary, icon = "open_in_new")

      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.animateContentSize()) {
        Text("After paying, type the 12-digit reference number (UTR / UPI Ref No.) shown in your UPI app", style = twText(12f, FontWeight.Medium, 16.sp), color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        Row(
          Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainer, CircleShape).hairline(Color.White.copy(alpha = 0.1f)).padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
          Symbol("pin", size = 20.dp, tint = NudgeColors.OnSurfaceVariant)
          BasicTextField(
            value = utr, onValueChange = { onUtrChange(it.filter(Char::isDigit).take(12)) }, singleLine = true,
            textStyle = twText(16f, lineHeight = 24.sp, tracking = 0.05f).copy(fontFamily = FontFamily.Monospace, color = NudgeColors.OnSurface),
            cursorBrush = SolidColor(NudgeColors.Primary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (canSubmit) onPaid() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (utr.isEmpty()) Text("e.g. 408271829102", style = twText(16f, lineHeight = 24.sp, tracking = 0.05f).copy(fontFamily = FontFamily.Monospace), color = NudgeColors.OutlineVariant); inner() } },
          )
          Text("${utr.length}/12", style = NudgeType.BodySm, color = if (utr.length == 12) NudgeColors.Primary else NudgeColors.Outline)
        }
      }
    }
    Column(
      Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PrimaryButton("I have paid", onPaid, Modifier.fillMaxWidth(), icon = "arrow_forward", enabled = canSubmit)
      Text(state.footnote, style = twText(12f, FontWeight.Medium, 16.sp), color = NudgeColors.OnSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
  }
}
