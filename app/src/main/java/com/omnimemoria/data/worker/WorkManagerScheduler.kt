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

        val request = PeriodicWorkRequestBuilder<PhotoIndexWorker>(6, TimeUnit.HOURS)
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
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "omnimemoria_index"
        const val UNIQUE_IMMEDIATE_WORK_NAME = "${UNIQUE_WORK_NAME}_immediate"
        const val UNIQUE_PERIODIC_WORK_NAME = "${UNIQUE_WORK_NAME}_periodic"
    }
}
