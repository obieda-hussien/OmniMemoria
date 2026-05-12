package com.omnimemoria.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoIntelligenceDao: PhotoIntelligenceDao
) {
    private val contentResolver: ContentResolver = context.contentResolver

    fun getPhotosPaged(sortConfig: SortConfig): Flow<PagingData<MediaPhoto>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE),
            pagingSourceFactory = {
                MediaPhotoPagingSource(
                    contentResolver = contentResolver,
                    photoIntelligenceDao = photoIntelligenceDao,
                    sortConfig = sortConfig
                )
            }
        ).flow
    }

    fun getFoldersPaged(): Flow<PagingData<MediaFolder>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE),
            pagingSourceFactory = { MediaFolderPagingSource(contentResolver) }
        ).flow
    }

    fun getPhotoById(id: Long): MediaPhoto? {
        val projection = photoProjection
        val selection = "${MediaStore.Images.Media._ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.toMediaPhoto()
            }
        }
        return null
    }

    fun deletePhotos(ids: List<Long>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)

        return runCatching {
            val uris = ids.map { id ->
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(contentResolver, uris)
            } else {
                uris.forEach { uri -> contentResolver.delete(uri, null, null) }
            }
        }
    }

    private class MediaPhotoPagingSource(
        private val contentResolver: ContentResolver,
        private val photoIntelligenceDao: PhotoIntelligenceDao,
        private val sortConfig: SortConfig
    ) : PagingSource<Int, MediaPhoto>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaPhoto> {
            return try {
                val startOffset = params.key ?: 0
                val vaultedIds = photoIntelligenceDao.getVaultPhotoIds().toHashSet()
                val pageData = mutableListOf<MediaPhoto>()
                var offset = startOffset
                var endReached = false

                while (pageData.size < params.loadSize && !endReached) {
                    val chunk = queryPhotos(contentResolver, sortConfig, params.loadSize, offset)
                    if (chunk.isEmpty()) {
                        endReached = true
                    } else {
                        pageData += chunk.filterNot { photo -> photo.id in vaultedIds }
                        offset += chunk.size
                        if (chunk.size < params.loadSize) {
                            endReached = true
                        }
                    }
                }

                val finalData = pageData.take(params.loadSize)
                LoadResult.Page(
                    data = finalData,
                    prevKey = if (startOffset == 0) null else (startOffset - params.loadSize).coerceAtLeast(0),
                    nextKey = if (endReached) null else offset
                )
            } catch (throwable: Throwable) {
                LoadResult.Error(throwable)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, MediaPhoto>): Int? {
            val anchorPosition = state.anchorPosition ?: return null
            val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
            return anchorPage.prevKey?.plus(state.config.pageSize)
                ?: anchorPage.nextKey?.minus(state.config.pageSize)
        }
    }

    private class MediaFolderPagingSource(
        private val contentResolver: ContentResolver
    ) : PagingSource<Int, MediaFolder>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaFolder> {
            return try {
                val offset = params.key ?: 0
                val folders = queryFolders(contentResolver)
                val end = (offset + params.loadSize).coerceAtMost(folders.size)
                val page = if (offset < folders.size) folders.subList(offset, end) else emptyList()

                LoadResult.Page(
                    data = page,
                    prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                    nextKey = if (end >= folders.size) null else end
                )
            } catch (throwable: Throwable) {
                LoadResult.Error(throwable)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, MediaFolder>): Int? {
            val anchorPosition = state.anchorPosition ?: return null
            val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
            return anchorPage.prevKey?.plus(state.config.pageSize)
                ?: anchorPage.nextKey?.minus(state.config.pageSize)
        }
    }

    companion object {
        private const val PAGE_SIZE = 60

        private val photoProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        private fun queryPhotos(
            contentResolver: ContentResolver,
            sortConfig: SortConfig,
            limit: Int,
            offset: Int
        ): List<MediaPhoto> {
            val queryArgs = buildQueryArgs(sortConfig, limit, offset)
            val results = mutableListOf<MediaPhoto>()
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoProjection,
                queryArgs,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    results += cursor.toMediaPhoto()
                }
            }
            return results
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun buildQueryArgs(sortConfig: SortConfig, limit: Int, offset: Int): Bundle {
            val (column, fallbackColumn) = when (sortConfig.sortBy) {
                SortBy.DATE_TAKEN -> MediaStore.Images.Media.DATE_TAKEN to MediaStore.Images.Media.DATE_ADDED
                SortBy.DATE_MODIFIED -> MediaStore.Images.Media.DATE_MODIFIED to MediaStore.Images.Media.DATE_ADDED
                SortBy.SIZE -> MediaStore.Images.Media.SIZE to MediaStore.Images.Media.DATE_ADDED
                SortBy.NAME -> MediaStore.Images.Media.DISPLAY_NAME to MediaStore.Images.Media.DATE_ADDED
                SortBy.TYPE -> MediaStore.Images.Media.MIME_TYPE to MediaStore.Images.Media.DATE_ADDED
                SortBy.RESOLUTION -> MediaStore.Images.Media.WIDTH to MediaStore.Images.Media.HEIGHT
                SortBy.DURATION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.MediaColumns.DURATION to MediaStore.Images.Media.DATE_ADDED
                    } else {
                        MediaStore.Images.Media.DATE_TAKEN to MediaStore.Images.Media.DATE_ADDED
                    }
                }
                SortBy.FAVORITES_FIRST -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        MediaStore.MediaColumns.IS_FAVORITE to MediaStore.Images.Media.DATE_TAKEN
                    } else {
                        MediaStore.Images.Media.DATE_TAKEN to MediaStore.Images.Media.DATE_ADDED
                    }
                }
            }

            val direction = when (sortConfig.sortOrder) {
                SortOrder.ASCENDING -> ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                SortOrder.DESCENDING -> ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            }

            return Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(column, fallbackColumn))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, direction)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
        }

        private fun queryFolders(contentResolver: ContentResolver): List<MediaFolder> {
            val foldersByBucket = linkedMapOf<String, FolderAccumulator>()
            val queryArgs = Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_TAKEN))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            }

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoProjection,
                queryArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateTakenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketIdIndex) ?: continue
                    val bucketName = cursor.getString(bucketNameIndex) ?: "Unknown"
                    val photoId = cursor.getLong(idIndex)
                    val dateTaken = cursor.getLong(dateTakenIndex)
                    val coverUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId)

                    val existing = foldersByBucket[bucketId]
                    if (existing == null) {
                        foldersByBucket[bucketId] = FolderAccumulator(
                            bucketId = bucketId,
                            name = bucketName,
                            coverUri = coverUri,
                            photoCount = 1,
                            latestPhotoDate = dateTaken
                        )
                    } else {
                        existing.photoCount += 1
                        if (dateTaken > existing.latestPhotoDate) {
                            existing.latestPhotoDate = dateTaken
                            existing.coverUri = coverUri
                        }
                    }
                }
            }

            return foldersByBucket.values
                .map { folder ->
                    MediaFolder(
                        bucketId = folder.bucketId,
                        name = folder.name,
                        coverUri = folder.coverUri,
                        photoCount = folder.photoCount,
                        latestPhotoDate = folder.latestPhotoDate
                    )
                }
                .sortedByDescending { folder -> folder.latestPhotoDate }
        }

        private fun android.database.Cursor.toMediaPhoto(): MediaPhoto {
            val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val name = getStringOrEmpty(MediaStore.Images.Media.DISPLAY_NAME)
            val size = getLongOrZero(MediaStore.Images.Media.SIZE)
            val mimeType = getStringOrEmpty(MediaStore.Images.Media.MIME_TYPE)
            val dateTaken = getLongOrZero(MediaStore.Images.Media.DATE_TAKEN)
            val width = getIntOrZero(MediaStore.Images.Media.WIDTH)
            val height = getIntOrZero(MediaStore.Images.Media.HEIGHT)
            val latitude = getDoubleOrNull(MediaStore.Images.Media.LATITUDE)
            val longitude = getDoubleOrNull(MediaStore.Images.Media.LONGITUDE)

            return MediaPhoto(
                id = id,
                uri = uri,
                name = name,
                size = size,
                mimeType = mimeType,
                dateTaken = dateTaken,
                width = width,
                height = height,
                latitude = latitude,
                longitude = longitude
            )
        }

        private fun android.database.Cursor.getStringOrEmpty(column: String): String {
            val index = getColumnIndex(column)
            return if (index >= 0) getString(index).orEmpty() else ""
        }

        private fun android.database.Cursor.getLongOrZero(column: String): Long {
            val index = getColumnIndex(column)
            return if (index >= 0) getLong(index) else 0L
        }

        private fun android.database.Cursor.getIntOrZero(column: String): Int {
            val index = getColumnIndex(column)
            return if (index >= 0) getInt(index) else 0
        }

        private fun android.database.Cursor.getDoubleOrNull(column: String): Double? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getDouble(index) else null
        }
    }
}

private data class FolderAccumulator(
    val bucketId: String,
    val name: String,
    var coverUri: Uri,
    var photoCount: Int,
    var latestPhotoDate: Long
)
