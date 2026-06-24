package com.omnimemoria.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
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
        val inputData = Data.Builder()
            .putLongArray(PhotoIndexWorker.WORK_INPUT_PHOTO_IDS, photoIds.toLongArray())
            .putString(PhotoIndexWorker.WORK_INPUT_MODE, PhotoIndexWorker.WORK_MODE_IMMEDIATE)
            .build()

        val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

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
        OnThisDayWorker.scheduleDaily(context)
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
        const val UNIQUE_IMMEDIATE_WORK_NAME = "omnimemoria_index_immediate"
        const val UNIQUE_PERIODIC_WORK_NAME = "omnimemoria_index_periodic"
        internal const val PERIODIC_INDEX_INTERVAL_HOURS = 6L
        internal const val TRASH_CLEANUP_INTERVAL_HOURS  = 24L
    }
}
