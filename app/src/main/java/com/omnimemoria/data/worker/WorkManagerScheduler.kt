package com.omnimemoria.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.await
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Forward-reference — resolved at link time; no circular import.
private typealias TrashWorker = com.omnimemoria.data.worker.TrashCleanupWorker

@Singleton
class WorkManagerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleImmediateIndex(photoIds: List<Long>) {
        // WorkManager Data has a 10 KiB limit. Chain bounded, deduplicated batches.
        photoIds.distinct().filter { it > 0 }.chunked(400).forEach { batch ->
            val request = immediateRequest(batch)
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request
            )
        }
    }

    internal fun immediateRequest(photoIds: List<Long>): androidx.work.OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setInputData(Data.Builder()
                .putLongArray(PhotoIndexWorker.WORK_INPUT_PHOTO_IDS, photoIds.toLongArray())
                .putString(PhotoIndexWorker.WORK_INPUT_MODE, PhotoIndexWorker.WORK_MODE_IMMEDIATE)
                .putBoolean(PhotoIndexWorker.WORK_INPUT_USER_INITIATED, true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

    internal fun scanRequest(afterId: Long = 0, ids: List<Long>? = null, userInitiated: Boolean = false): androidx.work.OneTimeWorkRequest {
        val data = Data.Builder().putLong(PhotoIndexWorker.WORK_INPUT_AFTER_ID, afterId)
            .putBoolean(PhotoIndexWorker.WORK_INPUT_USER_INITIATED, userInitiated)
        ids?.let { data.putLongArray(PhotoIndexWorker.WORK_INPUT_PHOTO_IDS, it.toLongArray()) }
        val idle = !userInitiated && ids == null
        val builder = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setInputData(data.build())
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(!userInitiated).setRequiresDeviceIdle(idle).build())
        // WorkManager throws when explicit backoff is combined with device idle.
        if (!idle) builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        return builder.build()
    }

    fun scheduleBackgroundIndex(afterId: Long = 0, ids: List<Long>? = null, append: Boolean = false, userInitiated: Boolean = false): androidx.work.Operation {
        return WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_SCAN_WORK_NAME,
            if (afterId == 0L && !append) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE,
            scanRequest(afterId, ids, userInitiated))
    }

    suspend fun startLibraryScan() {
        // Replace an idle-waiting scan when the user explicitly chooses Scan now.
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_SCAN_WORK_NAME,
            ExistingWorkPolicy.REPLACE, scanRequest(userInitiated = true)).await()
    }

    fun cancelLibraryScan() = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SCAN_WORK_NAME)

    fun schedulePeriodicIndex() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val inputData = Data.Builder()
            .putString(PhotoIndexWorker.WORK_INPUT_MODE, PhotoIndexWorker.WORK_MODE_PERIODIC)
            .build()

        val request = PeriodicWorkRequestBuilder<PhotoIndexWorker>(
            PERIODIC_INDEX_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAllWork() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SCAN_WORK_NAME)
        cancelPeriodicIndex()
    }

    fun cancelPeriodicIndex() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }

    // ── Trash cleanup (every 24 h, battery-not-low) ───────────────────────────────

    /**
     * Schedules a periodic job that runs [TrashCleanupWorker] once per day,
     * only when the battery is not low.  Uses KEEP policy so re-scheduling on
     * app launch is cheap (no duplicate work is enqueued).
     */
    fun scheduleTrashCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<TrashWorker>(
            TRASH_CLEANUP_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TrashWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelTrashCleanup() {
        WorkManager.getInstance(context).cancelUniqueWork(TrashWorker.UNIQUE_WORK_NAME)
    }

    // ── On This Day (daily at 9 AM) ───────────────────────────────────────────

    fun scheduleOnThisDay() {
        if (OnThisDayWorker.isEnabled(context)) OnThisDayWorker.scheduleDaily(context)
        else OnThisDayWorker.cancel(context)
    }

    fun cancelOnThisDay() {
        OnThisDayWorker.cancel(context)
    }

    // ── Media integrity (every 48 h, battery-not-low + device-idle) ─────────────

    fun scheduleMediaIntegrityCheck() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()
        val request = PeriodicWorkRequestBuilder<MediaIntegrityWorker>(48, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MediaIntegrityWorker.UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleTargetedIntegrityCheck(photoIds: List<Long>) {
        val inputData = Data.Builder()
            .putLongArray(MediaIntegrityWorker.WORK_INPUT_PHOTO_IDS, photoIds.toLongArray())
            .build()
        val request = OneTimeWorkRequestBuilder<MediaIntegrityWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MediaIntegrityWorker.UNIQUE_TARGETED_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        const val UNIQUE_SCAN_WORK_NAME = "omnimemoria_index_scan"
        const val UNIQUE_IMMEDIATE_WORK_NAME = "omnimemoria_index_immediate"
        const val UNIQUE_PERIODIC_WORK_NAME = "omnimemoria_index_periodic"
        internal const val PERIODIC_INDEX_INTERVAL_HOURS = 6L
        internal const val TRASH_CLEANUP_INTERVAL_HOURS  = 24L
    }
}
