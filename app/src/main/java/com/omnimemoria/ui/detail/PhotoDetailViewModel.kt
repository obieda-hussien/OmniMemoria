package com.omnimemoria.ui.detail

import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
    private val savedStateHandle: SavedStateHandle,
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

    private val collection = galleryStateHolder.consumeCollection()
    private var loadJob: Job? = null

    init {
        collection?.let { source ->
            _photoList.value = listOf(source.seed)
            savedStateHandle["preview_ids"] = source.snapshot?.map { it.id }?.toLongArray()
            savedStateHandle["preview_bucket"] = source.bucketId
            savedStateHandle["preview_sort"] = arrayOf(source.sort.sortBy.name, source.sort.sortOrder.name)
            val f = source.filter
            savedStateHandle["preview_types"] = f.mediaTypes.map { it.name }.toTypedArray()
            savedStateHandle["preview_formats"] = f.mimeFormats.toTypedArray()
            savedStateHandle["preview_min_size"] = f.minSizeBytes
            savedStateHandle["preview_max_size"] = f.maxSizeBytes
            savedStateHandle["preview_start"] = f.dateRange?.first
            savedStateHandle["preview_end"] = f.dateRange?.last
            savedStateHandle["preview_mp"] = f.minResolutionMp
            savedStateHandle["preview_text"] = f.hasText
            savedStateHandle["preview_faces"] = f.hasFaces
            savedStateHandle["preview_phone"] = f.hasPhoneNumber
            savedStateHandle["preview_favorite"] = f.isFavorite
        }
    }
    private fun restoredFilter(): FilterConfig {
        val types = savedStateHandle.get<Array<String>>("preview_types") ?: return FilterConfig()
        val start = savedStateHandle.get<Long>("preview_start")
        val end = savedStateHandle.get<Long>("preview_end")
        return FilterConfig(
            mediaTypes = types.map { com.omnimemoria.domain.model.MediaType.valueOf(it) }.toSet(),
            mimeFormats = savedStateHandle.get<Array<String>>("preview_formats")?.toSet().orEmpty(),
            minSizeBytes = savedStateHandle["preview_min_size"], maxSizeBytes = savedStateHandle["preview_max_size"],
            dateRange = if (start != null && end != null) start..end else null,
            minResolutionMp = savedStateHandle["preview_mp"], hasText = savedStateHandle["preview_text"],
            hasFaces = savedStateHandle["preview_faces"], hasPhoneNumber = savedStateHandle["preview_phone"],
            isFavorite = savedStateHandle["preview_favorite"]
        )
    }

    fun loadAllPhotos(photoId: Long, bucketId: String?, externalUriStr: String? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            if (externalUriStr != null) {
                getPhotoFromUri(externalUriStr)?.let { _photoList.value = listOf(it) }
                _isFullListReady.value = true
                return@launch
            }
            if (_photoList.value.isEmpty()) {
                mediaStoreRepository.getPhotoById(photoId)?.let { _photoList.value = listOf(it) }
            }
            val sortValues = savedStateHandle.get<Array<String>>("preview_sort")
            val sort = collection?.sort ?: sortValues?.let { com.omnimemoria.domain.model.SortConfig(
                com.omnimemoria.domain.model.SortBy.valueOf(it[0]), com.omnimemoria.domain.model.SortOrder.valueOf(it[1])) }
                ?: sortPresetRepository.getCurrentSort().first()
            val restoredIds = savedStateHandle.get<LongArray>("preview_ids")
            val filter = collection?.filter ?: restoredFilter()
            val all = collection?.snapshot ?: mediaStoreRepository.getMatchingPhotos(
                sort = sort, filter = filter,
                bucketId = collection?.bucketId ?: savedStateHandle.get<String>("preview_bucket") ?: bucketId,
                ids = restoredIds?.toSet()
            ).let { photos ->
                if (restoredIds == null) photos else {
                    val byId = photos.associateBy { it.id }
                    restoredIds.map { byId[it] }.filterNotNull()
                }
            }
            val targetIndex = all.indexOfFirst { it.id == photoId }
            if (targetIndex >= 0) {
                _initialPage.value = targetIndex
                _photoList.value = all
            }
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
            if ((collection?.filter ?: restoredFilter()).isFavorite == true) {
                val ids = favoritesRepository.getAllFavoriteIds().first()
                _photoList.value = _photoList.value.filter { it.id in ids }
                if (_photoList.value.isEmpty()) _uiEvents.send(PhotoDetailUiEvent.NavigateBack)
            }
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
