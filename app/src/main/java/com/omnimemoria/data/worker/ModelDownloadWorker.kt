package com.omnimemoria.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.omnimemoria.data.intelligence.ArabicModelStore
import com.omnimemoria.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: AppPreferences,
    private val store: ArabicModelStore,
    private val scheduler: WorkManagerScheduler
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(WorkForeground.info(applicationContext, 4102, "Downloading OCR model", "Downloading and verifying the offline model"))
        when (inputData.getString(MODEL_NAME)) {
            MODEL_PADDLE_OCR_ARABIC -> return@withContext downloadPaddleBundle()
            MODEL_TESSERACT_ARA -> Unit
            else -> return@withContext Result.failure(workDataOf("error" to "Unsupported model request."))
        }
        val partial = java.io.File(store.file.parentFile, "ara.$id.traineddata.part")
        try {
            if (!store.isHighAccuracyInstalled()) {
                preferences.setBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED, false)
                store.file.parentFile?.mkdirs()
                val connection = URL(ArabicModelStore.URL).openConnection() as HttpsURLConnection
                connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
                try {
                    if (connection.responseCode != 200) throw IOException("Model server returned ${connection.responseCode}")
                    var received = 0L
                    connection.inputStream.buffered().use { input -> partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer); if (count < 0) break
                            received += count
                            if (received > ArabicModelStore.SIZE) throw IOException("Unexpected model size")
                            output.write(buffer, 0, count)
                            setProgress(workDataOf("percent" to (received * 100 / ArabicModelStore.SIZE).toInt()))
                        }
                    } }
                    if (received != ArabicModelStore.SIZE || ArabicModelStore.sha256(partial) != ArabicModelStore.SHA256)
                        throw IOException("Model integrity check failed")
                    currentCoroutineContext().ensureActive()
                    if (!partial.renameTo(store.file)) throw IOException("Cannot install model")
                } finally { connection.disconnect() }
            }
            if (!store.isHighAccuracyInstalled()) throw IOException("Installed model is invalid")
            preferences.setBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED, true)
            // Installation succeeded even if scheduling a later scan is unavailable.
            runCatching { scheduler.scheduleBackgroundIndex(append = true) }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            preferences.setBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED, false)
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("error" to "Arabic model download failed. Please retry."))
        } finally { partial.delete() }
    }
    private suspend fun downloadPaddleBundle(): Result {
        try {
            val assets = paddleModels.assets()
            paddleModels.prepareDirectory()
            var completedWeight = 0
            val totalWeight = assets.sumOf { it.progressWeight }.coerceAtLeast(1)
            for (asset in assets) {
                currentCoroutineContext().ensureActive()
                if (isPaddleAssetValid(asset)) {
                    completedWeight += asset.progressWeight
                    continue
                }
                val partial = java.io.File(asset.file.parentFile, asset.file.name + ".$id.part")
                partial.delete()
                val connection = URL(asset.url).openConnection() as HttpsURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IOException("Model server returned HTTP $code")
                    val contentLength = connection.contentLengthLong
                    if (contentLength > asset.maxBytes) throw IOException("Model response exceeds its size limit")
                    var received = 0L
                    connection.inputStream.buffered().use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                if (received > asset.maxBytes) throw IOException("Downloaded model asset is too large")
                                output.write(buffer, 0, count)
                                if (contentLength > 0L) {
                                    val within = (received * asset.progressWeight / contentLength).toInt()
                                        .coerceIn(0, asset.progressWeight)
                                    val percent = ((completedWeight + within) * 100 / totalWeight).coerceIn(0, 99)
                                    setProgress(workDataOf("percent" to percent))
                                }
                            }
                        }
                    }
                    if (received < 1024L) throw IOException("Downloaded model asset is empty")
                    if (asset.sha256 != null && ArabicModelStore.sha256(partial) != asset.sha256)
                        throw IOException("Model checksum verification failed")
                    if (asset.requiredMarkers.isNotEmpty()) {
                        val downloadedText = partial.readText(Charsets.UTF_8)
                        if (asset.requiredMarkers.any { !downloadedText.contains(it) })
                            throw IOException("Model configuration failed validation")
                    }
                    currentCoroutineContext().ensureActive()
                    if (asset.file.exists() && !asset.file.delete()) throw IOException("Cannot replace old model asset")
                    if (!partial.renameTo(asset.file)) throw IOException("Cannot install downloaded model asset")
                } finally {
                    connection.disconnect()
                    partial.delete()
                }
                completedWeight += asset.progressWeight
                setProgress(workDataOf("percent" to (completedWeight * 100 / totalWeight).coerceIn(0, 99)))
            }
            if (!paddleModels.isInstalled()) throw IOException("Installed PaddleOCR model bundle failed integrity checks")
            preferences.setBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED, true)
            setProgress(workDataOf("percent" to 100))
            runCatching { scheduler.scheduleBackgroundIndex(append = true) }
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            preferences.setBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED, false)
            android.util.Log.w("OmniModelDownload", "PaddleOCR model download attempt failed", error)
            return if (runAttemptCount < 3)
                Result.retry()
            else Result.failure(workDataOf("error" to "PaddleOCR download or integrity verification failed. Check the connection and retry."))
        }
    }

    private fun isPaddleAssetValid(asset: com.omnimemoria.data.intelligence.PaddleOcrModelStore.Asset): Boolean {
        if (!asset.file.isFile || asset.file.length() !in 1024L..asset.maxBytes) return false
        if (asset.sha256 != null && runCatching { ArabicModelStore.sha256(asset.file) != asset.sha256 }.getOrDefault(true)) return false
        if (asset.requiredMarkers.isNotEmpty()) {
            val text = runCatching { asset.file.readText(Charsets.UTF_8) }.getOrDefault("")
            if (asset.requiredMarkers.any { !text.contains(it) }) return false
        }
        return true
    }

    companion object {
        const val MODEL_NAME = "model_name"
        const val MODEL_TESSERACT_ARA = "tesseract_ara"
        const val MODEL_PADDLE_OCR_ARABIC = "paddle_ocr_arabic"
        const val MODEL_MEDIAPIPE_EMBEDDER = "mediapipe_embedder"
        const val UNIQUE_ARABIC_DOWNLOAD = "model_download_tesseract_ara"
        fun request(): OneTimeWorkRequest = requestPaddleOcr()

        fun requestPaddleOcr(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(MODEL_NAME to MODEL_PADDLE_OCR_ARABIC))
            // User-requested model downloads work on either Wi-Fi or mobile data.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        fun requestLegacyTesseract(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(MODEL_NAME to MODEL_TESSERACT_ARA))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
