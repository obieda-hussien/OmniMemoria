package com.omnimemoria.data.intelligence

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import com.omnimemoria.domain.intelligence.OcrImageSizing
import com.omnimemoria.domain.intelligence.OcrLine
import com.omnimemoria.domain.intelligence.OcrText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Bounded page OCR, with line crops only when the page layout missed Arabic. */
internal object ArabicOcr {
    private data class Pass(val lines: List<OcrLine>, val confidence: Int)

    suspend fun recognize(engine: TessBaseAPI, image: Bitmap, latin: List<OcrLine>): List<OcrLine> {
        val prepared = prepare(image)
        try {
            val page = read(engine, prepared, TessBaseAPI.PageSegMode.PSM_AUTO)
            val arabic = page.lines.filter { it.confidence >= 40 && OcrText.arabicLetters(it.text) >= 2 }
            if (arabic.sumOf { OcrText.arabicLetters(it.text) } >= 80 && page.confidence >= 55) return page.lines
            currentCoroutineContext().ensureActive()
            val sparse = read(engine, prepared, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            val candidates = page.lines + sparse.lines
            // A Latin-only photo should not trigger dozens of Arabic line jobs.
            if (candidates.none { it.confidence >= 40 && OcrText.arabicLetters(it.text) >= 4 }) return candidates
            val cropped = mutableListOf<OcrLine>()
            for (line in latin.filter { it.isValid() && it.bottom - it.top < 0.15f && it.right - it.left > 0.08f }.take(48)) {
                currentCoroutineContext().ensureActive()
                val x = (line.left * prepared.width - 8).toInt().coerceIn(0, prepared.width - 1)
                val y = (line.top * prepared.height - 8).toInt().coerceIn(0, prepared.height - 1)
                val right = (line.right * prepared.width + 8).roundToInt().coerceIn(x + 1, prepared.width)
                val bottom = (line.bottom * prepared.height + 8).roundToInt().coerceIn(y + 1, prepared.height)
                val border = 12
                val tile = Bitmap.createBitmap(right - x + border * 2, bottom - y + border * 2, Bitmap.Config.ARGB_8888)
                try {
                    Canvas(tile).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(prepared, android.graphics.Rect(x, y, right, bottom),
                            android.graphics.Rect(border, border, tile.width - border, tile.height - border), null)
                    }
                    val found = read(engine, tile, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
                    cropped += found.lines.map { result -> result.copy(
                        left = ((x + result.left * tile.width - border) / prepared.width).coerceIn(0f, 1f),
                        right = ((x + result.right * tile.width - border) / prepared.width).coerceIn(0f, 1f),
                        top = ((y + result.top * tile.height - border) / prepared.height).coerceIn(0f, 1f),
                        bottom = ((y + result.bottom * tile.height - border) / prepared.height).coerceIn(0f, 1f)) }
                } finally { tile.recycle() }
            }
            return cropped + candidates
        } finally { prepared.recycle() }
    }

    private fun read(engine: TessBaseAPI, bitmap: Bitmap, mode: Int): Pass {
        engine.setPageSegMode(mode)
        try {
            engine.setImage(bitmap)
            if (engine.utF8Text.isNullOrBlank()) return Pass(emptyList(), 0)
            val confidence = engine.meanConfidence()
            val iterator = engine.resultIterator ?: return Pass(emptyList(), confidence)
            val lines = mutableListOf<OcrLine>()
            try {
                iterator.begin()
                do {
                    val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE) ?: continue
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE).orEmpty()
                    val line = OcrLine(text, box.left.toFloat()/bitmap.width, box.top.toFloat()/bitmap.height,
                        box.right.toFloat()/bitmap.width, box.bottom.toFloat()/bitmap.height, "arabic",
                        iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                    if (line.isValid()) lines += line
                } while (lines.size < 1000 && iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            } finally { iterator.delete() }
            return Pass(lines, confidence)
        } finally { engine.clear() }
    }

    private fun prepare(image: Bitmap): Bitmap {
        val sample = Bitmap.createScaledBitmap(image, 24, 24, true)
        val pixels = IntArray(24 * 24)
        try { sample.getPixels(pixels, 0, 24, 0, 0, 24, 24) } finally { if (sample !== image) sample.recycle() }
        val average = pixels.map { 0.299 * Color.red(it) + 0.587 * Color.green(it) + 0.114 * Color.blue(it) }.average()
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        // Preserve faint document text. Only invert predominantly dark pages.
        if (average < 110) matrix.postConcat(ColorMatrix(floatArrayOf(
            -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)))
        val (width, height) = OcrImageSizing.target(image.width, image.height, enlargeSmallText = true)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(image, null, android.graphics.Rect(0, 0, width, height),
                Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        }
    }
}
