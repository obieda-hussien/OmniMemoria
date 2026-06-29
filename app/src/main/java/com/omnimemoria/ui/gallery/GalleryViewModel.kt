package com.omnimemoria.ui.gallery

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import com.omnimemoria.data.repository.FavoritesRepository
import com.omnimemoria.data.repository.MediaStats
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.data.repository.TrashRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.MediaType
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── One-shot UI events ─────────────────────────────────────────────────────────

sealed class GalleryUiEvent {
    data class RequestMediaPermission(
        val pendingIntent: PendingIntent,
        val onConfirmed:   () -> Unit
    ) : GalleryUiEvent()

    data class ShowSnackbar(
        val message:     String,
        val actionLabel: String? = null,
        val onAction:    (() -> Unit)? = null
    ) : GalleryUiEvent()
}

sealed class GalleryItem {
    data class DateHeader(val label: String, val anchorPhotoId: Long) : GalleryItem()
    data class Photo(val photo: MediaPhoto, val isFavorite: Boolean = false) : GalleryItem()
}


private fun MediaPhoto.toDateGroupLabel(): String {
    val ms = this.effectiveDateMs
    if (ms <= 0L) return "Unknown Date"
    val today     = Calendar.getInstance()
    val yesterday = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
    val target    = Calendar.getInstance().also { it.timeInMillis = ms }
    return when {
        target.isSameDay(today)     -> "Today"
        target.isSameDay(yesterday) -> "Yesterday"
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(ms))
        else ->
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(ms))
    }
}

