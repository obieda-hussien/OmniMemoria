package com.omnimemoria.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnimemoria.data.repository.TrashRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background worker that removes expired trash items (older than 30 days).
 *
 * Scheduled by [com.omnimemoria.data.worker.WorkManagerScheduler.scheduleTrashCleanup]
 * as a 24-hour periodic job with a BATTERY_NOT_LOW constraint so it never
 * drains the user's battery.
 *
 * On API ≥ 30, the system's own MediaStore trash also auto-purges at 30 days,
 * but this worker ensures our Room metadata stays consistent and handles
 * pre-API-30 devices that rely on direct deletion.
 */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trashRepository: TrashRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "TrashCleanupWorker: starting expired-item sweep")
            trashRepository.cleanExpired()
            Log.d(TAG, "TrashCleanupWorker: sweep complete")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "TrashCleanupWorker: error during sweep", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TrashCleanupWorker"
        const val UNIQUE_WORK_NAME = "omnimemoria_trash_cleanup_periodic"
    }
}
