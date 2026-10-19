package com.omnimemoria.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnimemoria.data.preferences.AppPreferences
import com.omnimemoria.domain.flags.OfflineModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Safe handler for jobs enqueued by older builds. Actual transfers belong to
 * Phase 4. Never turn an old request into a fictitious installed model. */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val model = OfflineModel.fromId(inputData.getString(MODEL_NAME).orEmpty())
            ?: return Result.failure(workDataOf("error" to "unknown_model"))
        appPreferences.updateBooleans {
            mapOf(
                AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED to false,
                AppPreferences.PreferencesKeys.MODEL_MEDIAPIPE_EMBEDDER_DOWNLOADED to false,
                AppPreferences.PreferencesKeys.ARE_AI_MODELS_DOWNLOADED to false,
                (if (model == OfflineModel.TESSERACT_ARABIC) AppPreferences.PreferencesKeys.REQUEST_TESSERACT_DOWNLOAD
                 else AppPreferences.PreferencesKeys.REQUEST_EMBEDDER_DOWNLOAD) to true
            )
        }
        return Result.failure(workDataOf("error" to "download_available_in_phase_4"))
    }

    companion object {
        const val MODEL_NAME = "model_name"
        const val MODEL_TESSERACT_ARA = "tesseract_ara"
        const val MODEL_MEDIAPIPE_EMBEDDER = "mediapipe_embedder"
    }
}
