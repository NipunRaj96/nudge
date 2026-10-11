package com.nudge.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nudge.app.R

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) =
  Font(R.font.plus_jakarta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val PlusJakartaSans = FontFamily(
  jakarta(FontWeight.Normal), jakarta(FontWeight.Medium), jakarta(FontWeight.SemiBold),
  jakarta(FontWeight.Bold), jakarta(FontWeight.ExtraBold),
)

/** Stitch type scale (design.md): display-lg, headline-md, body-lg, body-sm, label-caps. */
object NudgeType {
  val DisplayLg = TextStyle(fontFamily = PlusJakartaSans, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.Bold)
  val HeadlineMd = TextStyle(fontFamily = PlusJakartaSans, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em, fontWeight = FontWeight.SemiBold)
  val BodyLg = TextStyle(fontFamily = PlusJakartaSans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
  val BodySm = TextStyle(fontFamily = PlusJakartaSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
  val LabelCaps = TextStyle(fontFamily = PlusJakartaSans, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.em, fontWeight = FontWeight.SemiBold)
}
