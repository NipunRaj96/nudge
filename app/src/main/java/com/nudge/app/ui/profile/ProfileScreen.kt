package com.nudge.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.components.GhostButton
import com.nudge.app.ui.components.InitialsAvatar
import com.nudge.app.ui.components.ScreenHeader
import com.nudge.app.ui.components.Symbol
import com.nudge.app.ui.components.hairline
import com.nudge.app.ui.components.tappable
import com.nudge.app.ui.components.twText
import com.nudge.app.ui.theme.NudgeColors
import com.nudge.app.ui.theme.NudgeType

data class ProfileRow(val icon: String, val label: String, val onClick: () -> Unit)

/** Optional highlight card (rider pass). */
data class ProfileHighlight(val title: String, val subtitle: String, val action: String?, val progress: Float? = null, val onAction: () -> Unit)

private val Radius32 = RoundedCornerShape(32.dp)

@Composable
fun ProfileScreen(
  name: String,
  phone: String,
  roleLabel: String,
  highlight: ProfileHighlight?,
  rows: List<ProfileRow>,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxSize().background(NudgeColors.Background)) {
    ScreenHeader("Profile")
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(name, 64.dp, Modifier.size(64.dp).clip(CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(name, style = NudgeType.HeadlineMd, color = NudgeColors.OnSurface)
          Text("+91 $phone · $roleLabel", style = NudgeType.BodySm, color = NudgeColors.OnSurfaceVariant)
        }
      }
      if (highlight != null) Column(
        Modifier.fillMaxWidth().background(NudgeColors.PrimaryContainer, Radius32).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(highlight.title, style = twText(18f, FontWeight.SemiBold, 28.sp), color = NudgeColors.OnPrimaryContainer)
        Text(highlight.subtitle, style = NudgeType.BodySm, color = NudgeColors.OnPrimaryContainer.copy(alpha = 0.85f))
        highlight.progress?.let { used -> // rides used, as in the Stitch profile pass card
          Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(NudgeColors.OnPrimaryContainer.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(used.coerceIn(0f, 1f)).height(4.dp).background(NudgeColors.OnPrimaryContainer))
          }
        }
        if (highlight.action != null) Text(
          highlight.action, style = twText(14f, FontWeight.SemiBold, 20.sp), color = NudgeColors.PrimaryContainer,
          modifier = Modifier.clip(CircleShape).background(NudgeColors.OnPrimaryContainer).tappable(onClick = highlight.onAction).padding(horizontal = 20.dp, vertical = 10.dp),
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
          Row(
            Modifier.fillMaxWidth().clip(Radius32).background(NudgeColors.SurfaceContainerLow).hairline(Color.White.copy(alpha = 0.05f), Radius32)
              .tappable(pressedScale = 0.98f, onClick = r.onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(40.dp).background(NudgeColors.SurfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Symbol(r.icon, size = 20.dp, tint = NudgeColors.Primary) }
            Text(r.label, style = twText(16f, FontWeight.Medium, 24.sp), color = NudgeColors.OnSurface, modifier = Modifier.weight(1f))
            Symbol("chevron_right", tint = NudgeColors.OnSurfaceVariant)
          }
        }
      }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
      var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
      if (confirm) com.nudge.app.ui.components.ConfirmDialog("Log out?", "You'll need an SMS code to log in again.", "Log out", onLogout, { confirm = false }, dismiss = "Stay")
      GhostButton("Log out", { confirm = true }, Modifier.fillMaxWidth(), color = NudgeColors.Error, icon = "logout")
    }
  }
}
