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
import com.omnimemoria.data.local.db.CorruptedMediaDao
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.domain.model.FolderSortBy
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.MediaType
import com.omnimemoria.domain.model.MediaQuerySql
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import com.omnimemoria.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

// ── Stats model ──────────────────────────────────────────────────────────────────
data class MediaStats(
    val photoCount:      Int  = 0,
    val videoCount:      Int  = 0,
    val photoSizeBytes:  Long = 0L,
    val videoSizeBytes:  Long = 0L,
    val totalSizeBytes:  Long = 0L,
    val albumCount:      Int  = 0
) {
    val totalCount: Int
        get() = photoCount + videoCount
}

@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoIntelligenceDao: PhotoIntelligenceDao,
    private val favoritesRepository: FavoritesRepository,
    private val corruptedMediaDao: CorruptedMediaDao
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val mediaCollection: Uri = MediaStore.Files.getContentUri("external")

    // ══ 1. إحصائيات حقيقية ══════════════════════════════════════════════════════
    suspend fun getMediaStats(): MediaStats {
        var photoCount = 0
        var videoCount = 0
        var photoSize  = 0L
        var videoSize  = 0L
        val bucketIds  = mutableSetOf<String>()

        // Compute selection once — reused for both selection and args below.
        val (selection, selectionArgs) = QueryBuilder(FilterConfig()).buildSelection()

        val corruptedIds = runCatching {
            corruptedMediaDao.getAllIds().toHashSet()
        }.getOrDefault(emptySet<Long>())

        contentResolver.query(
            mediaCollection,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            ),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idi = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val si = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val bi = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val pi = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val mi = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val rowId = if (idi >= 0) cursor.getLong(idi) else -1L
                if (rowId in corruptedIds) continue

                val size = if (si >= 0) cursor.getLong(si) else 0L
                val mediaType = if (mi >= 0 && !cursor.isNull(mi)) cursor.getInt(mi) else null
                val mimeType = if (pi >= 0 && !cursor.isNull(pi)) cursor.getString(pi).orEmpty() else ""
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ||
                    (mediaType == null && mimeType.startsWith("video/", ignoreCase = true))
                if (isVideo) {
                    videoCount++
                    videoSize += size
                } else {
                    photoCount++
                    photoSize += size
                }
                if (bi >= 0) cursor.getString(bi)?.let { bucketIds.add(it) }
            }
        }
        return MediaStats(
            photoCount = photoCount,
            videoCount = videoCount,
            photoSizeBytes = photoSize,
            videoSizeBytes = videoSize,
            totalSizeBytes = photoSize + videoSize,
            albumCount = bucketIds.size
        )
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

    fun observeMediaQueryChanges(sort: SortConfig = SortConfig(), filter: FilterConfig = FilterConfig()): Flow<Unit> =
        if (filter.isFavorite != null || sort.sortBy == SortBy.FAVORITES_FIRST) {
            merge(observeMediaStoreChanges(), favoritesRepository.getAllFavoriteIds().drop(1).map { Unit })
        } else observeMediaStoreChanges()

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

        // Compute the base selection once — same FilterConfig() for all 5 iterations.
        val (baseSelection, baseArgs) = QueryBuilder(FilterConfig()).buildSelection()

        for (yearsBack in 1..5) {
            val start = Calendar.getInstance().apply {
                set(curYear - yearsBack, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = start + 24L * 60 * 60 * 1000 - 1

            contentResolver.query(
                mediaCollection,
                photoProjection,
                "${MediaStore.MediaColumns.DATE_TAKEN} BETWEEN ? AND ? AND ($baseSelection)",
                arrayOf(start.toString(), end.toString(), *baseArgs),
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) results.add(cursor.toMediaPhoto())
            }
            if (results.size >= 5) break
        }
        return results
    }

    private data class ResolvedQuery(
        val selection: String,
        val args: Array<String>,
        val favoriteIds: Set<Long>
    )

    private suspend fun resolveQuery(filter: FilterConfig, includedIds: Set<Long>? = null): ResolvedQuery {
        val (base, args) = QueryBuilder(filter).buildSelection()
        val clauses = mutableListOf(base)
        val excluded = photoIntelligenceDao.getVaultPhotoIds().toSet() + corruptedMediaDao.getAllIds()
        clauses += MediaQuerySql.idsClause(excluded, false)
        val favorites = favoritesRepository.getAllFavoriteIds().first()
        filter.isFavorite?.let { clauses += MediaQuerySql.idsClause(favorites, it) }
        includedIds?.let { clauses += MediaQuerySql.idsClause(it, true) }
        filter.hasText?.let { clauses += MediaQuerySql.idsClause(photoIntelligenceDao.getIdsByTextPresence(it).toSet(), true) }
        filter.hasFaces?.let { clauses += MediaQuerySql.idsClause(photoIntelligenceDao.getIdsByFaces(it).toSet(), true) }
        filter.hasPhoneNumber?.let { clauses += MediaQuerySql.idsClause(photoIntelligenceDao.getIdsByPhoneNumber(it).toSet(), true) }
        return ResolvedQuery(clauses.joinToString(" AND "), args, favorites)
    }

    suspend fun getMatchingPhotos(
        sort: SortConfig = SortConfig(),
        filter: FilterConfig = FilterConfig(),
        bucketId: String? = null,
        ids: Set<Long>? = null
    ): List<MediaPhoto> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        queryPhotos(contentResolver, sort, filter, Int.MAX_VALUE, 0, bucketId, resolveQuery(filter, ids))
            .also { currentCoroutineContext().ensureActive() }
    }

    suspend fun getPhotosByIds(ids: List<Long>): List<MediaPhoto> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val resolved = resolveQuery(FilterConfig(), ids.toSet())
        val found = queryPhotos(contentResolver, SortConfig(), FilterConfig(), Int.MAX_VALUE, 0, null, resolved)
            .associateBy { it.id }
        ids.mapNotNull(found::get)
    }

    fun getAllPhotos(sortConfig: SortConfig): List<MediaPhoto> =
        queryPhotos(contentResolver, sortConfig, FilterConfig(), Int.MAX_VALUE, 0)

    suspend fun getAllNonVaultPhotos(sortConfig: SortConfig): List<MediaPhoto> = getMatchingPhotos(sortConfig)
    suspend fun getAllNonVaultPhotosByFolder(bucketId: String, sortConfig: SortConfig): List<MediaPhoto> =
        getMatchingPhotos(sortConfig, bucketId = bucketId)
    fun getAllPhotosByFolder(bucketId: String, sortConfig: SortConfig): List<MediaPhoto> =
        queryPhotos(contentResolver, sortConfig, FilterConfig(), Int.MAX_VALUE, 0, bucketId)

    fun getPhotosPaged(sortConfig: SortConfig, filterConfig: FilterConfig = FilterConfig()): Flow<PagingData<MediaPhoto>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 24, enablePlaceholders = false)) {
            MediaPhotoPagingSource(this, sortConfig, filterConfig, null)
        }.flow

    fun getPhotosByFolder(bucketId: String, sortConfig: SortConfig, filterConfig: FilterConfig = FilterConfig()): Flow<PagingData<MediaPhoto>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 24, enablePlaceholders = false)) {
            MediaPhotoPagingSource(this, sortConfig, filterConfig, bucketId)
        }.flow

    fun getFoldersPaged(sortConfig: FolderSortConfig = FolderSortConfig(), filter: FilterConfig = FilterConfig()): Flow<PagingData<MediaFolder>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            MediaFolderPagingSource(this, sortConfig, filter)
        }.flow

    suspend fun getFolderByBucketId(bucketId: String): MediaFolder? = withContext(Dispatchers.IO) {
        queryFolders(contentResolver, FolderSortConfig(), resolveQuery(FilterConfig()), bucketId).firstOrNull()
    }

    suspend fun searchMatchingPhotos(
        text: String, sort: SortConfig, filter: FilterConfig, matchingIds: Set<Long> = emptySet()
    ): List<MediaPhoto> = withContext(Dispatchers.IO) {
        val resolved = resolveQuery(filter)
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val nameClause = "_display_name LIKE ? ESCAPE '\\'"
        val match = if (matchingIds.isEmpty()) nameClause else "($nameClause OR ${MediaQuerySql.idsClause(matchingIds, true)})"
        queryPhotos(contentResolver, sort, filter, 500, 0, null,
            resolved.copy(selection = "${resolved.selection} AND ($match)", args = resolved.args + "%$escaped%"))
            .also { currentCoroutineContext().ensureActive() }
    }

    suspend fun getPhotoById(id: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        if (id in corruptedMediaDao.getAllIds()) return@withContext null
        contentResolver.query(mediaCollection, photoProjection, "_id = ?", arrayOf(id.toString()), null)?.use {
            if (it.moveToFirst()) it.toMediaPhoto() else null
        }
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
        private val repository: MediaStoreRepository,
        private val sort: SortConfig,
        private val filter: FilterConfig,
        private val bucketId: String?
    ) : PagingSource<Int, MediaPhoto>() {
        private var resolved: ResolvedQuery? = null
        private val positions = java.util.concurrent.ConcurrentHashMap<Long, Int>()
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaPhoto> = withContext(Dispatchers.IO) {
            try {
                val boundary = params.key ?: 0
                val offset = if (params is LoadParams.Prepend) (boundary - params.loadSize).coerceAtLeast(0) else boundary
                val size = if (params is LoadParams.Prepend) boundary - offset else params.loadSize
                val query = resolved ?: repository.resolveQuery(filter).also { resolved = it }
                currentCoroutineContext().ensureActive()
                val data = queryPhotos(repository.contentResolver, sort, filter, size, offset, bucketId, query)
                currentCoroutineContext().ensureActive()
                data.forEachIndexed { index, photo -> positions[photo.id] = offset + index }
                LoadResult.Page(data, if (offset == 0) null else offset,
                    if (data.size < size) null else offset + data.size)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { LoadResult.Error(error) }
        }
        override fun getRefreshKey(state: PagingState<Int, MediaPhoto>): Int? {
            val anchor = state.anchorPosition ?: return null
            val photo = state.closestItemToPosition(anchor) ?: return null
            return positions[photo.id]?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }
        }
    }

    private class MediaFolderPagingSource(
        private val repository: MediaStoreRepository,
        private val sort: FolderSortConfig,
        private val filter: FilterConfig
    ) : PagingSource<Int, MediaFolder>() {
        private var snapshot: List<MediaFolder>? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaFolder> = withContext(Dispatchers.IO) {
            try {
                val folders = snapshot ?: queryFolders(repository.contentResolver, sort, repository.resolveQuery(filter))
                    .also { snapshot = it }
                val boundary = (params.key ?: 0).coerceAtMost(folders.size)
                val offset = if (params is LoadParams.Prepend) (boundary - params.loadSize).coerceAtLeast(0) else boundary
                val end = if (params is LoadParams.Prepend) boundary else (offset + params.loadSize).coerceAtMost(folders.size)
                LoadResult.Page(folders.subList(offset, end), if (offset == 0) null else offset,
                    if (end == folders.size) null else end)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { LoadResult.Error(error) }
        }
        override fun getRefreshKey(state: PagingState<Int, MediaFolder>): Int? =
            state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }
    }

    // ── Companion ────────────────────────────────────────────────────────────────

    companion object {
        private const val PAGE_SIZE = 60

        class QueryBuilder(private val filter: FilterConfig) {
            fun buildSelection(): Pair<String, Array<String>> {
                val (selection, args) = MediaQuerySql.selection(filter)
                val visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) " AND is_trashed = 0 AND is_pending = 0"
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) " AND is_pending = 0" else ""
                return selection + visibility to args
            }
        }

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
            cr: ContentResolver, sort: SortConfig, filter: FilterConfig, limit: Int, offset: Int,
            bucketId: String? = null, resolved: ResolvedQuery? = null
        ): List<MediaPhoto> {
            val (base, baseArgs) = resolved?.let { it.selection to it.args } ?: QueryBuilder(filter).buildSelection()
            val selection = if (bucketId.isNullOrBlank()) base else "($base) AND bucket_id = ?"
            val args = if (bucketId.isNullOrBlank()) baseArgs else baseArgs + bucketId
            val order = MediaQuerySql.sort(sort, resolved?.favoriteIds ?: emptySet())
            val results = mutableListOf<MediaPhoto>()
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cr.query(MediaStore.Files.getContentUri("external"), photoProjection, Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                    putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                }, null)
            } else cr.query(MediaStore.Files.getContentUri("external"), photoProjection, selection, args, "$order LIMIT $limit OFFSET $offset")
            cursor?.use {
                val handled = it.extras.getStringArray(ContentResolver.EXTRA_HONORED_ARGS)?.toSet().orEmpty()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && offset > 0 && ContentResolver.QUERY_ARG_OFFSET !in handled) {
                    // An OEM may apply OFFSET without advertising it. Query an
                    // unbounded cursor before applying the window ourselves.
                    cr.query(MediaStore.Files.getContentUri("external"), photoProjection, selection, args, order)?.use { full ->
                        full.moveToPosition(offset - 1)
                        while (results.size < limit && full.moveToNext()) results += full.toMediaPhoto()
                    }
                } else while (results.size < limit && it.moveToNext()) results += it.toMediaPhoto()
            }
            return results
        }

        private fun queryFolders(cr: ContentResolver, sortConfig: FolderSortConfig, resolved: ResolvedQuery? = null, bucketId: String? = null): List<MediaFolder> {
            val map  = linkedMapOf<String, FolderAccumulator>()
            val (base, baseArgs) = resolved?.let { it.selection to it.args } ?: QueryBuilder(FilterConfig()).buildSelection()
            val selection = if (bucketId == null) base else "($base) AND bucket_id = ?"
            val selectionArgs = if (bucketId == null) baseArgs else baseArgs + bucketId
            cr.query(MediaStore.Files.getContentUri("external"), photoProjection, selection, selectionArgs,
                MediaQuerySql.sort(SortConfig()))
                ?.use { cursor ->
                    val idC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val biC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bnC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val dtC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                    while (cursor.moveToNext()) {
                        val bid  = cursor.getString(biC) ?: continue
                        val name = cursor.getString(bnC) ?: "Unknown"
                        val pid  = cursor.getLong(idC)
                        val dt   = cursor.toMediaPhoto().effectiveDateMs
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
                .let { folders ->
                    val sorted = when (sortConfig.sortBy) {
                        FolderSortBy.DATE_LATEST_PHOTO -> folders.sortedWith(compareBy<MediaFolder> { it.latestPhotoDate }.thenBy { it.bucketId })
                        FolderSortBy.NAME -> folders.sortedWith(compareBy<MediaFolder> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.bucketId })
                        FolderSortBy.PHOTO_COUNT -> folders.sortedWith(compareBy<MediaFolder> { it.photoCount }.thenBy { it.bucketId })
                    }
                    when (sortConfig.sortOrder) {
                        SortOrder.ASCENDING -> sorted
                        SortOrder.DESCENDING -> sorted.reversed()
                    }
                }
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
