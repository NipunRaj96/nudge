package com.nudge.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nudge.app.R

@OptIn(ExperimentalTextApi::class)
private val OutlinedFamily = FontFamily(Font(R.font.material_symbols_subset, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 0f))))

@OptIn(ExperimentalTextApi::class)
private val FilledFamily = FontFamily(Font(R.font.material_symbols_subset, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 1f))))

/**
 * Material Symbols Outlined glyph by name (the same icon set + FILL axis the Stitch HTML uses). The font is a ~20 KB
 * subset (wght 400, GRAD 0, opsz 24) containing only the icons we use — regenerate it with tools/subset_symbols.py
 * after adding a new icon name, otherwise the icon renders as text.
 */
@Composable
fun Symbol(
  name: String,
  modifier: Modifier = Modifier,
  size: Dp = 24.dp,
  tint: Color = LocalContentColor.current,
  filled: Boolean = false,
) {
  // Debug builds fail loudly so a missing glyph is caught during development; release shows a blank space, never crashes.
  val glyph = remember(name) {
    SymbolGlyphs[name] ?: if (com.nudge.app.BuildConfig.DEBUG) error("Icon '$name' missing from the Symbols subset; run tools/subset_symbols.py") else ""
  }
  val sp = with(LocalDensity.current) { size.toSp() } // icons are fixed dp, not font-scaled
  Text(
    text = glyph,
    modifier = modifier.size(size),
    color = tint,
    textAlign = TextAlign.Center,
    maxLines = 1,
    softWrap = false,
    style = TextStyle(
      fontFamily = if (filled) FilledFamily else OutlinedFamily,
      fontSize = sp,
      lineHeight = sp,
      lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    ),
  )
}
