package com.omnimemoria.data.worker

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnimemoria.data.local.db.CorruptedMedia
import com.omnimemoria.data.local.db.CorruptedMediaDao
import com.omnimemoria.data.local.db.MediaIntegrityChecked
import com.omnimemoria.data.local.db.MediaIntegrityCheckedDao
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.SortConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@HiltWorker
class MediaIntegrityWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaStoreRepository: MediaStoreRepository,
    private val corruptedMediaDao: CorruptedMediaDao,
    private val checkedDao: MediaIntegrityCheckedDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val inputIds = inputData.getLongArray(WORK_INPUT_PHOTO_IDS)?.toSet()

            val allPhotos = mediaStoreRepository.getAllPhotos(SortConfig())
            val alreadyCorrupted = corruptedMediaDao.getAllIds().toHashSet()
            val alreadyChecked = checkedDao.getCheckedIds().toHashSet()

            val candidates = if (inputIds != null) {
                // Targeted run (e.g. newly added media) — always re-check these
                // regardless of history.
                allPhotos.filter { it.id in inputIds }
            } else {
                // Periodic sweep — skip anything already resolved either way.
                allPhotos.filterNot { it.id in alreadyCorrupted || it.id in alreadyChecked }
            }

            candidates.forEachIndexed { index, photo ->
                if (index % 25 == 0) yield() // don't hog the IO dispatcher

                val isVideo = photo.mimeType.startsWith("video/", ignoreCase = true)
                val broken = if (!isVideo) {
                    runCatching {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        applicationContext.contentResolver.openInputStream(photo.uri)?.use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }
                        opts.outWidth <= 0 || opts.outHeight <= 0
                    }.getOrDefault(true)
                } else {
                    runCatching {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(applicationContext, photo.uri)
                            retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION
                            ) == null
                        } finally {
                            retriever.release()
                        }
                    }.getOrDefault(true)
                }

                val now = System.currentTimeMillis()
                if (broken) {
                    corruptedMediaDao.upsert(
                        CorruptedMedia(
                            id = photo.id,
                            detectedAt = now,
                            reason = if (isVideo) "metadata_failed" else "decode_failed"
                        )
                    )
                } else {
                    checkedDao.markChecked(MediaIntegrityChecked(id = photo.id, checkedAt = now))
                }
            }

            // Re-verify "clean" files older than 30 days — handles the rare case of a
            // file being overwritten in place at the same MediaStore id (e.g. an
            // interrupted cloud-sync placeholder write).
            checkedDao.pruneOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1_000)

            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_INPUT_PHOTO_IDS = "work_input_photo_ids"
        const val UNIQUE_PERIODIC_WORK_NAME = "omnimemoria_media_integrity_periodic"
        const val UNIQUE_TARGETED_WORK_NAME = "omnimemoria_media_integrity_targeted"
    }
}
