package com.nudge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Real, scannable QR code drawn on a Canvas (ZXing encodes; we only paint the modules). */
@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier, dark: Color = Color(0xFF09090B), light: Color = Color.White) {
  val matrix = remember(content) {
    QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
  }
  Canvas(modifier.size(size)) {
    drawRect(light)
    val n = matrix.width
    val cell = this.size.width / n
    for (y in 0 until n) for (x in 0 until n) if (matrix[x, y]) {
      drawRect(dark, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
    }
  }
}
