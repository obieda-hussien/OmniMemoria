package com.omnimemoria.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnimemoria.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val modelName = inputData.getString(MODEL_NAME).orEmpty()
        Log.d(TAG, "Downloading $modelName")

        when (modelName) {
            MODEL_TESSERACT_ARA -> {
                appPreferences.setBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED, true)
            }

            MODEL_MEDIAPIPE_EMBEDDER -> {
                appPreferences.setBoolean(
                    AppPreferences.PreferencesKeys.MODEL_MEDIAPIPE_EMBEDDER_DOWNLOADED,
                    true
                )
            }
        }

        val tesseractDownloaded = appPreferences
            .getBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED)
            .first()
        val embedderDownloaded = appPreferences
            .getBoolean(AppPreferences.PreferencesKeys.MODEL_MEDIAPIPE_EMBEDDER_DOWNLOADED)
            .first()

        appPreferences.setBoolean(
            AppPreferences.PreferencesKeys.ARE_AI_MODELS_DOWNLOADED,
            tesseractDownloaded && embedderDownloaded
        )

        return Result.success()
    }

    companion object {
        const val MODEL_NAME = "model_name"
        const val MODEL_TESSERACT_ARA = "tesseract_ara"
        const val MODEL_MEDIAPIPE_EMBEDDER = "mediapipe_embedder"

        private const val TAG = "ModelDownloadWorker"
    }
}