private fun Calendar.isSameDay(other: Calendar) =
    get(Calendar.YEAR)        == other.get(Calendar.YEAR) &&
    get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val sortPresetRepository:  SortPresetRepository,
    private val galleryStateHolder:    GalleryStateHolder,
    private val favoritesRepository:   FavoritesRepository,
    private val trashRepository:       TrashRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── Media stats ────────────────────────────────────────────────────────────
    private val _mediaStats = MutableStateFlow(MediaStats())
    val mediaStats: StateFlow<MediaStats> = _mediaStats.asStateFlow()

    private val _onThisDayPhotos = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val onThisDayPhotos: StateFlow<List<MediaPhoto>> = _onThisDayPhotos.asStateFlow()

    private val _dynamicAccent = MutableStateFlow<Color?>(null)
    val dynamicAccent: StateFlow<Color?> = _dynamicAccent.asStateFlow()

    // ── Selection ──────────────────────────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isInSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Layout ─────────────────────────────────────────────────────────────────
    private val _columnCount = MutableStateFlow(3)
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    private val _compactTopBar = MutableStateFlow(false)
    val compactTopBar: StateFlow<Boolean> = _compactTopBar.asStateFlow()

    // ── Filter / sort ──────────────────────────────────────────────────────────
    private val _currentFilter = MutableStateFlow(FilterConfig())
    val currentFilter: StateFlow<FilterConfig> = _currentFilter.asStateFlow()

    val activeFilterCount: StateFlow<Int> = _currentFilter.map { config ->
        var count = 0
        if (config.mediaTypes != setOf(MediaType.IMAGE, MediaType.VIDEO)) count++
        if (config.mimeFormats.isNotEmpty()) count++
        if (config.minSizeBytes != null || config.maxSizeBytes != null) count++
        if (config.dateRange != null) count++
        if (config.minResolutionMp != null) count++
        if (config.hasText != null) count++
        if (config.hasFaces != null) count++
        if (config.hasPhoneNumber != null) count++
        if (config.isFavorite != null) count++
        count
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _localSortConfig = MutableStateFlow<SortConfig?>(null)

    val activeSortConfig: StateFlow<SortConfig> = sortPresetRepository.getCurrentSort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortConfig())

    // ── Favorites ──────────────────────────────────────────────────────────────
    val favoriteIds: StateFlow<Set<Long>> = favoritesRepository.getAllFavoriteIds().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val mediaStoreVersion = MutableStateFlow(0)

    // ── UI events ──────────────────────────────────────────────────────────────
    private val _uiEvents = Channel<GalleryUiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<GalleryUiEvent> = _uiEvents.receiveAsFlow()

    // ── Paging flow ────────────────────────────────────────────────────────────
    val groupedPhotos: Flow<PagingData<GalleryItem>> = combine(
        _localSortConfig.combine(activeSortConfig) { local, repo -> local ?: repo },
        _currentFilter,
        mediaStoreVersion
    ) { config, filter, _ -> Pair(config, filter) }
        .flatMapLatest { (config, currentFilter) ->
            mediaStoreRepository.getPhotosPaged(config, currentFilter).cachedIn(viewModelScope)
        }
        .combine(favoriteIds) { pagingData, favIds -> Pair(pagingData, favIds) }
        .combine(_currentFilter) { (pagingData, favIds), filter ->
            // We let MediaStoreRepository do most of the filtering now, but we still map to GalleryItem
            pagingData
                .map { photo -> GalleryItem.Photo(photo, isFavorite = photo.id in favIds) as GalleryItem }
                .insertSeparators { before, after ->
                    val bLabel = (before as? GalleryItem.Photo)?.photo?.toDateGroupLabel()
                    val aLabel = (after  as? GalleryItem.Photo)?.photo?.toDateGroupLabel()
                    when {
                        after == null || after !is GalleryItem.Photo -> null
                        before == null || bLabel != aLabel ->
                            GalleryItem.DateHeader(
                                label         = aLabel ?: "",
                                anchorPhotoId = after.photo.id
                            )
                        else -> null
                    }
                }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            refreshHomeSummary()
        }
        viewModelScope.launch {
            mediaStoreRepository.observeMediaStoreChanges().collectLatest {
                mediaStoreVersion.update { it + 1 }
                refreshHomeSummary()
            }
        }
    }

    private suspend fun refreshHomeSummary() = withContext(Dispatchers.IO) {
        _mediaStats.value = mediaStoreRepository.getMediaStats()
        _onThisDayPhotos.value = mediaStoreRepository.getPhotosOnThisDay()
        val uri = mediaStoreRepository.getMostRecentPhotoUri()
        _dynamicAccent.value = uri?.let { mediaStoreRepository.extractDominantColor(it) }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    fun prepareForNavigation(photo: MediaPhoto) {
        galleryStateHolder.cachePendingPhoto(photo)
        galleryStateHolder.activeSortConfig.value =
            _localSortConfig.value ?: activeSortConfig.value
        galleryStateHolder.activeFilter.value = _currentFilter.value
    }

    fun updateSortAndFilter(config: SortConfig, filter: FilterConfig) {
        _localSortConfig.value   = config
        _currentFilter.value     = filter
        galleryStateHolder.activeSortConfig.value = config
        galleryStateHolder.activeFilter.value     = filter
        viewModelScope.launch(Dispatchers.IO) {
            sortPresetRepository.updateActive(config)
        }
    }

    // ── Favorites ──────────────────────────────────────────────────────────────

    fun toggleFavoriteInGrid(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.toggleFavorite(photoId)
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /**
     * يحوّل الصور المحددة إلى Trash — بيحل الـ IDs من MediaStore أولاً.
     * جديد: بيتعامل مع الـ IntentSender على Android 11+.
     */
    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // Because deleted items in UI might be instantly hidden, but we just need a MediaPhoto object.
            // Even if getPhotoById fails (e.g., if it excludes trashed items or cache mismatch),
            // we must ensure we get the item. But `getPhotoById` works normally for non-trashed items.
            // The actual issue might be that `getPhotoById` relies on `QueryBuilder(FilterConfig())`
            // which filters out TRASHED items on API 30+.
            // If it returns null, `deleteSelectedPhotos` gets an empty list!
            val photos = ids.mapNotNull { mediaStoreRepository.getPhotoById(it) }
            if (photos.isNotEmpty()) deleteSelectedPhotos(photos)
        }
    }

    fun deleteSelectedPhotos(selectedPhotos: List<MediaPhoto>) {
        if (selectedPhotos.isEmpty()) return
        val count = selectedPhotos.size
        viewModelScope.launch(Dispatchers.IO) {
            val deletedIds = selectedPhotos.map { it.id }
            trashRepository.moveToTrashWithFallback(
                photos            = selectedPhotos,
                onNeedsPermission = { pi, onConfirmed ->
                    _uiEvents.send(
                        GalleryUiEvent.RequestMediaPermission(
                            pendingIntent = pi,
                            onConfirmed   = { viewModelScope.launch(Dispatchers.IO) { onConfirmed() } }
                        )
                    )
                },
                onDone = {
                    clearSelection()
                    _uiEvents.send(buildUndoEvent(count, deletedIds))
                }
            )
        }
    }

    private fun buildUndoEvent(count: Int, deletedIds: List<Long>): GalleryUiEvent.ShowSnackbar =
        GalleryUiEvent.ShowSnackbar(
            message     = "$count photo${if (count > 1) "s" else ""} moved to Trash",
            actionLabel = "Undo",
            onAction    = {
                viewModelScope.launch(Dispatchers.IO) {
                    val items = trashRepository.getTrashItemsByIds(deletedIds)
                    val pendingIntent = trashRepository.restoreFromTrashIntent(items)

                    if (pendingIntent != null) {
                        _uiEvents.send(
                            GalleryUiEvent.RequestMediaPermission(
                                pendingIntent = pendingIntent,
                                onConfirmed   = {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        trashRepository.confirmRestoreFromTrash(items)
                                    }
                                }
                            )
                        )
                    } else {
                        trashRepository.confirmRestoreFromTrash(items)
                    }
                }
            }
        )

    // ── Selection ──────────────────────────────────────────────────────────────

    fun toggleSelection(photoId: Long) {
        _selectedIds.update { if (photoId in it) it - photoId else it + photoId }
    }
    fun clearSelection()               { _selectedIds.value = emptySet() }
    fun selectAll(ids: List<Long>)     { _selectedIds.value = ids.toSet() }

    // ── Zoom ───────────────────────────────────────────────────────────────────

    fun onPinchZoom(zoomDelta: Float) {
        val c = _columnCount.value
        when {
            zoomDelta > 1.25f -> _columnCount.value = (c - 1).coerceAtLeast(2)
            zoomDelta < 0.75f -> _columnCount.value = (c + 1).coerceAtMost(5)
        }
    }
    fun setColumnCount(count: Int)         { _columnCount.value   = count.coerceIn(2, 5) }
    fun setCompactTopBar(enabled: Boolean) { _compactTopBar.value = enabled }
}
