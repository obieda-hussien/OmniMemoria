package com.omnimemoria.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omnimemoria.MainActivity
import com.omnimemoria.R
import com.omnimemoria.data.repository.MediaStoreRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class OnThisDayWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaStoreRepository: MediaStoreRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val memories = mediaStoreRepository.getPhotosOnThisDay()
        if (memories.isEmpty()) return@withContext Result.success()

        val yearsAgo  = Calendar.getInstance().get(Calendar.YEAR) - run {
            val cal = Calendar.getInstance()
            cal.timeInMillis = memories.first().dateTaken
            cal.get(Calendar.YEAR)
        }

        val title = "🗓️ On This Day"
        val body  = when {
            memories.size == 1 -> "A memory from $yearsAgo year${if (yearsAgo > 1) "s" else ""} ago is waiting for you"
            else               -> "${memories.size} memories from the past are waiting for you"
        }

        showNotification(title, body)
        Result.success()
    }

    private fun showNotification(title: String, body: String) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // إنشاء الـ Channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "On This Day",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily memory reminders from OmniMemoria"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        // Intent بيفتح التطبيق مباشرة
        val openIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_OPEN_ON_THIS_DAY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery) // استبدل بأيقونة التطبيق
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID          = "omnimemoria_on_this_day"
        const val NOTIFICATION_ID     = 1001
        const val EXTRA_OPEN_ON_THIS_DAY = "open_on_this_day"
        private const val WORK_NAME   = "omnimemoria_on_this_day_daily"

        // ── جدولة يومية — بيشتغل كل 24 ساعة ────────────────────────────────
        // استدعاء من Application.onCreate أو Settings
        fun scheduleDaily(context: Context) {
            // احسب كم ثانية باقية على الساعة 9 صباحاً
            val now       = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1) // لو فاتت → بكره
            }
            val initialDelay = targetTime.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<OnThisDayWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
