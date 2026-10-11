package com.nudge.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.PaymentRequest
import com.nudge.app.data.PaymentStatus
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.Segmented
import com.nudge.app.ui.components.StatusChip
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius32 = RoundedCornerShape(32.dp)
private val Green = Color(0xFF4ADE80)
private val Red = Color(0xFFF87171)

/** Operator approves/rejects manual UPI payments; "Export" saves the finance CSV. */
@Composable
fun AdminPaymentsScreen(
  payments: List<PaymentRequest>,
  onResolve: (PaymentRequest, Boolean) -> Unit,
  onExport: () -> Unit,
  modifier: Modifier = Modifier,
  plans: List<com.nudge.app.data.Plan> = emptyList(),
  busy: Boolean = false,
  onGrant: (phone: String, plan: com.nudge.app.data.Plan, amount: Int?, day: java.time.LocalDate?, done: () -> Unit) -> Unit = { _, _, _, _, _ -> },
) {
  var tab by remember { mutableStateOf(0) }
  var granting by remember { mutableStateOf(false) }
  var rejecting by remember { mutableStateOf<PaymentRequest?>(null) }
  rejecting?.let { p ->
    com.nudge.app.ui.components.ConfirmDialog(
      "Reject ${p.riderName}'s ₹${"%,d".format(p.amount)}?", "Do this only if the money did not arrive. Any seats they booked with this payment are cancelled.",
      "Reject", { onResolve(p, false) }, { rejecting = null },
    )
  }
  if (granting) GrantPassSheet(plans, busy, onDismiss = { granting = false }) { phone, plan, amount, day -> onGrant(phone, plan, amount, day) { granting = false } }
  val status = listOf(PaymentStatus.Pending, PaymentStatus.Approved, PaymentStatus.Rejected)[tab]
  val shown = payments.filter { it.status == status }
  val pending = payments.count { it.status == PaymentStatus.Pending }
  val collected = payments.filter { it.status == PaymentStatus.Approved }.sumOf { it.amount }
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Payments", trailing = {
      Row(
        Modifier.clip(CircleShape).background(NudgeColors.SurfaceContainerHigh).hairline().tappable(pressedScale = 0.95f, onClick = onExport).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
      ) {
        Symbol("download", size = 18.dp, tint = NudgeColors.Primary)
        Text("Export", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.Primary)
      }
    })
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Tile("To review", pending.toString(), NudgeColors.Primary, Modifier.weight(1f))
          Tile("Collected", "₹${"%,d".format(collected)}", NudgeColors.OnSurface, Modifier.weight(1f))
        }
      }
      item {
        com.nudge.app.ui.components.ListCard(
          "Got cash or paid outside the app?", "Give a rider their pass here (also handy for testing).", leadingIcon = "add_card", onClick = { granting = true },
        )
      }
      item { Segmented(listOf("Pending", "Approved", "Rejected").zip(listOf(PaymentStatus.Pending, PaymentStatus.Approved, PaymentStatus.Rejected)) { l, st -> payments.count { it.status == st }.let { if (it > 0) "$l ($it)" else l } }, tab, { tab = it }, Modifier.padding(vertical = 4.dp)) }
      if (shown.isEmpty()) item { Text("Nothing here.", style = NudgeType.BodyLg, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(16.dp)) }
      items(shown, key = { it.id }) { PaymentCard(it, Modifier.animateItem()) { p, ok -> if (ok) onResolve(p, true) else rejecting = p } }
    }
  }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
  Column(modifier.background(NudgeColors.SurfaceContainer, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
    Text(value, style = twText(28f, FontWeight.Bold, 36.sp, -0.02f), color = color, maxLines = 1)
  }
}

@Composable
private fun PaymentCard(p: PaymentRequest, modifier: Modifier, onResolve: (PaymentRequest, Boolean) -> Unit) {
  Column(
    modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(p.riderName, style = twText(16f, FontWeight.SemiBold, 24.sp), color = NudgeColors.OnSurface, maxLines = 1)
        Text("${p.planTitle} · ${p.timeLabel}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
        Text("${com.nudge.app.data.prettyPhone(p.phone)}${p.paidTo?.let { " · to $it" }.orEmpty()}", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, maxLines = 1)
      }
      Text("₹${"%,d".format(p.amount)}", style = twText(18f, FontWeight.Bold, 28.sp), color = NudgeColors.Primary)
    }
    Row(
      Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLowest, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(if (p.utr.isBlank()) "CASH" else "REF", style = twText(12f, FontWeight.SemiBold, 16.sp, 0.05f), color = NudgeColors.OnSurfaceVariant)
      Text(p.utr.ifBlank { "Recorded by operator" }, style = twText(14f, lineHeight = 20.sp, tracking = 0.05f).copy(fontFamily = FontFamily.Monospace), color = NudgeColors.OnSurface)
    }
    when (p.status) {
      PaymentStatus.Pending -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "Reject", style = twText(14f, FontWeight.SemiBold, 20.sp), color = Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.weight(1f).clip(CircleShape).hairline(Red.copy(alpha = 0.4f)).tappable { onResolve(p, false) }.padding(vertical = 12.dp),
        )
        Text(
          "Approve", style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.OnPrimaryContainer, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.weight(1f).clip(CircleShape).background(NudgeColors.PrimaryContainer).tappable { onResolve(p, true) }.padding(vertical = 12.dp),
        )
      }
      PaymentStatus.Approved -> StatusChip("APPROVED", Green.copy(alpha = 0.12f), Green, icon = "check_circle")
      PaymentStatus.Rejected -> StatusChip("REJECTED", Red.copy(alpha = 0.12f), Red, icon = "cancel")
    }
  }
}


/** Record a cash / offline payment: pick the rider by phone and a plan; their pass is active at once. */
@Composable
private fun GrantPassSheet(
  plans: List<com.nudge.app.data.Plan>, busy: Boolean, onDismiss: () -> Unit,
  onGrant: (String, com.nudge.app.data.Plan, Int?, java.time.LocalDate?) -> Unit,
) {
  var phone by remember { mutableStateOf("") }
  var plan by remember { mutableStateOf(plans.firstOrNull { it.popular } ?: plans.firstOrNull()) }
  var amount by remember(plan) { mutableStateOf(plan?.price?.toString().orEmpty()) }
  val valid = phone.length == 10 && phone.first() in "6789" && plan != null && amount.toIntOrNull() != null && !busy
  com.nudge.app.ui.components.EditorSheet(
    "Give a pass", onDismiss, { plan?.let { onGrant(phone, it, amount.toIntOrNull(), null) } }, valid, saveLabel = if (busy) "Saving…" else "Give pass",
  ) {
    com.nudge.app.ui.components.FormField("Rider's mobile number", phone, { phone = it.filter(Char::isDigit).takeLast(10) },
      keyboard = androidx.compose.ui.text.input.KeyboardType.Phone, prefix = "+91")
    Text("The rider must have logged in to Nudge once with this number.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    com.nudge.app.ui.components.SectionLabel("Plan")
    plans.forEach { p ->
      com.nudge.app.ui.components.ListCard(
        p.title, "₹${"%,d".format(p.price)} today", leadingIcon = if (p.id == plan?.id) "radio_button_checked" else "radio_button_unchecked", onClick = { plan = p },
      )
    }
    com.nudge.app.ui.components.FormField("Amount received", amount, { amount = it.filter(Char::isDigit).take(6) },
      keyboard = androidx.compose.ui.text.input.KeyboardType.Number, prefix = "₹")
  }
}
