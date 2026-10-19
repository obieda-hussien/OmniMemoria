package com.omnimemoria.data.repository

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.omnimemoria.data.local.db.TrashDao
import com.omnimemoria.data.local.db.TrashItem
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** System trash on Android 11+. Older devices retain the file and hide it in
 * OmniMemoria until restore, explicit permanent deletion or expiry. */
@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao
) {
    private val contentResolver = context.contentResolver
    private val mutex = Mutex()
    fun getAllFlow(): Flow<List<TrashItem>> = trashDao.getAllFlow()
    fun getCount(): Flow<Int> = trashDao.getCount()
    fun getTotalItems(): Flow<Int> = trashDao.getTotalItems()
    suspend fun getTrashItemsByIds(ids: List<Long>): List<TrashItem> = withContext(Dispatchers.IO) { ids.mapNotNull { trashDao.getById(it) } }
    suspend fun getTrashItemById(id: Long): TrashItem? = withContext(Dispatchers.IO) { trashDao.getById(id) }

    suspend fun moveToTrashWithFallback(
        photos: List<MediaPhoto>,
        onNeedsPermission: suspend (PendingIntent, suspend () -> Unit) -> Unit,
        onDone: suspend () -> Unit = {},
        onError: suspend (String) -> Unit = { throw IOException(it) }
    ) {
        if (photos.isEmpty()) return
        val snapshot = photos.distinctBy { it.id }
        suspend fun report(action: suspend () -> Unit) {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onError(error.message ?: "Unable to move media to trash") }
        }
        report {
            val intent = moveToTrashIntent(snapshot)
            if (intent != null) onNeedsPermission(intent) { report { confirmMoveToTrash(snapshot); onDone() } }
            else { confirmMoveToTrash(snapshot); onDone() }
        }
    }
    suspend fun moveToTrashWithFallback(
        photo: MediaPhoto,
        onNeedsPermission: suspend (PendingIntent, suspend () -> Unit) -> Unit,
        onDone: suspend () -> Unit = {},
        onError: suspend (String) -> Unit = { throw IOException(it) }
    ) = moveToTrashWithFallback(listOf(photo), onNeedsPermission, onDone, onError)

    suspend fun moveToTrashIntent(photos: List<MediaPhoto>): PendingIntent? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 30 && photos.isNotEmpty())
            MediaStore.createTrashRequest(contentResolver, photos.map { it.uri }, true)
        else null // Only absence of platform support takes the legacy path.
    }
    suspend fun moveToTrashIntent(photo: MediaPhoto) = moveToTrashIntent(listOf(photo))

    suspend fun confirmMoveToTrash(photos: List<MediaPhoto>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            photos.forEach { photo ->
                val state = readState(photo.uri)
                if (!state.exists || (Build.VERSION.SDK_INT >= 30 && !state.trashed))
                    throw IOException("The media was not moved to trash. Please try again.")
                trashDao.upsert(TrashItem(photo.id, photo.name, photo.id, System.currentTimeMillis(), photo.mimeType))
            }
        }
    }
    suspend fun confirmMoveToTrash(photo: MediaPhoto) = confirmMoveToTrash(listOf(photo))

    suspend fun restoreFromTrashIntent(items: List<TrashItem>): PendingIntent? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 30 && items.isNotEmpty())
            MediaStore.createTrashRequest(contentResolver, items.map(::buildUriForItem), false)
        else null
    }
    suspend fun restoreFromTrashIntent(item: TrashItem) = restoreFromTrashIntent(listOf(item))

    suspend fun confirmRestoreFromTrash(items: List<TrashItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            items.forEach { item ->
                val state = readState(buildUriForItem(item))
                if (!state.exists || (Build.VERSION.SDK_INT >= 30 && state.trashed))
                    throw IOException("The original file is unavailable or has not been restored.")
                trashDao.delete(item.id)
            }
        }
    }
    suspend fun confirmRestoreFromTrash(item: TrashItem) = confirmRestoreFromTrash(listOf(item))

    suspend fun permanentlyDeleteIntent(items: List<TrashItem>): PendingIntent? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 30 && items.isNotEmpty())
            MediaStore.createDeleteRequest(contentResolver, items.map(::buildUriForItem))
        else null
    }
    suspend fun permanentlyDeleteIntent(item: TrashItem) = permanentlyDeleteIntent(listOf(item))

    suspend fun confirmPermanentlyDelete(items: List<TrashItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            items.forEach { item ->
                val uri = buildUriForItem(item)
                if (Build.VERSION.SDK_INT < 30 && readState(uri).exists) contentResolver.delete(uri, null, null)
                if (readState(uri).exists) throw IOException("The file was not deleted. It remains in trash.")
                trashDao.delete(item.id)
            }
        }
    }
    suspend fun confirmPermanentlyDelete(item: TrashItem) = confirmPermanentlyDelete(listOf(item))
    suspend fun getEmptyTrashIntent() = permanentlyDeleteIntent(trashDao.getAll())
    suspend fun confirmEmptyTrash() = confirmPermanentlyDelete(trashDao.getAll())

    suspend fun cleanExpired() = withContext(Dispatchers.IO) {
        val expired = trashDao.getExpiredItems(System.currentTimeMillis() - 30L * 86_400_000L)
        // Android 11+ owns expiry. Keep records until the system actually removes
        // the file; permission errors never become evidence of deletion.
        if (Build.VERSION.SDK_INT >= 30) {
            for (item in expired) if (!readState(buildUriForItem(item)).exists) trashDao.delete(item.id)
        } else confirmPermanentlyDelete(expired)
    }

    private data class MediaState(val exists: Boolean, val trashed: Boolean = false)
    private fun readState(uri: Uri): MediaState {
        val projection = if (Build.VERSION.SDK_INT >= 30) arrayOf("_id", "is_trashed") else arrayOf("_id")
        val cursor = if (Build.VERSION.SDK_INT >= 30) contentResolver.query(uri, projection,
            Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }, null)
        else contentResolver.query(uri, projection, null, null, null)
        return (cursor ?: throw IOException("Cannot verify media state. Please check storage access.")).use {
            if (!it.moveToFirst()) MediaState(false)
            else MediaState(true, Build.VERSION.SDK_INT >= 30 && it.getInt(it.getColumnIndexOrThrow("is_trashed")) == 1)
        }
    }

    fun buildUriForItem(item: TrashItem): Uri = ContentUris.withAppendedId(
        if (item.mediaType.startsWith("video/", true)) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, item.mediaStoreId
    )
}
