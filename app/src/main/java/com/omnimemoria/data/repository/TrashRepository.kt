package com.omnimemoria.data.repository

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.omnimemoria.data.local.db.TrashDao
import com.omnimemoria.data.local.db.TrashItem
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao
) {
    private val contentResolver = context.contentResolver

    // ── Reactive reads ───────────────────────────────────────────────────────

    fun getAllFlow(): Flow<List<TrashItem>> = trashDao.getAllFlow()
    fun getCount(): Flow<Int> = trashDao.getCount()
    fun getTotalItems(): Flow<Int> = trashDao.getTotalItems()

    // ── Move to trash ────────────────────────────────────────────────────────

    suspend fun moveToTrash(photo: MediaPhoto): PendingIntent? = withContext(Dispatchers.IO) {
        trashDao.upsert(
            TrashItem(
                id           = photo.id,
                originalPath = photo.name,
                mediaStoreId = photo.id,
                deletedAt    = System.currentTimeMillis(),
                mediaType    = photo.mimeType
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createTrashRequest(contentResolver, listOf(photo.uri), true)
        } else {
            runCatching { contentResolver.delete(photo.uri, null, null) }
            null
        }
    }

    suspend fun moveAllToTrash(photos: List<MediaPhoto>): PendingIntent? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            photos.forEach { photo ->
                trashDao.upsert(
                    TrashItem(
                        id           = photo.id,
                        originalPath = photo.name,
                        mediaStoreId = photo.id,
                        deletedAt    = now,
                        mediaType    = photo.mimeType
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createTrashRequest(contentResolver, photos.map { it.uri }, true)
            } else {
                photos.forEach { photo ->
                    runCatching { contentResolver.delete(photo.uri, null, null) }
                }
                null
            }
        }

    // ── Restore ──────────────────────────────────────────────────────────────

    suspend fun restoreFromTrash(trashItemId: Long): PendingIntent? =
        withContext(Dispatchers.IO) {
            // FIX: جلب الـ item الكامل عشان نبني الـ URI الصح بالـ mediaType الحقيقي
            val item = trashDao.getById(trashItemId)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && item != null) {
                runCatching {
                    MediaStore.createTrashRequest(
                        contentResolver,
                        listOf(buildUriForItem(item)),
                        false
                    )
                }.getOrNull()
            } else null

            trashDao.delete(trashItemId)
            pendingIntent
        }

    // ── Permanent delete ─────────────────────────────────────────────────────

    suspend fun permanentlyDelete(trashItemId: Long): PendingIntent? =
        withContext(Dispatchers.IO) {
            val item = trashDao.getById(trashItemId)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && item != null) {
                runCatching {
                    MediaStore.createDeleteRequest(contentResolver, listOf(buildUriForItem(item)))
                }.getOrNull()
            } else null

            trashDao.delete(trashItemId)
            pendingIntent
        }

    suspend fun permanentlyDeleteItem(item: TrashItem): PendingIntent? =
        withContext(Dispatchers.IO) {
            val uri = buildUriForItem(item)
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                }.getOrNull()
            } else {
                runCatching { contentResolver.delete(uri, null, null) }
                null
            }
            trashDao.delete(item.id)
            pi
        }

    // ── Empty trash ──────────────────────────────────────────────────────────

    suspend fun emptyTrash(): PendingIntent? = withContext(Dispatchers.IO) {
        val allItems = trashDao.getAll()
        // FIX: نستخدم buildUriForItem مباشرة بدل uriForTrashItem المكسور
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && allItems.isNotEmpty()) {
            val uris = allItems.map { buildUriForItem(it) }
            runCatching {
                MediaStore.createDeleteRequest(contentResolver, uris)
            }.getOrNull()
        } else null

        trashDao.clearAll()
        pendingIntent
    }

    // ── Cleanup expired ──────────────────────────────────────────────────────

    suspend fun cleanExpired() = withContext(Dispatchers.IO) {
        val cutoff  = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1_000
        val expired = trashDao.getExpiredItems(cutoff)
        expired.forEach { item ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // FIX: نستخدم buildUriForItem مباشرة لأن لدينا الـ item الكامل
                runCatching { contentResolver.delete(buildUriForItem(item), null, null) }
            }
            trashDao.delete(item.id)
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    fun buildUriForItem(item: TrashItem): android.net.Uri {
        val isVideo = item.mediaType.startsWith("video/", ignoreCase = true)
        val base = if (isVideo)
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(base, item.mediaStoreId)
    }
}
