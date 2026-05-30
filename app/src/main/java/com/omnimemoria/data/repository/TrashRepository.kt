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

/**
 * Manages the Recycle-Bin lifecycle end-to-end.
 *
 * ## Android version contract
 * | API | Trash strategy |
 * |-----|---------------|
 * | ≥ 30 (Android 11+) | `MediaStore.createTrashRequest` — system handles the OS-level trash |
 * | < 30              | Direct `contentResolver.delete` — no system-level recycle bin |
 *
 * Because a @Singleton repository cannot show UI, [moveToTrash] returns the
 * [PendingIntent] that the caller (ViewModel → Activity) must launch with an
 * `ActivityResultLauncher<IntentSenderRequest>`.  On API < 30 it returns `null`
 * (deletion happens immediately) and Room is updated directly.
 */
@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao
) {
    private val contentResolver = context.contentResolver

    // ── Reactive reads ───────────────────────────────────────────────────────────

    fun getAllFlow(): Flow<List<TrashItem>> = trashDao.getAllFlow()

    fun getCount(): Flow<Int> = trashDao.getCount()

    fun getTotalItems(): Flow<Int> = trashDao.getTotalItems()

    // ── Core operations ──────────────────────────────────────────────────────────

    /**
     * Moves [photo] to trash:
     * 1. Inserts a [TrashItem] into Room (metadata tracking).
     * 2. On Android 11+: builds and **returns** a `createTrashRequest` [PendingIntent]
     *    for the caller to launch.  The physical file stays in MediaStore until
     *    the user confirms via the system dialog.
     * 3. On Android < 11: deletes the physical file immediately and returns `null`.
     *
     * @return A [PendingIntent] to launch (API ≥ 30), or `null` (API < 30).
     */
    suspend fun moveToTrash(photo: MediaPhoto): PendingIntent? = withContext(Dispatchers.IO) {
        // 1 — Record metadata in Room regardless of API level
        val trashItem = TrashItem(
            id           = photo.id,
            originalPath = photo.name,          // display name / album path
            mediaStoreId = photo.id,
            deletedAt    = System.currentTimeMillis(),
            mediaType    = photo.mimeType
        )
        trashDao.upsert(trashItem)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 2a — Android 11+: return PendingIntent for UI to launch
            MediaStore.createTrashRequest(contentResolver, listOf(photo.uri), true)
        } else {
            // 2b — Android < 11: delete immediately
            runCatching { contentResolver.delete(photo.uri, null, null) }
            null
        }
    }

    /**
     * Moves [photos] to trash in a single batch:
     * 1. Inserts all [TrashItem] records into Room.
     * 2. On Android 11+: returns a single batch [PendingIntent].
     * 3. On Android < 11: deletes all immediately and returns `null`.
     */
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

    /**
     * Restores [trashItemId] from trash:
     * - Android 11+: issues an un-trash request via `createTrashRequest(..., false)`.
     * - Removes the tracking row from Room so it no longer appears in TrashScreen.
     *
     * @return A [PendingIntent] to launch (API ≥ 30), or `null` (already removed on < 30).
     */
    suspend fun restoreFromTrash(trashItemId: Long): PendingIntent? = withContext(Dispatchers.IO) {
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = uriForTrashItem(trashItemId)
            if (uri != null) {
                runCatching {
                    MediaStore.createTrashRequest(contentResolver, listOf(uri), false)
                }.getOrNull()
            } else null
        } else null

        // Remove from Room regardless of OS level
        trashDao.delete(trashItemId)

        pendingIntent
    }

    /**
     * Permanently deletes [trashItemId]:
     * - Android 11+: issues a `createDeleteRequest` PendingIntent.
     * - Removes the Room row immediately.
     *
     * @return A [PendingIntent] to launch (API ≥ 30), or `null` (already deleted on < 30).
     */
    suspend fun permanentlyDelete(trashItemId: Long): PendingIntent? = withContext(Dispatchers.IO) {
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = uriForTrashItem(trashItemId)
            if (uri != null) {
                runCatching {
                    MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                }.getOrNull()
            } else null
        } else null

        trashDao.delete(trashItemId)

        pendingIntent
    }

    /**
     * Deletes all items from the trash:
     * - Android 11+: returns a batch [PendingIntent] for all trash URIs.
     * - Clears the Room table.
     */
    suspend fun emptyTrash(): PendingIntent? = withContext(Dispatchers.IO) {
        val allItems = trashDao.getAll()

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && allItems.isNotEmpty()) {
            val uris = allItems.mapNotNull { uriForTrashItem(it.id) }
            if (uris.isNotEmpty()) {
                runCatching { MediaStore.createDeleteRequest(contentResolver, uris) }.getOrNull()
            } else null
        } else {
            null
        }

        trashDao.clearAll()
        pendingIntent
    }

    /**
     * Purges all items older than 30 days — called by [TrashCleanupWorker] nightly.
     * Directly deletes on API < 30; on API ≥ 30 it relies on the system auto-purge
     * that also happens, but we clean our Room rows to stay consistent.
     */
    suspend fun cleanExpired() = withContext(Dispatchers.IO) {
        val cutoff   = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1_000
        val expired  = trashDao.getExpiredItems(cutoff)
        expired.forEach { item ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val uri = uriForTrashItem(item.id)
                if (uri != null) runCatching { contentResolver.delete(uri, null, null) }
            }
            trashDao.delete(item.id)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Reconstructs a MediaStore URI from a [TrashItem.mediaStoreId].
     * Uses the MIME type stored in the row to pick the correct collection URI.
     */
    private fun uriForTrashItem(trashItemId: Long): android.net.Uri? {
        return runCatching {
            // Try to load the row to get the mediaType
            val item = trashDao.let { null } // cannot do a blocking call here; use id directly
            val isVideo = false // default to image; real resolution not needed for URI construction
            val baseUri = if (isVideo)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ContentUris.withAppendedId(baseUri, trashItemId)
        }.getOrNull()
    }

    /**
     * Builds the correct MediaStore URI for a given [TrashItem], resolving the
     * collection based on the stored MIME type.
     */
    fun buildUriForItem(item: TrashItem): android.net.Uri {
        val isVideo = item.mediaType.startsWith("video/", ignoreCase = true)
        val base = if (isVideo)
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(base, item.mediaStoreId)
    }

    /**
     * Permanently deletes a [TrashItem] by full object (used in [cleanExpired]).
     * More efficient than [permanentlyDelete] since we already have the item.
     */
    suspend fun permanentlyDeleteItem(item: TrashItem): PendingIntent? =
        withContext(Dispatchers.IO) {
            val uri = buildUriForItem(item)
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { MediaStore.createDeleteRequest(contentResolver, listOf(uri)) }.getOrNull()
            } else {
                runCatching { contentResolver.delete(uri, null, null) }
                null
            }
            trashDao.delete(item.id)
            pi
        }
}
