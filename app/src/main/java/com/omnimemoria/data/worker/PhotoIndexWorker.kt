package com.omnimemoria.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.domain.flags.FeatureFlagManager
import com.omnimemoria.domain.flags.FeaturePolicy
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
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
        val flags = featureFlagManager.featureStates.first()
        if (FeaturePolicy.indexingFlags.none { flags[it] == true }) {
            return Result.success(workDataOf(OUTPUT_STATUS to "disabled"))
        }
        val inputPhotoIds = inputData.getLongArray(WORK_INPUT_PHOTO_IDS)
        val inputMode = inputData.getString(WORK_INPUT_MODE)
        Log.d(
            TAG,
            "PhotoIndexWorker triggered for ${inputData}. mode=$inputMode photoIdsCount=${inputPhotoIds?.size ?: 0}"
        )
        // The Phase 4 extraction engines are not bundled yet. Do not mark any
        // photo indexed or report inference success from this scheduling scaffold.
        return Result.success(workDataOf(OUTPUT_STATUS to "awaiting_phase_4_engines"))
    }

    companion object {
        const val OUTPUT_STATUS = "index_status"
        const val WORK_INPUT_PHOTO_IDS = "work_input_photo_ids"
        const val WORK_INPUT_MODE = "work_input_mode"
        const val WORK_MODE_IMMEDIATE = "immediate"
        const val WORK_MODE_PERIODIC = "periodic"
        private const val TAG = "PhotoIndexWorker"
    }
}
