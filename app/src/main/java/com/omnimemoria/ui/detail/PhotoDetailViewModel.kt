package com.omnimemoria.ui.detail

import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.repository.FavoritesRepository
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.data.repository.TrashRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.gallery.GalleryStateHolder
import com.omnimemoria.domain.model.FilterConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── UI Events ─────────────────────────────────────────────────────────────────

sealed class PhotoDetailUiEvent {
    data class RequestMediaPermission(
        val pendingIntent: PendingIntent,
        val onConfirmed: () -> Unit
    ) : PhotoDetailUiEvent()

    object NavigateBack : PhotoDetailUiEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val favoritesRepository:  FavoritesRepository,
    private val sortPresetRepository: SortPresetRepository,
    private val galleryStateHolder:   GalleryStateHolder,
    private val trashRepository:      TrashRepository,          // جديد
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _photoList       = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photoList: StateFlow<List<MediaPhoto>> = _photoList.asStateFlow()

    private val _initialPage     = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

    private val _isFavorite      = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isFullListReady = MutableStateFlow(false)
    val isFullListReady: StateFlow<Boolean> = _isFullListReady.asStateFlow()

    private val _uiEvents        = Channel<PhotoDetailUiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<PhotoDetailUiEvent> = _uiEvents.receiveAsFlow()

    private var currentPhotoId: Long = -1L
    private var favoriteObserverJob: kotlinx.coroutines.Job? = null

    init {
        val cached = galleryStateHolder.consumePendingPhoto()
        if (cached != null) _photoList.value = listOf(cached)
    }

    fun loadAllPhotos(photoId: Long, bucketId: String?, externalUriStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (externalUriStr != null) {
                val single = getPhotoFromUri(externalUriStr)
                if (single != null) {
                    _photoList.value       = listOf(single)
                    _initialPage.value     = 0
                    _isFullListReady.value = true
                }
                return@launch
            }

            if (_photoList.value.isEmpty()) {
                mediaStoreRepository.getPhotoById(photoId)?.let { seed ->
                    _photoList.value   = listOf(seed)
                    _initialPage.value = 0
                }
            }

            val sortConfig = galleryStateHolder.activeSortConfig.value
                .takeIf { it != com.omnimemoria.domain.model.SortConfig() }
                ?: sortPresetRepository.getCurrentSort().first()

            val activeFilter = galleryStateHolder.activeFilter.value

            val rawAll = if (bucketId.isNullOrBlank())
                mediaStoreRepository.getAllNonVaultPhotos(sortConfig)
            else
                mediaStoreRepository.getAllNonVaultPhotosByFolder(bucketId, sortConfig)

            val all = rawAll.applyFilterConfig(
                filter   = if (bucketId.isNullOrBlank()) activeFilter else FilterConfig(),
                isBucket = !bucketId.isNullOrBlank()
            )

            val targetIndex = all.indexOfFirst { it.id == photoId }
            if (targetIndex < 0) {
                _isFullListReady.value = true
                return@launch
            }

            _photoList.value       = all
            _initialPage.value     = targetIndex
            _isFullListReady.value = true

            observeFavoriteState(photoId)
        }
    }

    fun onPhotoPageChanged(photoId: Long) {
        if (photoId == currentPhotoId) return
        observeFavoriteState(photoId)
    }

    private fun observeFavoriteState(photoId: Long) {
        currentPhotoId = photoId
        favoriteObserverJob?.cancel()
        favoriteObserverJob = viewModelScope.launch {
            favoritesRepository.isFavorite(photoId).collect { _isFavorite.value = it }
        }
    }

    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.toggleFavorite(photoId)
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    fun deleteCurrentPhoto(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = _photoList.value.find { it.id == photoId }
                ?: mediaStoreRepository.getPhotoById(photoId)
                ?: return@launch
            trashRepository.moveToTrashWithFallback(
                photo             = photo,
                onNeedsPermission = { pi, onConfirmed ->
                    _uiEvents.send(PhotoDetailUiEvent.RequestMediaPermission(pi) {
                        viewModelScope.launch(Dispatchers.IO) { onConfirmed() }
                    })
                },
                onDone = {
                    _uiEvents.send(PhotoDetailUiEvent.NavigateBack)
                }
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }

    suspend fun getPhotoFromUri(uriStr: String): MediaPhoto? = withContext(Dispatchers.IO) {
        try {
            val uri = android.net.Uri.parse(uriStr)
            val cr  = context.contentResolver
            var displayName = "External Media"
            var size = 0L; var mimeType = ""
            var dateTaken = 0L; var dateModified = 0L; var dateAdded = 0L
            var width = 0; var height = 0

            runCatching {
                cr.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE,    MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns.WIDTH,        MediaStore.MediaColumns.HEIGHT
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        fun ci(n: String) = c.getColumnIndex(n)
                        ci(MediaStore.MediaColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { displayName  = c.getString(it) ?: displayName }
                        ci(MediaStore.MediaColumns.SIZE).takeIf         { it >= 0 }?.let { size         = c.getLong(it) }
                        ci(MediaStore.MediaColumns.MIME_TYPE).takeIf    { it >= 0 }?.let { mimeType     = c.getString(it).orEmpty() }
                        ci(MediaStore.MediaColumns.DATE_TAKEN).takeIf   { it >= 0 }?.let { dateTaken    = c.getLong(it) }
                        ci(MediaStore.MediaColumns.DATE_MODIFIED).takeIf{ it >= 0 }?.let { dateModified = c.getLong(it) }
                        ci(MediaStore.MediaColumns.DATE_ADDED).takeIf   { it >= 0 }?.let { dateAdded    = c.getLong(it) }
                        ci(MediaStore.MediaColumns.WIDTH).takeIf         { it >= 0 }?.let { width        = c.getInt(it) }
                        ci(MediaStore.MediaColumns.HEIGHT).takeIf        { it >= 0 }?.let { height       = c.getInt(it) }
                    }
                }
            }
            if (mimeType.isBlank()) mimeType = cr.getType(uri).orEmpty()
            if (size == 0L) runCatching {
                cr.openFileDescriptor(uri, "r")?.use { size = it.statSize.coerceAtLeast(0L) }
            }
            if ((width == 0 || height == 0) && !mimeType.startsWith("video/", ignoreCase = true)) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    if (opts.outWidth  > 0) width  = opts.outWidth
                    if (opts.outHeight > 0) height = opts.outHeight
                }
            }
            if (dateTaken == 0L && dateModified == 0L) {
                runCatching {
                    Regex("""(\d{13})""").find(displayName)?.value?.toLongOrNull()
                        ?.let { dateTaken = it }
                }
            }
            MediaPhoto(
                id           = -1L,
                uri          = uri,
                name         = displayName,
                size         = size,
                mimeType     = mimeType,
                dateTaken    = dateTaken,
                dateModified = dateModified,
                dateAdded    = dateAdded,
                width        = width,
                height       = height,
                latitude     = null,
                longitude    = null
            )
        } catch (e: Exception) { null }
    }
}

internal fun List<MediaPhoto>.applyFilterConfig(
    filter:   FilterConfig,
    isBucket: Boolean = false
): List<MediaPhoto> {
    return this // Now handled by MediaStoreRepository directly
}
