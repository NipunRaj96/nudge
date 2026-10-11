package com.nudge.app.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain text of a PDF the operator picked, one table row per line (text sorted by position on the page). */
suspend fun pdfText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
  PDFBoxResourceLoader.init(context.applicationContext)
  context.contentResolver.openInputStream(uri)?.use { input ->
    PDDocument.load(input).use { doc -> PDFTextStripper().apply { sortByPosition = true }.getText(doc) }
  } ?: throw BackendError("Couldn't open that file.")
}
