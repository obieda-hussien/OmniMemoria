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
        setForeground(WorkForeground.info(applicationContext, 4102, "Downloading Arabic OCR model", "Downloading and verifying the offline model"))
        if (inputData.getString(MODEL_NAME) != MODEL_TESSERACT_ARA)
            return@withContext Result.failure(workDataOf("error" to "Embedding downloads arrive with semantic indexing."))
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
    companion object {
        const val MODEL_NAME = "model_name"
        const val MODEL_TESSERACT_ARA = "tesseract_ara"
        const val MODEL_MEDIAPIPE_EMBEDDER = "mediapipe_embedder"
        const val UNIQUE_ARABIC_DOWNLOAD = "model_download_tesseract_ara"
        fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(MODEL_NAME to MODEL_TESSERACT_ARA))
            // User-requested model downloads must not wait for a full battery.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
