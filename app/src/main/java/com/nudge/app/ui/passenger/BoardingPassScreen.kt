package com.nudge.app.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.data.Ride
import com.nudge.app.data.RideStatus
import com.nudge.app.ui.components.GhostButton
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.QrCode
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.StatusChip
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

private val Radius32 = RoundedCornerShape(32.dp)

/** The rider shows this to the driver: big name first (driver taps the name), QR as a backup. */
@Composable
fun BoardingPassScreen(
  ride: Ride,
  passengerName: String,
  qrPayload: String,
  onClose: () -> Unit,
  onTrack: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  /** What happens to the ride/money, e.g. "The ride goes back to your pack. Payments are not refunded." */
  cancelNote: String = "Payments are not refunded.",
  canCancel: Boolean = true,
  cutoffMin: Int = 15,
) {
  val upcoming = ride.status == RideStatus.Upcoming
  var confirmCancel by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
  if (confirmCancel) com.nudge.app.ui.components.ConfirmDialog(
    "Cancel this ride?", cancelNote, "Cancel ride", onCancel, { confirmCancel = false }, dismiss = "Keep my seat",
  )
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Boarding pass", onBack = onClose)
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        Modifier.fillMaxWidth().shadow(24.dp, Radius32).background(Color.White, Radius32).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        QrCode(qrPayload, 184.dp)
        Text(passengerName, style = twText(28f, FontWeight.ExtraBold, 34.sp, -0.02f), color = Color(0xFF09090B), textAlign = TextAlign.Center)
        when (ride.status) {
          RideStatus.Upcoming -> if (ride.paymentPending) StatusChip("SEAT HELD · PAYMENT IN REVIEW", Color(0xFFFEF3C7), Color(0xFF92400E), icon = "hourglass_top")
            else StatusChip("CONFIRMED", Color(0xFFDCFCE7), Color(0xFF166534), icon = "check_circle")
          RideStatus.Completed -> StatusChip("COMPLETED", Color(0xFFE4E4E7), Color(0xFF3F3F46), icon = "check_circle")
          RideStatus.NoShow -> StatusChip("NO-SHOW", Color(0xFFFEE2E2), Color(0xFF991B1B), icon = "error")
          RideStatus.Cancelled -> StatusChip("CANCELLED", Color(0xFFE4E4E7), Color(0xFF3F3F46), icon = "cancel")
        }
      }
      Column(
        Modifier.fillMaxWidth().background(NudgeColors.SurfaceContainerLow, Radius32).hairline(Color.White.copy(alpha = 0.05f), Radius32).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Detail("route", "${ride.routeName} · ${ride.shift}")
        Detail("schedule", "${ride.dateLabel} · Pickup ${ride.time}")
        Detail("location_on", ride.pickup)
        Detail("airport_shuttle", "Shuttle ${ride.shuttleCode}")
      }
    }
    if (upcoming) Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      PrimaryButton("Track shuttle", onTrack, Modifier.fillMaxWidth(), icon = "near_me")
      if (canCancel) GhostButton("Cancel ride", { confirmCancel = true }, Modifier.fillMaxWidth(), color = NudgeColors.Error)
      else Text("Cancelling closes $cutoffMin min before pickup.", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
  }
}

@Composable
private fun Detail(icon: String, text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Symbol(icon, size = 22.dp, tint = NudgeColors.Primary)
    Text(text, style = twText(16f, FontWeight.Medium, 24.sp), color = NudgeColors.OnSurface, modifier = Modifier.weight(1f))
  }
}
