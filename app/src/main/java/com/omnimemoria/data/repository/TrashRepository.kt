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

    suspend fun getTrashItemsByIds(ids: List<Long>): List<TrashItem> = withContext(Dispatchers.IO) {
        ids.mapNotNull { trashDao.getById(it) }
    }

    suspend fun getTrashItemById(id: Long): TrashItem? = withContext(Dispatchers.IO) {
        trashDao.getById(id)
    }

    // ── Move to trash ────────────────────────────────────────────────────────

    suspend fun moveToTrashIntent(photos: List<MediaPhoto>): PendingIntent? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && photos.isNotEmpty()) {
                runCatching {
                    MediaStore.createTrashRequest(contentResolver, photos.map { it.uri }, true)
                }.getOrNull()
            } else null
        }

    suspend fun moveToTrashIntent(photo: MediaPhoto): PendingIntent? = moveToTrashIntent(listOf(photo))

    suspend fun confirmMoveToTrash(photos: List<MediaPhoto>) = withContext(Dispatchers.IO) {
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                runCatching { contentResolver.delete(photo.uri, null, null) }
            }
        }
    }

    suspend fun confirmMoveToTrash(photo: MediaPhoto) = confirmMoveToTrash(listOf(photo))

    // ── Restore ──────────────────────────────────────────────────────────────

    suspend fun restoreFromTrashIntent(items: List<TrashItem>): PendingIntent? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && items.isNotEmpty()) {
                runCatching {
                    MediaStore.createTrashRequest(contentResolver, items.map { buildUriForItem(it) }, false)
                }.getOrNull()
            } else null
        }

    suspend fun restoreFromTrashIntent(item: TrashItem): PendingIntent? = restoreFromTrashIntent(listOf(item))

    suspend fun confirmRestoreFromTrash(items: List<TrashItem>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            trashDao.delete(item.id)
            // Note: On API < 30, it was permanently deleted during moveToTrash, we can't fully restore it.
        }
    }

    suspend fun confirmRestoreFromTrash(item: TrashItem) = confirmRestoreFromTrash(listOf(item))

    // ── Permanent delete ─────────────────────────────────────────────────────

    suspend fun permanentlyDeleteIntent(items: List<TrashItem>): PendingIntent? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && items.isNotEmpty()) {
                runCatching {
                    MediaStore.createDeleteRequest(contentResolver, items.map { buildUriForItem(it) })
                }.getOrNull()
            } else null
        }

    suspend fun permanentlyDeleteIntent(item: TrashItem): PendingIntent? = permanentlyDeleteIntent(listOf(item))

    suspend fun confirmPermanentlyDelete(items: List<TrashItem>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                runCatching { contentResolver.delete(buildUriForItem(item), null, null) }
            }
            trashDao.delete(item.id)
        }
    }

    suspend fun confirmPermanentlyDelete(item: TrashItem) = confirmPermanentlyDelete(listOf(item))

    // ── Empty trash ──────────────────────────────────────────────────────────

    suspend fun getEmptyTrashIntent(): PendingIntent? = withContext(Dispatchers.IO) {
        permanentlyDeleteIntent(trashDao.getAll())
    }

    suspend fun confirmEmptyTrash() = withContext(Dispatchers.IO) {
        confirmPermanentlyDelete(trashDao.getAll())
    }

    // ── Cleanup expired ──────────────────────────────────────────────────────

    suspend fun cleanExpired() = withContext(Dispatchers.IO) {
        val cutoff  = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1_000
        val expired = trashDao.getExpiredItems(cutoff)
        expired.forEach { item ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
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
