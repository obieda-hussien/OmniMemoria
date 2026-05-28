package com.omnimemoria.ui.gallery

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import com.omnimemoria.data.repository.MediaStats
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class GalleryItem {
    data class DateHeader(val label: String, val anchorPhotoId: Long) : GalleryItem()
    data class Photo(val photo: MediaPhoto) : GalleryItem()
}

enum class MediaFilter { ALL, PHOTOS_ONLY, VIDEOS_ONLY }

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
    // ── FIX: inject GalleryStateHolder so we can cache the tapped photo
    // and sync sort/filter before navigating to PhotoDetailScreen ──────────────
    private val galleryStateHolder:    GalleryStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _mediaStats = MutableStateFlow(MediaStats())
    val mediaStats: StateFlow<MediaStats> = _mediaStats.asStateFlow()

    private val _onThisDayPhotos = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val onThisDayPhotos: StateFlow<List<MediaPhoto>> = _onThisDayPhotos.asStateFlow()

    private val _dynamicAccent = MutableStateFlow<Color?>(null)
    val dynamicAccent: StateFlow<Color?> = _dynamicAccent.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isInSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _columnCount = MutableStateFlow(3)
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    private val _compactTopBar = MutableStateFlow(false)
    val compactTopBar: StateFlow<Boolean> = _compactTopBar.asStateFlow()

    private val _currentFilter = MutableStateFlow(MediaFilter.ALL)
    val currentFilter: StateFlow<MediaFilter> = _currentFilter.asStateFlow()

    private val _localSortConfig = MutableStateFlow<SortConfig?>(null)

    val activeSortConfig: StateFlow<SortConfig> = sortPresetRepository.getCurrentSort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortConfig())

    val groupedPhotos: Flow<PagingData<GalleryItem>> = combine(
        _localSortConfig.combine(activeSortConfig) { local, repo -> local ?: repo },
        _currentFilter
    ) { config, filter -> Pair(config, filter) }
        .flatMapLatest { (config, filter) ->
            mediaStoreRepository.getPhotosPaged(config).map { pagingData ->
                val filteredData = when (filter) {
                    MediaFilter.ALL         -> pagingData
                    MediaFilter.PHOTOS_ONLY -> pagingData.filter { !it.mimeType.startsWith("video/", ignoreCase = true) }
                    MediaFilter.VIDEOS_ONLY -> pagingData.filter {  it.mimeType.startsWith("video/", ignoreCase = true) }
                }

                filteredData
                    .map { photo -> GalleryItem.Photo(photo) as GalleryItem }
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
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _mediaStats.value = mediaStoreRepository.getMediaStats()
        }
        viewModelScope.launch {
            mediaStoreRepository.observeMediaStoreChanges().collectLatest {
                _mediaStats.value = mediaStoreRepository.getMediaStats()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val uri = mediaStoreRepository.getMostRecentPhotoUri() ?: return@launch
            _dynamicAccent.value = mediaStoreRepository.extractDominantColor(uri)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _onThisDayPhotos.value = mediaStoreRepository.getPhotosOnThisDay()
        }
    }

    // ── FIX: call this right before navigating to PhotoDetailScreen ───────────
    // Caches the tapped photo (zero-IO seed) and syncs the active sort/filter
    // so PhotoDetailViewModel builds the SAME swipe window as the gallery grid.
    fun prepareForNavigation(photo: MediaPhoto) {
        galleryStateHolder.cachePendingPhoto(photo)
        galleryStateHolder.activeSortConfig.value =
            _localSortConfig.value ?: activeSortConfig.value
        galleryStateHolder.activeFilter.value = _currentFilter.value
    }

    fun updateSortAndFilter(config: SortConfig, filter: MediaFilter) {
        _localSortConfig.value   = config
        _currentFilter.value     = filter
        // Keep state holder in sync immediately so any in-flight navigation
        // that fires right after this call also gets the correct config.
        galleryStateHolder.activeSortConfig.value = config
        galleryStateHolder.activeFilter.value     = filter
    }

    fun toggleSelection(photoId: Long) {
        _selectedIds.update { if (photoId in it) it - photoId else it + photoId }
    }
    fun clearSelection() { _selectedIds.value = emptySet() }
    fun selectAll(ids: List<Long>) { _selectedIds.value = ids.toSet() }

    fun onPinchZoom(zoomDelta: Float) {
        val c = _columnCount.value
        when {
            zoomDelta > 1.25f -> _columnCount.value = (c - 1).coerceAtLeast(2)
            zoomDelta < 0.75f -> _columnCount.value = (c + 1).coerceAtMost(5)
        }
    }
    fun setColumnCount(count: Int)      { _columnCount.value   = count.coerceIn(2, 5) }
    fun setCompactTopBar(enabled: Boolean) { _compactTopBar.value = enabled }
}
