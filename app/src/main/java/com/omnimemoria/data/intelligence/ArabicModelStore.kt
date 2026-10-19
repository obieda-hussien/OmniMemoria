package com.omnimemoria.data.intelligence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArabicModelStore @Inject constructor(@ApplicationContext context: Context) {
    val root = File(context.filesDir, "tesseract")
    val file = File(root, "tessdata/ara.traineddata")
    fun isInstalled(): Boolean = file.isFile && file.length() == SIZE && sha256(file) == SHA256
    companion object {
        const val SIZE = 1432056L
        const val SHA256 = "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"
        const val URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/ara.traineddata"
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
