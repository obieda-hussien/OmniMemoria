package com.omnimemoria.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

// ── Stats model ──────────────────────────────────────────────────────────────────
data class MediaStats(
    val photoCount:     Int  = 0,
    val totalSizeBytes: Long = 0L,
    val albumCount:     Int  = 0
)

@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoIntelligenceDao: PhotoIntelligenceDao
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val mediaCollection: Uri = MediaStore.Files.getContentUri("external")

    // ══ 1. إحصائيات حقيقية ══════════════════════════════════════════════════════
    fun getMediaStats(): MediaStats {
        var photoCount = 0
        var totalSize  = 0L
        val bucketIds  = mutableSetOf<String>()

        contentResolver.query(
            mediaCollection,
            arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.BUCKET_ID),
            mediaSelection,
            mediaSelectionArgs,
            null
        )?.use { cursor ->
            val si = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val bi = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            while (cursor.moveToNext()) {
                photoCount++
                if (si >= 0) totalSize += cursor.getLong(si)
                if (bi >= 0) cursor.getString(bi)?.let { bucketIds.add(it) }
            }
        }
        return MediaStats(photoCount, totalSize, bucketIds.size)
    }

    // ══ 2. Live ContentObserver → Flow (debounced 1.5s) ════════════════════════
    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        contentResolver.registerContentObserver(
            mediaCollection, true, observer
        )
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(1_500L)

    // ══ 3. آخر صورة — لـ Dynamic Theme ═════════════════════════════════════════
    fun getMostRecentPhotoUri(): Uri? {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
            }
        }
        return null
    }

    // ══ 4. استخراج اللون الغالب (lightweight 32×32 avg) ════════════════════════
    suspend fun extractDominantColor(uri: Uri): Color? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            val raw  = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return@withContext null

            val scaled = Bitmap.createScaledBitmap(raw, 32, 32, true)
            val pixels = IntArray(32 * 32)
            scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)

            var r = 0L; var g = 0L; var b = 0L
            pixels.forEach { px ->
                r += android.graphics.Color.red(px)
                g += android.graphics.Color.green(px)
                b += android.graphics.Color.blue(px)
            }
            val n = pixels.size.toLong()
            Color(
                red   = (r / n).toInt().coerceIn(0, 255) / 255f,
                green = (g / n).toInt().coerceIn(0, 255) / 255f,
                blue  = (b / n).toInt().coerceIn(0, 255) / 255f
            )
        }.getOrNull()
    }

    // ══ 5. On This Day — صورة واحدة من كل سنة ماضية (max 5) ═══════════════════
    fun getPhotosOnThisDay(): List<MediaPhoto> {
        val results = mutableListOf<MediaPhoto>()
        val today   = Calendar.getInstance()
        val month   = today.get(Calendar.MONTH)
        val day     = today.get(Calendar.DAY_OF_MONTH)
        val curYear = today.get(Calendar.YEAR)

        for (yearsBack in 1..5) {
            val start = Calendar.getInstance().apply {
                set(curYear - yearsBack, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = start + 24L * 60 * 60 * 1000 - 1

            contentResolver.query(
                mediaCollection,
                photoProjection,
                "${MediaStore.MediaColumns.DATE_TAKEN} BETWEEN ? AND ? AND ($mediaSelection)",
                arrayOf(start.toString(), end.toString(), *mediaSelectionArgs),
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) results.add(cursor.toMediaPhoto())
            }
            if (results.size >= 5) break
        }
        return results
    }

    // ══ 6. كل الصور مرتبة بالتاريخ — للـ PhotoDetail swipe window ══════════════
    fun getAllPhotosSortedByDate(): List<MediaPhoto> {
        val results = mutableListOf<MediaPhoto>()
        contentResolver.query(
            mediaCollection,
            photoProjection,
            mediaSelection,
            mediaSelectionArgs,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) results += cursor.toMediaPhoto()
        }
        return results
    }

    // ══ 7. All non-vault photos — unlimited swipe source for PhotoDetail ═════════
    // Filters out vault items so the pager index matches the gallery grid exactly.
    suspend fun getAllNonVaultPhotosSortedByDate(): List<MediaPhoto> =
        withContext(Dispatchers.IO) {
            val vaultIds = photoIntelligenceDao.getVaultPhotoIds().toHashSet()
            getAllPhotosSortedByDate().filterNot { it.id in vaultIds }
        }

    // ══ Paging ══════════════════════════════════════════════════════════════════

    fun getPhotosPaged(sortConfig: SortConfig): Flow<PagingData<MediaPhoto>> = Pager(
        config              = PagingConfig(pageSize = PAGE_SIZE),
        pagingSourceFactory = {
            MediaPhotoPagingSource(contentResolver, photoIntelligenceDao, sortConfig)
        }
    ).flow

    fun getFoldersPaged(): Flow<PagingData<MediaFolder>> = Pager(
        config              = PagingConfig(pageSize = PAGE_SIZE),
        pagingSourceFactory = { MediaFolderPagingSource(contentResolver) }
    ).flow

    fun getPhotoById(id: Long): MediaPhoto? {
        contentResolver.query(
            mediaCollection,
            photoProjection,
            "${MediaStore.MediaColumns._ID} = ? AND ($mediaSelection)",
            arrayOf(id.toString(), *mediaSelectionArgs),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.toMediaPhoto()
        }
        return null
    }

    fun deletePhotos(ids: List<Long>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return runCatching {
            val uris = ids.map {
                ContentUris.withAppendedId(mediaCollection, it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                MediaStore.createDeleteRequest(contentResolver, uris)
            else
                uris.forEach { uri -> contentResolver.delete(uri, null, null) }
        }
    }

    // ── PagingSources ────────────────────────────────────────────────────────────

    private class MediaPhotoPagingSource(
        private val contentResolver:      ContentResolver,
        private val photoIntelligenceDao: PhotoIntelligenceDao,
        private val sortConfig:           SortConfig
    ) : PagingSource<Int, MediaPhoto>() {
        private var cachedVaultedIds: Set<Long>? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaPhoto> {
            return try {
                val startOffset = params.key ?: 0
                val vaultedIds  = cachedVaultedIds
                    ?: photoIntelligenceDao.getVaultPhotoIds().toHashSet()
                        .also { cachedVaultedIds = it }
                val pageData   = mutableListOf<MediaPhoto>()
                var offset     = startOffset
                var endReached = false
                val chunkSize  = params.loadSize * 2

                while (pageData.size < params.loadSize && !endReached) {
                    val chunk = queryPhotos(contentResolver, sortConfig, chunkSize, offset)
                    if (chunk.isEmpty()) {
                        endReached = true
                    } else {
                        pageData += chunk.filterNot { it.id in vaultedIds }
                        offset   += chunk.size
                        if (chunk.size < chunkSize) endReached = true
                    }
                }
                LoadResult.Page(
                    data    = pageData.take(params.loadSize),
                    prevKey = if (startOffset == 0) null
                              else (startOffset - params.loadSize).coerceAtLeast(0),
                    nextKey = if (endReached) null else offset
                )
            } catch (t: Throwable) { LoadResult.Error(t) }
        }

        override fun getRefreshKey(state: PagingState<Int, MediaPhoto>): Int? {
            val anchor = state.anchorPosition ?: return null
            val page   = state.closestPageToPosition(anchor) ?: return null
            return page.prevKey?.plus(state.config.pageSize)
                ?: page.nextKey?.minus(state.config.pageSize)
        }
    }

    private class MediaFolderPagingSource(
        private val contentResolver: ContentResolver
    ) : PagingSource<Int, MediaFolder>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaFolder> {
            return try {
                val offset  = params.key ?: 0
                val folders = queryFolders(contentResolver)
                val end     = (offset + params.loadSize).coerceAtMost(folders.size)
                LoadResult.Page(
                    data    = if (offset < folders.size) folders.subList(offset, end)
                              else emptyList(),
                    prevKey = if (offset == 0) null
                              else (offset - params.loadSize).coerceAtLeast(0),
                    nextKey = if (end >= folders.size) null else end
                )
            } catch (t: Throwable) { LoadResult.Error(t) }
        }
        override fun getRefreshKey(state: PagingState<Int, MediaFolder>): Int? {
            val anchor = state.anchorPosition ?: return null
            val page   = state.closestPageToPosition(anchor) ?: return null
            return page.prevKey?.plus(state.config.pageSize)
                ?: page.nextKey?.minus(state.config.pageSize)
        }
    }

    // ── Companion ────────────────────────────────────────────────────────────────

    companion object {
        private const val PAGE_SIZE = 60

        // ── FIX: added DATE_MODIFIED and DATE_ADDED for fallback display ──────
        // Snapchat / received media often has DATE_TAKEN = 0.
        // DATE_MODIFIED (seconds) is always present; DATE_ADDED (seconds) is the
        // last resort. MediaPhoto.effectiveDateMs picks the best available value.
        val photoProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,   // seconds since epoch
            MediaStore.MediaColumns.DATE_ADDED,      // seconds since epoch
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        private fun queryPhotos(
            cr: ContentResolver, sortConfig: SortConfig, limit: Int, offset: Int
        ): List<MediaPhoto> {
            val results = mutableListOf<MediaPhoto>()
            cr.query(
                MediaStore.Files.getContentUri("external"),
                photoProjection, buildQueryArgs(sortConfig, limit, offset), null
            )?.use { cursor ->
                while (cursor.moveToNext()) results += cursor.toMediaPhoto()
            }
            if (sortConfig.sortBy != SortBy.RESOLUTION) return results
            return when (sortConfig.sortOrder) {
                SortOrder.ASCENDING  -> results.sortedBy          { it.width.toLong() * it.height }
                SortOrder.DESCENDING -> results.sortedByDescending { it.width.toLong() * it.height }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun buildQueryArgs(sc: SortConfig, limit: Int, offset: Int): Bundle {
            val (col, fallback) = when (sc.sortBy) {
                SortBy.DATE_TAKEN      -> MediaStore.MediaColumns.DATE_TAKEN    to MediaStore.MediaColumns.DATE_ADDED
                SortBy.DATE_MODIFIED   -> MediaStore.MediaColumns.DATE_MODIFIED to MediaStore.MediaColumns.DATE_ADDED
                SortBy.SIZE            -> MediaStore.MediaColumns.SIZE          to MediaStore.MediaColumns.DATE_ADDED
                SortBy.NAME            -> MediaStore.MediaColumns.DISPLAY_NAME  to MediaStore.MediaColumns.DATE_ADDED
                SortBy.TYPE            -> MediaStore.MediaColumns.MIME_TYPE     to MediaStore.MediaColumns.DATE_ADDED
                SortBy.RESOLUTION      -> MediaStore.MediaColumns.WIDTH         to MediaStore.MediaColumns.HEIGHT
                SortBy.DURATION        -> MediaStore.MediaColumns.DATE_TAKEN    to MediaStore.MediaColumns.DATE_ADDED
                SortBy.FAVORITES_FIRST ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        MediaStore.MediaColumns.IS_FAVORITE to MediaStore.MediaColumns.DATE_TAKEN
                    else
                        MediaStore.MediaColumns.DATE_TAKEN  to MediaStore.MediaColumns.DATE_ADDED
            }
            val dir = when (sc.sortOrder) {
                SortOrder.ASCENDING  -> ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                SortOrder.DESCENDING -> ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            }
            return Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(col, fallback))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, dir)
                putInt(ContentResolver.QUERY_ARG_LIMIT,  limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, mediaSelection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, mediaSelectionArgs)
            }
        }

        private fun queryFolders(cr: ContentResolver): List<MediaFolder> {
            val map  = linkedMapOf<String, FolderAccumulator>()
            val args = Bundle().apply {
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_TAKEN)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
            }
            cr.query(
                MediaStore.Files.getContentUri("external"),
                photoProjection,
                Bundle(args).apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, mediaSelection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, mediaSelectionArgs)
                },
                null
            )
                ?.use { cursor ->
                    val idC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val biC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bnC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val dtC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                    while (cursor.moveToNext()) {
                        val bid  = cursor.getString(biC) ?: continue
                        val name = cursor.getString(bnC) ?: "Unknown"
                        val pid  = cursor.getLong(idC)
                        val dt   = cursor.getLong(dtC)
                        val uri  = contentUriForMime(
                            pid,
                            cursor.getStringOrEmpty(MediaStore.MediaColumns.MIME_TYPE),
                            cursor.getIntOrNull(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        )
                        val ex = map[bid]
                        if (ex == null) map[bid] = FolderAccumulator(bid, name, uri, 1, dt)
                        else {
                            ex.photoCount++
                            if (dt > ex.latestPhotoDate) { ex.latestPhotoDate = dt; ex.coverUri = uri }
                        }
                    }
                }
            return map.values
                .map { f -> MediaFolder(f.bucketId, f.name, f.coverUri, f.photoCount, f.latestPhotoDate) }
                .sortedByDescending { it.latestPhotoDate }
        }

        // ── FIX: maps DATE_MODIFIED and DATE_ADDED from cursor ────────────────
        fun android.database.Cursor.toMediaPhoto(): MediaPhoto {
            val id = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            val mime = getStringOrEmpty(MediaStore.MediaColumns.MIME_TYPE)
            val mediaType = getIntOrNull(MediaStore.Files.FileColumns.MEDIA_TYPE)
            return MediaPhoto(
                id           = id,
                uri          = contentUriForMime(id, mime, mediaType),
                name         = getStringOrEmpty(MediaStore.MediaColumns.DISPLAY_NAME),
                size         = getLongOrZero(MediaStore.MediaColumns.SIZE),
                mimeType     = mime,
                dateTaken    = getLongOrZero(MediaStore.MediaColumns.DATE_TAKEN),
                dateModified = getLongOrZero(MediaStore.MediaColumns.DATE_MODIFIED),
                dateAdded    = getLongOrZero(MediaStore.MediaColumns.DATE_ADDED),
                width        = getIntOrZero(MediaStore.MediaColumns.WIDTH),
                height       = getIntOrZero(MediaStore.MediaColumns.HEIGHT),
                latitude     = getDoubleOrNull(MediaStore.Images.Media.LATITUDE),
                longitude    = getDoubleOrNull(MediaStore.Images.Media.LONGITUDE)
            )
        }

        private fun contentUriForMime(id: Long, mimeType: String, mediaType: Int?): Uri {
            val isVideo = mimeType.startsWith("video/", ignoreCase = true) ||
                mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val base = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            return ContentUris.withAppendedId(base, id)
        }

        private val mediaSelection =
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
        private val mediaSelectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        private fun android.database.Cursor.getStringOrEmpty(c: String) =
            getColumnIndex(c).let { if (it >= 0) getString(it).orEmpty() else "" }
        private fun android.database.Cursor.getLongOrZero(c: String) =
            getColumnIndex(c).let { if (it >= 0) getLong(it) else 0L }
        private fun android.database.Cursor.getIntOrZero(c: String) =
            getColumnIndex(c).let { if (it >= 0) getInt(it) else 0 }
        private fun android.database.Cursor.getIntOrNull(c: String) =
            getColumnIndex(c).let { if (it >= 0 && !isNull(it)) getInt(it) else null }
        private fun android.database.Cursor.getDoubleOrNull(c: String) =
            getColumnIndex(c).let { if (it >= 0 && !isNull(it)) getDouble(it) else null }
    }
}

private data class FolderAccumulator(
    val bucketId: String, val name: String,
    var coverUri: Uri, var photoCount: Int, var latestPhotoDate: Long
)
