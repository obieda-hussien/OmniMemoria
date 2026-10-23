package com.omnimemoria.data.intelligence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.ImageDecoder
import android.graphics.ColorSpace
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.palette.graphics.Palette
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import com.omnimemoria.data.local.db.PhotoIntelligence
import com.omnimemoria.domain.intelligence.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** One bounded image and one native OCR instance at a time across all workers. */
@Singleton
class PhotoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arabicModel: ArabicModelStore,
    private val paddleModels: PaddleOcrModelStore
) {
    private val mutex = Mutex()
    private var text: TextRecognizer? = null
    private var labels: ImageLabeler? = null
    private var faces: FaceDetector? = null
    private var arabic: TessBaseAPI? = null
    private var paddle: PaddleOCR? = null

    suspend fun analyze(image: IndexImage, old: PhotoIntelligence?, required: Int, arabicReady: Boolean,
        analysisVersion: Int = AnalysisPolicy.VERSION): PhotoIntelligence = withContext(Dispatchers.IO) {
        mutex.withLock {
            val unchanged = old != null && old.sourceModified == image.modified && old.sourceSize == image.size && old.analysisVersion == analysisVersion
            val base = if (unchanged) old!! else PhotoIntelligence(image.id, "", "", false, false, false, "", "", "", indexedAt = null, embeddingId = null)
            var result = base
            var mask = base.completedMask
            var ocrLines = OcrRegions.read(base.ocrBoxes)
            val errors = mutableListOf<String>()
            val bitmap = decode(image)
            try {
                val input = InputImage.fromBitmap(bitmap, 0)
                suspend fun stage(bit: Int, name: String, action: suspend () -> Unit) {
                    currentCoroutineContext().ensureActive()
                    if (required and bit == 0 || mask and bit != 0) return
                    try { action(); mask = mask or bit }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        Log.e("OmniPhotoAnalyzer", "$name failed for photo ${image.id}", error)
                        val detail = error.message?.takeIf { !it.contains("/data/") && !it.contains("https://") }?.take(90)
                        errors += if (detail.isNullOrBlank()) "$name failed (${error.javaClass.simpleName}); retry from photo details or Settings."
                            else "$name failed (${error.javaClass.simpleName}: $detail); retry from photo details or Settings."
                    }
                    catch (error: UnsatisfiedLinkError) {
                        Log.e("OmniPhotoAnalyzer", "$name native engine is unavailable for photo ${image.id}", error)
                        errors += "$name is unavailable on this device (native library loading failed)."
                    }
                }
                stage(AnalysisPolicy.TEXT, "Text recognition") {
                    val engine = text ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { text = it }
                    // Scan the original and overlapping native-resolution crops. This rescues
                    // small print and long URLs that disappear when a 4K page is downscaled.
                    val latin = recognizeLatin(engine, bitmap)
                    ocrLines = ocrLines.filter { it.language == "arabic" || it.language == "code" } + latin
                    result = result.copy(rawText = OcrText.text(ocrLines), ocrBoxes = OcrRegions.write(bitmap.width, bitmap.height, ocrLines))
                }
                stage(AnalysisPolicy.CODES, "QR and barcode recognition") {
                    val codes = PhotoCodeScanner.scan(bitmap)
                    ocrLines = (ocrLines.filterNot { it.language == "code" } + codes).take(1500)
                    result = result.copy(rawText = OcrText.text(ocrLines), ocrBoxes = OcrRegions.write(bitmap.width, bitmap.height, ocrLines))
                }
                stage(AnalysisPolicy.ARABIC, "Arabic and English OCR") {
                    if (!arabicReady) throw IOException("Arabic OCR model is not installed")
                    if (paddleModels.isInstalled()) {
                        var paddleFailure: Throwable? = null
                        val recognized = try {
                            if (!OpenCVUtils.init(context)) throw IOException("OpenCV initialization failed")
                            val engine = paddle ?: PaddleOCR.create(
                                context = context,
                                config = PaddleOCRConfig(
                                    detLimitSideLen = 1280,
                                    detLimitType = "max",
                                    detMaxSideLimit = 1600,
                                    detThresh = 0.25f,
                                    detBoxThresh = 0.50f,
                                    recScoreThresh = 0.35f,
                                    recBatchSize = 1
                                ),
                                engineConfig = EngineConfig(numThreads = 2),
                                detModelAssetPath = paddleModels.detector.absolutePath,
                                recModelAssetPath = paddleModels.recognizer.absolutePath,
                                recConfigAssetPath = paddleModels.recognitionConfig.absolutePath
                            ).also { paddle = it }
                            recognizePaddle(engine, bitmap)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            paddleFailure = error
                            Log.e("OmniPhotoAnalyzer", "PaddleOCR inference failed for photo ${image.id}", error)
                            emptyList()
                        } catch (error: UnsatisfiedLinkError) {
                            paddleFailure = error
                            Log.e("OmniPhotoAnalyzer", "PaddleOCR native runtime failed for photo ${image.id}", error)
                            emptyList()
                        }

                        // Mobile ONNX model or native runtime can fail on device-specific builds.
                        // Fall back to the installed Tesseract Arabic model instead of turning the
                        // whole analysis into a dead end; keep ML Kit and QR results in either case.
                        val hasArabic = recognized.any { OcrText.arabicLetters(it.text) >= 2 }
                        val legacy = if ((paddleFailure != null || !hasArabic) && arabicModel.isInstalled()) {
                            val legacyEngine = arabic ?: TessBaseAPI().also { instance ->
                                if (!instance.init(arabicModel.root.absolutePath, "ara", TessBaseAPI.OEM_LSTM_ONLY)) {
                                    instance.recycle()
                                    throw IOException("PaddleOCR failed and Arabic fallback initialization failed", paddleFailure ?: IOException("Unknown PaddleOCR failure"))
                                }
                                arabic = instance
                            }
                            ArabicOcr.recognize(
                                legacyEngine,
                                bitmap,
                                ocrLines.filter { it.language != "arabic" && it.language != "code" }
                            )
                        } else emptyList()

                        if (paddleFailure != null && legacy.isEmpty()) {
                            throw IOException(
                                "PaddleOCR failed and no working Arabic fallback produced text",
                                paddleFailure ?: IOException("Unknown PaddleOCR failure")
                            )
                        }

                        if (recognized.isNotEmpty() || legacy.isNotEmpty()) {
                            val preferred = recognized + legacy
                            val fallback = ocrLines.filter { existing ->
                                existing.language == "code" || preferred.none { candidate ->
                                    val smaller = minOf(candidate.area, existing.area).coerceAtLeast(0.001f)
                                    candidate.overlap(existing) / smaller >= 0.35f &&
                                        candidate.confidence >= existing.confidence
                                }
                            }
                            ocrLines = preferred + fallback
                        }
                    } else {
                        val engine = arabic ?: TessBaseAPI().also { instance ->
                            if (!instance.init(arabicModel.root.absolutePath, "ara", TessBaseAPI.OEM_LSTM_ONLY)) {
                                instance.recycle()
                                throw IOException("Arabic OCR initialization failed")
                            }
                            arabic = instance
                        }
                        val arabicLines = ArabicOcr.recognize(
                            engine,
                            bitmap,
                            ocrLines.filter { it.language != "arabic" && it.language != "code" }
                        )
                        ocrLines = ocrLines.filter { it.language != "arabic" } + arabicLines +
                            ocrLines.filter { it.language == "code" }
                    }
                    result = result.copy(rawText = OcrText.text(ocrLines), ocrBoxes = OcrRegions.write(bitmap.width, bitmap.height, ocrLines))
                }
                stage(AnalysisPolicy.LABELS, "Image classification") {
                    val engine = labels ?: ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).also { labels = it }
                    val found = withContext(NonCancellable) { engine.process(input).await() }
                    result = result.copy(labels = found.filter { it.confidence >= 0.6f }.sortedByDescending { it.confidence }.take(20).joinToString(", ") { it.text })
                }
                stage(AnalysisPolicy.FACES, "Face detection") {
                    val engine = faces ?: FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()).also { faces = it }
                    result = result.copy(hasFaces = withContext(NonCancellable) { engine.process(input).await() }.isNotEmpty())
                }
                stage(AnalysisPolicy.COLORS, "Color analysis") {
                    val palette = Palette.from(bitmap).resizeBitmapArea(128 * 128).maximumColorCount(8).generate()
                    val swatches = palette.swatches.sortedByDescending { it.population }
                    result = result.copy(dominantColorHex = swatches.getOrNull(0)?.rgb?.let { hex(it) }.orEmpty(), secondaryColorHex = swatches.getOrNull(1)?.rgb?.let { hex(it) }.orEmpty())
                }
                stage(AnalysisPolicy.HASH, "Photo fingerprint") {
                    val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
                    try {
                        val pixels = IntArray(1024); scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                        result = result.copy(pHash = PerceptualHash.compute(DoubleArray(1024) { i ->
                            val pixel = pixels[i]; 0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
                        }))
                    } finally { if (scaled !== bitmap) scaled.recycle() }
                }
                currentCoroutineContext().ensureActive()
                result.copy(completedMask = mask, sourceModified = image.modified, sourceSize = image.size,
                    analysisVersion = analysisVersion, indexedAt = System.currentTimeMillis(), isIndexed = mask and required == required,
                    hasPhoneNumber = EntityExtractor.phones(result.rawText).isNotEmpty(), hasEmail = EntityExtractor.emails(result.rawText).isNotEmpty(),
                    indexError = if (required and AnalysisPolicy.ARABIC != 0 && !arabicReady) "Download the Arabic OCR model in Settings." else errors.joinToString(" "))
            } finally { bitmap.recycle() }
        }
    }

    suspend fun release() = withContext(NonCancellable + Dispatchers.IO) {
        val paddleToRelease = mutex.withLock {
            text?.close(); text = null
            labels?.close(); labels = null
            faces?.close(); faces = null
            arabic?.recycle(); arabic = null
            paddle.also { paddle = null }
        }
        paddleToRelease?.release()
    }

    /**
     * Keeps 4K pages sharp for detectors that resize their input. A full-page pass is kept
     * for ML Kit's reading context; every large image is additionally scanned in overlapping
     * 1280px crops so small print is not shrunk to a handful of pixels.
     */
    private fun recognitionRegions(bitmap: Bitmap): List<Rect> {
        val width = bitmap.width
        val height = bitmap.height
        val full = Rect(0, 0, width, height)
        if (maxOf(width, height) <= 1800 && width.toLong() * height <= 2_500_000L) return listOf(full)
        val edge = 1280
        fun starts(length: Int): List<Int> {
            if (length <= edge) return listOf(0)
            val count = kotlin.math.ceil(length.toDouble() / edge).toInt()
            val last = length - edge
            return (0 until count).map { index ->
                if (count == 1) 0 else (last.toDouble() * index / (count - 1)).roundToInt()
            }.distinct()
        }
        val tiles = starts(width).flatMap { left ->
            starts(height).map { top -> Rect(left, top, minOf(width, left + edge), minOf(height, top + edge)) }
        }
        return listOf(full) + tiles
    }

    private suspend fun recognizeLatin(engine: TextRecognizer, bitmap: Bitmap): List<OcrLine> {
        val lines = mutableListOf<OcrLine>()
        for (region in recognitionRegions(bitmap)) {
            currentCoroutineContext().ensureActive()
            val tile = if (region.left == 0 && region.top == 0 &&
                region.width() == bitmap.width && region.height() == bitmap.height
            ) bitmap else Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
            try {
                val input = InputImage.fromBitmap(tile, 0)
                val found = withContext(NonCancellable) { engine.process(input).await() }
                found.textBlocks.flatMap { it.lines }.take(1000).forEach { line ->
                    val box = line.boundingBox ?: return@forEach
                    val mapped = OcrLine(
                        OcrText.normalize(line.text),
                        (region.left + box.left).toFloat() / bitmap.width,
                        (region.top + box.top).toFloat() / bitmap.height,
                        (region.left + box.right).toFloat() / bitmap.width,
                        (region.top + box.bottom).toFloat() / bitmap.height,
                        "latin",
                        80f
                    )
                    if (mapped.isValid()) lines += mapped
                }
            } finally {
                if (tile !== bitmap) tile.recycle()
            }
        }
        return OcrText.resolve(lines)
    }

    private suspend fun recognizePaddle(engine: PaddleOCR, bitmap: Bitmap): List<OcrLine> {
        val allRegions = recognitionRegions(bitmap)
        val regions = if (allRegions.size > 1) allRegions.drop(1) else allRegions
        val lines = mutableListOf<OcrLine>()
        for (region in regions) {
            currentCoroutineContext().ensureActive()
            val tile = if (region.left == 0 && region.top == 0 &&
                region.width() == bitmap.width && region.height() == bitmap.height
            ) bitmap else Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
            try {
                val found = withContext(NonCancellable) { engine.recognize(tile) }
                found.results.take(1000).forEach { item ->
                    val text = OcrText.normalize(item.text)
                    val points = item.box.points
                    if (points.isEmpty() || text.isBlank() || !text.any(Char::isLetterOrDigit)) return@forEach
                    val left = (region.left + points.minOf { it.x }.coerceIn(0f, tile.width.toFloat())) / bitmap.width
                    val top = (region.top + points.minOf { it.y }.coerceIn(0f, tile.height.toFloat())) / bitmap.height
                    val right = (region.left + points.maxOf { it.x }.coerceIn(0f, tile.width.toFloat())) / bitmap.width
                    val bottom = (region.top + points.maxOf { it.y }.coerceIn(0f, tile.height.toFloat())) / bitmap.height
                    val confidence = (item.confidence * 100f).coerceIn(0f, 100f)
                    val script = if (OcrText.arabicLetters(text) >= 2) "arabic" else "latin"
                    val line = OcrLine(text, left, top, right, bottom, script, confidence)
                    if (line.isValid() && confidence >= 30f) lines += line
                }
            } finally {
                if (tile !== bitmap) tile.recycle()
            }
        }
        return OcrText.resolve(lines)
    }

    private fun decode(image: IndexImage): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, image.uri)) { decoder, info, _ ->
            val (width, height) = OcrImageSizing.target(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(image.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Image cannot be decoded")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = resolver.openInputStream(image.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: throw IOException("Image is unavailable")
        try {
            val exif = resolver.openInputStream(image.uri)?.use { ExifInterface(it) }
            if (exif == null || (exif.rotationDegrees == 0 && !exif.isFlipped)) return bitmap
            val matrix = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(exif.rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        } catch (error: Exception) { bitmap.recycle(); throw error }
    }
    private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)
}
