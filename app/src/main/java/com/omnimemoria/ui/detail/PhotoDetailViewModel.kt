package com.omnimemoria.ui.detail

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.FavoritePhoto
import com.omnimemoria.data.local.db.FavoritesDao
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.gallery.GalleryStateHolder
import com.omnimemoria.ui.gallery.MediaFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val favoritesDao:         FavoritesDao,
    private val sortPresetRepository:  SortPresetRepository,
    private val galleryStateHolder:    GalleryStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _photoList       = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photoList: StateFlow<List<MediaPhoto>> = _photoList.asStateFlow()

    private val _initialPage     = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

    private val _isFavorite      = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    /**
     * false → full list still loading; pager stays on seed
     * true  → full list ready; pager snaps to correct index via LaunchedEffect
     */
    private val _isFullListReady = MutableStateFlow(false)
    val isFullListReady: StateFlow<Boolean> = _isFullListReady.asStateFlow()

    init {
        // ── Zero-IO seed: grab the photo the user just tapped ─────────────────
        // GalleryViewModel / FolderDetailViewModel always call
        // galleryStateHolder.cachePendingPhoto(photo) before navigating,
        // so this runs before the first Compose frame → photoList is never
        // empty on first render → shared-element transition has a target. ✓
        val cached = galleryStateHolder.consumePendingPhoto()
        if (cached != null) {
            _photoList.value = listOf(cached)
        }
    }

    fun loadAllPhotos(photoId: Long, bucketId: String?, externalUriStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {

            // ── External URI (share / view intent from outside the app) ────────
            if (externalUriStr != null) {
                val single = getPhotoFromUri(externalUriStr)
                if (single != null) {
                    _photoList.value       = listOf(single)
                    _initialPage.value     = 0
                    _isFullListReady.value = true
                }
                return@launch
            }

            // ── Seed fallback (only if init() cache-miss: deep link / notification) ─
            if (_photoList.value.isEmpty()) {
                mediaStoreRepository.getPhotoById(photoId)?.let { seed ->
                    _photoList.value  = listOf(seed)
                    _initialPage.value = 0
                }
            }

            // ── Full list — SAME sort + filter that produced the gallery grid ──
            val sortConfig = galleryStateHolder.activeSortConfig.value
                .takeIf { it != com.omnimemoria.domain.model.SortConfig() }
                ?: sortPresetRepository.getCurrentSort().first()

            val activeFilter = galleryStateHolder.activeFilter.value

            val rawAll = if (bucketId.isNullOrBlank()) {
                mediaStoreRepository.getAllNonVaultPhotos(sortConfig)
            } else {
                mediaStoreRepository.getAllNonVaultPhotosByFolder(bucketId, sortConfig)
            }

            // Apply the same MediaFilter the gallery was using.
            // Folder detail always uses ALL (folders show mixed media types).
            val all = rawAll.applyMediaFilter(
                filter   = if (bucketId.isNullOrBlank()) activeFilter else MediaFilter.ALL,
                isBucket = !bucketId.isNullOrBlank()
            )

            val targetIndex = all.indexOfFirst { it.id == photoId }
            if (targetIndex < 0) {
                // Photo not in filtered list (vault, deleted, or filter mismatch) —
                // keep showing seed.
                _isFullListReady.value = true
                return@launch
            }

            _photoList.value       = all
            _initialPage.value     = targetIndex
            _isFullListReady.value = true
            _isFavorite.value      = favoritesDao.getAll().any { it.id == photoId }
        }
    }

    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val currently = _isFavorite.value
            if (!currently) {
                favoritesDao.upsert(FavoritePhoto(id = photoId, addedAt = System.currentTimeMillis()))
            }
            _isFavorite.value = !currently
        }
    }

    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }

    /**
     * Reads real metadata from ContentResolver for an external / shared URI.
     * Steps: query → getType → statSize → BitmapFactory bounds → filename timestamp.
     */
    suspend fun getPhotoFromUri(uriStr: String): MediaPhoto? = withContext(Dispatchers.IO) {
        try {
            val uri = android.net.Uri.parse(uriStr)
            val cr  = context.contentResolver
            var displayName = "External Media"; var size = 0L; var mimeType = ""
            var dateTaken = 0L; var dateModified = 0L; var dateAdded = 0L
            var width = 0; var height = 0

            runCatching {
                cr.query(uri, arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.WIDTH,        MediaStore.MediaColumns.HEIGHT
                ), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        fun ci(n: String) = c.getColumnIndex(n)
                        ci(MediaStore.MediaColumns.DISPLAY_NAME).takeIf { it>=0 }?.let { displayName  = c.getString(it) ?: displayName }
                        ci(MediaStore.MediaColumns.SIZE).takeIf         { it>=0 }?.let { size         = c.getLong(it) }
                        ci(MediaStore.MediaColumns.MIME_TYPE).takeIf    { it>=0 }?.let { mimeType     = c.getString(it).orEmpty() }
                        ci(MediaStore.MediaColumns.DATE_TAKEN).takeIf   { it>=0 }?.let { dateTaken    = c.getLong(it) }
                        ci(MediaStore.MediaColumns.DATE_MODIFIED).takeIf{ it>=0 }?.let { dateModified = c.getLong(it) }
                        ci(MediaStore.MediaColumns.DATE_ADDED).takeIf   { it>=0 }?.let { dateAdded    = c.getLong(it) }
                        ci(MediaStore.MediaColumns.WIDTH).takeIf         { it>=0 }?.let { width        = c.getInt(it) }
                        ci(MediaStore.MediaColumns.HEIGHT).takeIf        { it>=0 }?.let { height       = c.getInt(it) }
                    }
                }
            }
            if (mimeType.isBlank()) mimeType = cr.getType(uri).orEmpty()
            if (size == 0L) runCatching { cr.openFileDescriptor(uri, "r")?.use { size = it.statSize.coerceAtLeast(0L) } }
            if ((width == 0 || height == 0) && !mimeType.startsWith("video/", ignoreCase = true)) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    if (opts.outWidth  > 0) width  = opts.outWidth
                    if (opts.outHeight > 0) height = opts.outHeight
                }
            }
            if (dateTaken == 0L && dateModified == 0L) {
                runCatching { Regex("""(\d{13})""").find(displayName)?.value?.toLongOrNull()?.let { dateTaken = it } }
            }
            MediaPhoto(id = -1L, uri = uri, name = displayName, size = size, mimeType = mimeType,
                dateTaken = dateTaken, dateModified = dateModified, dateAdded = dateAdded,
                width = width, height = height, latitude = null, longitude = null)
        } catch (e: Exception) { null }
    }
}

// ── Extension: apply MediaFilter to List<MediaPhoto> ──────────────────────────
internal fun List<MediaPhoto>.applyMediaFilter(
    filter:   MediaFilter,
    isBucket: Boolean = false
): List<MediaPhoto> {
    if (isBucket || filter == MediaFilter.ALL) return this
    return when (filter) {
        MediaFilter.PHOTOS_ONLY -> filter { !it.mimeType.startsWith("video/", ignoreCase = true) }
        MediaFilter.VIDEOS_ONLY -> filter {  it.mimeType.startsWith("video/", ignoreCase = true) }
        MediaFilter.ALL         -> this
    }
}
