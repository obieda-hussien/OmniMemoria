package com.omnimemoria.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.domain.flags.FeatureFlagManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PhotoIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val featureFlagManager: FeatureFlagManager,
    private val photoIntelligenceDao: PhotoIntelligenceDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val inputPhotoIds = inputData.getLongArray(WORK_INPUT_PHOTO_IDS)
        val inputMode = inputData.getString(WORK_INPUT_MODE)
        Log.d(
            TAG,
            "PhotoIndexWorker triggered for ${inputData}. mode=$inputMode photoIdsCount=${inputPhotoIds?.size ?: 0}"
        )
        return Result.success()
    }

    companion object {
        const val WORK_INPUT_PHOTO_IDS = "work_input_photo_ids"
        const val WORK_INPUT_MODE = "work_input_mode"
        const val WORK_MODE_IMMEDIATE = "immediate"
        const val WORK_MODE_PERIODIC = "periodic"
        private const val TAG = "PhotoIndexWorker"
    }
}
