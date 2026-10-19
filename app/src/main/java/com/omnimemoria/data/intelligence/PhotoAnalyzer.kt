package com.omnimemoria.data.intelligence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
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
import com.omnimemoria.data.local.db.PhotoIntelligence
import com.omnimemoria.domain.intelligence.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** One bounded image and one native OCR instance at a time across all workers. */
@Singleton
class PhotoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arabicModel: ArabicModelStore
) {
    private val mutex = Mutex()
    private var text: TextRecognizer? = null
    private var labels: ImageLabeler? = null
    private var faces: FaceDetector? = null
    private var arabic: TessBaseAPI? = null

    suspend fun analyze(image: IndexImage, old: PhotoIntelligence?, required: Int, arabicReady: Boolean): PhotoIntelligence = withContext(Dispatchers.IO) {
        mutex.withLock {
            val unchanged = old != null && old.sourceModified == image.modified && old.sourceSize == image.size && old.analysisVersion == AnalysisPolicy.VERSION
            val base = if (unchanged) old!! else PhotoIntelligence(image.id, "", "", false, false, false, "", "", "", indexedAt = null, embeddingId = null)
            var result = base
            var mask = base.completedMask
            val errors = mutableListOf<String>()
            val bitmap = decode(image)
            try {
                val input = InputImage.fromBitmap(bitmap, 0)
                suspend fun stage(bit: Int, name: String, action: suspend () -> Unit) {
                    currentCoroutineContext().ensureActive()
                    if (required and bit == 0 || mask and bit != 0) return
                    try { action(); mask = mask or bit }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { errors += "$name failed; retry from photo details or Settings." }
                    catch (error: UnsatisfiedLinkError) { errors += "$name is unavailable on this device." }
                }
                stage(AnalysisPolicy.TEXT, "Text recognition") {
                    val engine = text ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { text = it }
                    // ML Kit tasks do not own cancellation. Await completion before
                    // releasing their bitmap or closing the native client.
                    val found = withContext(NonCancellable) { engine.process(input).await() }
                    val boxes = JSONArray()
                    found.textBlocks.flatMap { it.lines }.take(500).forEach { line -> line.boundingBox?.let { box ->
                        boxes.put(JSONObject().put("text", line.text).put("left", box.left.toFloat()/bitmap.width)
                            .put("top", box.top.toFloat()/bitmap.height).put("right", box.right.toFloat()/bitmap.width)
                            .put("bottom", box.bottom.toFloat()/bitmap.height))
                    } }
                    val merged = if (mask and AnalysisPolicy.ARABIC != 0) OcrText.merge(result.rawText, found.text) else found.text.take(100_000)
                    result = result.copy(rawText = merged, ocrBoxes = JSONObject().put("width", bitmap.width).put("height", bitmap.height).put("lines", boxes).toString())
                }
                stage(AnalysisPolicy.ARABIC, "Arabic OCR") {
                    if (!arabicReady) throw IOException("Arabic model is not installed")
                    val engine = arabic ?: TessBaseAPI().also { instance ->
                        if (!instance.init(arabicModel.root.absolutePath, "ara")) { instance.recycle(); throw IOException("Arabic OCR initialization failed") }
                        arabic = instance
                    }
                    try {
                        engine.setImage(bitmap)
                        val extracted = engine.getUTF8Text().orEmpty().take(100_000)
                        result = result.copy(rawText = OcrText.merge(result.rawText, extracted))
                    } finally { engine.clear() }
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
                    analysisVersion = AnalysisPolicy.VERSION, indexedAt = System.currentTimeMillis(), isIndexed = mask and required == required,
                    hasPhoneNumber = EntityExtractor.phones(result.rawText).isNotEmpty(), hasEmail = EntityExtractor.emails(result.rawText).isNotEmpty(),
                    indexError = if (required and AnalysisPolicy.ARABIC != 0 && !arabicReady) "Download the Arabic OCR model in Settings." else errors.joinToString(" "))
            } finally { bitmap.recycle() }
        }
    }

    suspend fun release() = withContext(NonCancellable + Dispatchers.IO) { mutex.withLock {
        text?.close(); text = null; labels?.close(); labels = null; faces?.close(); faces = null; arabic?.recycle(); arabic = null
    } }

    private fun decode(image: IndexImage): Bitmap {
        val resolver = context.contentResolver
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
