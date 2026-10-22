package com.omnimemoria.data.intelligence

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.omnimemoria.domain.intelligence.OcrLine
import kotlin.math.max
import kotlin.math.min

/**
 * Finds QR codes and other common barcodes independently of OCR. The decoded payload is
 * stored alongside recognized text, so URLs encoded in a QR are searchable and actionable.
 */
internal object PhotoCodeScanner {
    private const val TILE_EDGE = 1280
    private const val MAX_PAYLOAD_CHARS = 2_048

    private val hints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR
        )
    )

    fun scan(bitmap: Bitmap): List<OcrLine> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val detections = linkedMapOf<String, OcrLine>()
        val regions = regions(bitmap.width, bitmap.height)
        for (region in regions) {
            val tile = if (region.left == 0 && region.top == 0 &&
                region.width() == bitmap.width && region.height() == bitmap.height
            ) bitmap else Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
            try {
                decode(tile).forEach { result ->
                    val payload = result.text.orEmpty().trim().take(MAX_PAYLOAD_CHARS)
                    if (payload.isBlank()) return@forEach
                    val points = result.resultPoints
                    val xs = points.map { it.x }.filter(Float::isFinite)
                    val ys = points.map { it.y }.filter(Float::isFinite)
                    val padX = max(8f, tile.width * 0.008f)
                    val padY = max(8f, tile.height * 0.008f)
                    val rawLeft = region.left + (xs.minOrNull() ?: 0f) - padX
                    val rawTop = region.top + (ys.minOrNull() ?: 0f) - padY
                    val rawRight = region.left + (xs.maxOrNull() ?: tile.width.toFloat()) + padX
                    val rawBottom = region.top + (ys.maxOrNull() ?: tile.height.toFloat()) + padY
                    val left = (rawLeft / bitmap.width).coerceIn(0f, 1f)
                    val top = (rawTop / bitmap.height).coerceIn(0f, 1f)
                    val right = (rawRight / bitmap.width).coerceIn(0f, 1f)
                    val bottom = (rawBottom / bitmap.height).coerceIn(0f, 1f)
                    val safeRight = max(right, min(1f, left + 0.004f))
                    val safeBottom = max(bottom, min(1f, top + 0.004f))
                    val line = OcrLine("QR code: $payload", left, top, safeRight, safeBottom, "code", 100f)
                    if (line.isValid()) detections.putIfAbsent(payload, line)
                }
            } finally {
                if (tile !== bitmap) tile.recycle()
            }
        }
        return detections.values.take(50)
    }

    private fun decode(bitmap: Bitmap): List<com.google.zxing.Result> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val normal = decodeSource(source)
        // Inverted QR codes are common in dark UI screenshots and posters.
        return if (normal.isNotEmpty()) normal else decodeSource(source.invert())
    }

    private fun decodeSource(source: com.google.zxing.LuminanceSource): List<com.google.zxing.Result> {
        val binary = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        reader.setHints(hints)
        val multiple = runCatching { GenericMultipleBarcodeReader(reader).decodeMultiple(binary, hints).toList() }
            .getOrDefault(emptyList())
        if (multiple.isNotEmpty()) {
            reader.reset()
            return multiple
        }
        reader.reset()
        reader.setHints(hints)
        return try {
            listOf(reader.decodeWithState(binary))
        } catch (_: NotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            reader.reset()
        }
    }

    private fun regions(width: Int, height: Int): List<Rect> {
        val full = Rect(0, 0, width, height)
        if (max(width, height) <= TILE_EDGE || width.toLong() * height <= 1_600_000L) return listOf(full)

        fun starts(length: Int): List<Int> {
            if (length <= TILE_EDGE) return listOf(0)
            val count = kotlin.math.ceil(length.toDouble() / TILE_EDGE).toInt()
            val last = length - TILE_EDGE
            return (0 until count).map { index ->
                if (count == 1) 0 else (last.toDouble() * index / (count - 1)).toInt()
            }.distinct()
        }
        val tiles = starts(width).flatMap { left ->
            starts(height).map { top ->
                Rect(left, top, min(width, left + TILE_EDGE), min(height, top + TILE_EDGE))
            }
        }
        return listOf(full) + tiles
    }
}
