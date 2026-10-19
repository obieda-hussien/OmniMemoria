package com.omnimemoria.data.intelligence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArabicModelStore @Inject constructor(@ApplicationContext context: Context) {
    private val legacyRoot = File(context.filesDir, "tesseract")
    private val accurateRoot = File(context.filesDir, "tesseract_accurate")
    val file = File(accurateRoot, "tessdata/ara.traineddata")
    private val legacyFile = File(legacyRoot, "tessdata/ara.traineddata")
    val root: File get() = if (isHighAccuracyInstalled()) accurateRoot else legacyRoot
    private val verified = mutableMapOf<String, Pair<String, Boolean>>()
    @Synchronized private fun verify(candidate: File, size: Long, sha: String): Boolean {
        if (!candidate.isFile || candidate.length() != size) return false
        val signature = "${candidate.lastModified()}:$size"
        verified[candidate.path]?.takeIf { it.first == signature }?.let { return it.second }
        val valid = runCatching { sha256(candidate) == sha }.getOrDefault(false)
        verified[candidate.path] = signature to valid
        return valid
    }
    fun isHighAccuracyInstalled() = verify(file, SIZE, SHA256)
    fun isInstalled(): Boolean = isHighAccuracyInstalled() || verify(legacyFile, LEGACY_SIZE, LEGACY_SHA256)
    companion object {
        const val SIZE = 12603724L
        const val SIZE_LABEL = "12.6 MB"
        const val SHA256 = "ab9d157d8e38ca00e7e39c7d5363a5239e053f5b0dbdb3167dde9d8124335896"
        const val URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/e12c65a915945e4c28e237a9b52bc4a8f39a0cec/ara.traineddata"
        private const val LEGACY_SIZE = 1432056L
        private const val LEGACY_SHA256 = "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
