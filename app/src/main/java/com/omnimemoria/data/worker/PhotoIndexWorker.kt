package com.omnimemoria.data.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnimemoria.data.intelligence.*
import com.omnimemoria.data.local.db.AppDatabase
import com.omnimemoria.domain.flags.FeatureFlagManager
import com.omnimemoria.domain.intelligence.AnalysisPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@HiltWorker
class PhotoIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val flags: FeatureFlagManager,
    private val database: AppDatabase,
    private val source: IndexImageSource,
    private val analyzer: PhotoAnalyzer,
    private val models: ArabicModelStore,
    private val paddleModels: PaddleOcrModelStore,
    private val scheduler: WorkManagerScheduler
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var examined = 0; var processed = 0; var failed = 0; var skipped = 0
        var after = inputData.getLong(WORK_INPUT_AFTER_ID, 0)
        val userInitiated = inputData.getBoolean(WORK_INPUT_USER_INITIATED, false)
        val ids = inputData.getLongArray(WORK_INPUT_PHOTO_IDS)?.toList()
        val started = SystemClock.elapsedRealtime()
        val dao = database.photoIntelligenceDao()
        val trash = database.trashDao()
        try {
            if (userInitiated) setForeground(WorkForeground.info(applicationContext, 4101, "Scanning photo library", "Analyzing photos on this device"))
            val paddleReady = paddleModels.isInstalled()
            val legacyArabicReady = models.isInstalled()
            val arabicReady = paddleReady || legacyArabicReady
            val highAccuracyArabic = paddleReady || models.isHighAccuracyInstalled()
            while (examined < 80 && SystemClock.elapsedRealtime() - started < 45_000) {
                currentCoroutineContext().ensureActive()
                val required = AnalysisPolicy.required(flags.featureStates.first())
                if (required == 0) return@withContext Result.success(workDataOf(OUTPUT_STATUS to "disabled"))
                val available = if (arabicReady) required else required and AnalysisPolicy.ARABIC.inv()
                val analysisVersion = AnalysisPolicy.version(highAccuracyArabic && required and AnalysisPolicy.ARABIC != 0)
                val page = source.page(after, ids)
                if (page.isEmpty()) return@withContext Result.success(workDataOf(OUTPUT_STATUS to "complete", "processed" to processed, "failed" to failed, "skipped" to skipped, "examined" to examined))
                val corrupted = database.corruptedMediaDao().getAllIds().toHashSet()
                for (image in page) {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) throw CancellationException("Indexing stopped")
                    after = image.id; examined++
                    val currentRequired = AnalysisPolicy.required(flags.featureStates.first())
                    if (currentRequired != required) {
                        scheduler.scheduleBackgroundIndex(0, ids, append = true, userInitiated = userInitiated)
                        return@withContext Result.success(workDataOf(OUTPUT_STATUS to "preferences_changed"))
                    }
                    val old = dao.getById(image.id)
                    if (image.id in corrupted || old?.isVaultItem == true || trash.getById(image.id) != null) { skipped++; continue }
                    if (old != null && !AnalysisPolicy.needsAnalysis(old.completedMask, available, old.analysisVersion,
                            old.sourceModified, image.modified, old.sourceSize, image.size, analysisVersion)) { skipped++; continue }
                    try {
                        val result = analyzer.analyze(image, old, available, arabicReady, analysisVersion)
                        val fresh = source.page(image.id - 1, listOf(image.id)).firstOrNull()
                        if (AnalysisPolicy.required(flags.featureStates.first()) != required) {
                            scheduler.scheduleBackgroundIndex(0, ids, append = true, userInitiated = userInitiated)
                            return@withContext Result.success(workDataOf(OUTPUT_STATUS to "preferences_changed"))
                        }
                        // Retry from this request's original cursor so an edit
                        // during inference cannot be skipped by pagination.
                        if (fresh != null && fresh != image) return@withContext if (runAttemptCount < 3)
                            Result.retry() else Result.failure(workDataOf(OUTPUT_STATUS to "photo_changed"))
                        if (fresh == null) continue // Removed or trashed during analysis.
                        database.withTransaction {
                            val latest = dao.getById(image.id)
                            if (latest?.isVaultItem != true && trash.getById(image.id) == null &&
                                !(latest != null && latest.sourceModified == image.modified && latest.sourceSize == image.size &&
                                  latest.completedMask != result.completedMask && latest.completedMask and result.completedMask == result.completedMask)) {
                                dao.upsert(result)
                            }
                        }
                        if (result.indexError.isBlank()) processed++ else failed++
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        failed++
                        database.withTransaction {
                            if (dao.getById(image.id)?.isVaultItem != true && trash.getById(image.id) == null) {
                                val unchanged = old != null && old.sourceModified == image.modified && old.sourceSize == image.size
                                val record = if (unchanged) old!! else com.omnimemoria.data.local.db.PhotoIntelligence(
                                    image.id, "", "", false, false, false, "", "", "", indexedAt = null, embeddingId = null)
                                dao.upsert(record.copy(indexError = "Unable to analyze this photo. Check its format and storage access.", indexedAt = System.currentTimeMillis()))
                            }
                        }
                    }
                    if (examined % 10 == 0) setProgress(workDataOf("processed" to processed, "failed" to failed, "skipped" to skipped, "examined" to examined))
                    yield()
                    if (examined >= 80 || SystemClock.elapsedRealtime() - started >= 45_000) break
                }
            }
            currentCoroutineContext().ensureActive()
            scheduler.scheduleBackgroundIndex(after, ids, userInitiated = userInitiated)
            Result.success(workDataOf(OUTPUT_STATUS to "continuing", "processed" to processed, "failed" to failed, "skipped" to skipped, "examined" to examined))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: SecurityException) { Result.failure(workDataOf(OUTPUT_STATUS to "storage_permission_required")) }
        catch (error: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf(OUTPUT_STATUS to "library_unavailable"))
        } finally { analyzer.release() }
    }
    companion object {
        const val WORK_INPUT_USER_INITIATED = "user_initiated"
        const val OUTPUT_STATUS = "index_status"
        const val WORK_INPUT_AFTER_ID = "after_id"
        const val WORK_INPUT_PHOTO_IDS = "work_input_photo_ids"
        const val WORK_INPUT_MODE = "work_input_mode"
        const val WORK_MODE_IMMEDIATE = "immediate"
        const val WORK_MODE_PERIODIC = "periodic"
    }
}
