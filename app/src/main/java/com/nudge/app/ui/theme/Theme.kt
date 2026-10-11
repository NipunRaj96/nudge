package com.nudge.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Tailwind radius scale from the Stitch config: DEFAULT 1rem, lg 2rem, xl 3rem, full pill. */
object NudgeShapes {
  val Default = RoundedCornerShape(16.dp)
  val Lg = RoundedCornerShape(32.dp)
  val Xl = RoundedCornerShape(48.dp)
  val Full = RoundedCornerShape(percent = 50)
}

object NudgeSpacing {
  val Gutter = 16.dp
  val StackSm = 8.dp
  val StackMd = 16.dp
  val StackLg = 32.dp
  val MarginMain = 24.dp
}

private val scheme = darkColorScheme(
  primary = NudgeColors.Primary, onPrimary = NudgeColors.OnPrimary,
  primaryContainer = NudgeColors.PrimaryContainer, onPrimaryContainer = NudgeColors.OnPrimaryContainer,
  inversePrimary = NudgeColors.InversePrimary,
  secondary = NudgeColors.Secondary, onSecondary = NudgeColors.OnSecondary,
  secondaryContainer = NudgeColors.SecondaryContainer, onSecondaryContainer = NudgeColors.OnSecondaryContainer,
  tertiary = NudgeColors.Tertiary, onTertiary = NudgeColors.OnTertiary,
  tertiaryContainer = NudgeColors.TertiaryContainer, onTertiaryContainer = NudgeColors.OnTertiaryContainer,
  background = NudgeColors.Background, onBackground = NudgeColors.OnBackground,
  surface = NudgeColors.Surface, onSurface = NudgeColors.OnSurface,
  surfaceVariant = NudgeColors.SurfaceVariant, onSurfaceVariant = NudgeColors.OnSurfaceVariant,
  surfaceTint = NudgeColors.SurfaceTint,
  inverseSurface = NudgeColors.InverseSurface, inverseOnSurface = NudgeColors.InverseOnSurface,
  error = NudgeColors.Error, onError = NudgeColors.OnError,
  errorContainer = NudgeColors.ErrorContainer, onErrorContainer = NudgeColors.OnErrorContainer,
  outline = NudgeColors.Outline, outlineVariant = NudgeColors.OutlineVariant,
  surfaceBright = NudgeColors.SurfaceBright, surfaceDim = NudgeColors.SurfaceDim,
  surfaceContainerLowest = NudgeColors.SurfaceContainerLowest, surfaceContainerLow = NudgeColors.SurfaceContainerLow,
  surfaceContainer = NudgeColors.SurfaceContainer, surfaceContainerHigh = NudgeColors.SurfaceContainerHigh,
  surfaceContainerHighest = NudgeColors.SurfaceContainerHighest,
)

@Composable
fun NudgeTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = scheme,
    typography = Typography(
      displayLarge = NudgeType.DisplayLg, headlineMedium = NudgeType.HeadlineMd,
      bodyLarge = NudgeType.BodyLg, bodyMedium = NudgeType.BodySm, labelMedium = NudgeType.LabelCaps,
    ),
    shapes = Shapes(),
    content = content,
  )
}
