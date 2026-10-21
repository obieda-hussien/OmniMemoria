package com.omnimemoria.data.intelligence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Integrity-checked offline bundle for PaddleOCRv5 Arabic + English recognition. */
@Singleton
class PaddleOcrModelStore @Inject constructor(@ApplicationContext context: Context) {
    val directory = File(context.filesDir, "models/paddle-ocr-v5-arabic")
    val detector = File(directory, "detector.onnx")
    val recognizer = File(directory, "arabic-recognizer.onnx")
    val recognitionConfig = File(directory, "arabic-inference.yml")

    data class Asset(
        val file: File,
        val url: String,
        val sha256: String?,
        val maxBytes: Long,
        val progressWeight: Int,
        val requiredMarkers: List<String> = emptyList()
    )

    fun assets(): List<Asset> = listOf(
        Asset(
            detector,
            "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/df0bd9d/inference.onnx",
            DETECTOR_SHA256, 6L * 1024 * 1024, 36
        ),
        Asset(
            recognizer,
            "https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx/resolve/14aaedc/inference.onnx",
            RECOGNIZER_SHA256, 12L * 1024 * 1024, 60
        ),
        Asset(
            recognitionConfig,
            "https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec/raw/68285d6/inference.yml",
            null, 64L * 1024, 4,
            listOf("model_name: arabic_PP-OCRv5_mobile_rec", "PostProcess:", "character_dict:", "ء", "ا")
        )
    )

    @Synchronized
    fun isInstalled(): Boolean {
        val files = assets()
        if (files.any { !it.file.isFile || it.file.length() !in 1024L..it.maxBytes }) return false
        val signature = files.joinToString("|") { "${it.file.length()}:${it.file.lastModified()}" }
        if (signature == cachedSignature) return cachedValid
        val valid = runCatching {
            files.all { asset ->
                val contentValid = if (asset.requiredMarkers.isEmpty()) true else {
                    val text = asset.file.readText(Charsets.UTF_8)
                    asset.requiredMarkers.all(text::contains)
                }
                contentValid && (asset.sha256 == null || sha256(asset.file) == asset.sha256)
            }
        }.getOrDefault(false)
        cachedSignature = signature
        cachedValid = valid
        return valid
    }

    fun prepareDirectory() { directory.mkdirs() }

    companion object {
        private const val DETECTOR_SHA256 = "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d"
        private const val RECOGNIZER_SHA256 = "799113ebf267fbe742deb99eb36e8d42c9ddc5291ceacf92add41b4d52a59110"
        const val SIZE_LABEL = "~13 MB"
        private const val MODEL_BUNDLE_VERSION = "ppocrv5-arabic-2026-10"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }

    @Volatile private var cachedSignature: String? = null
    @Volatile private var cachedValid = false
}
