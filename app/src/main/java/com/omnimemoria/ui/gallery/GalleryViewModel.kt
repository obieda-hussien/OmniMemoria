package com.omnimemoria.ui.gallery

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.omnimemoria.data.repository.MediaStats
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── sealed class للـ Grid items ──────────────────────────────────────────────────
sealed class GalleryItem {
    data class PhotoItem(val photo: MediaPhoto) : GalleryItem()
    data class HeaderItem(val label: String) : GalleryItem()
}

// ── FIX: يستخدم effectiveDateMs بدلاً من dateTaken مباشرة ───────────────────────
// Snapchat وتطبيقات كتير بتحفظ الصور بـ dateTaken = 0،
// فكانت بتتجمع كلها تحت "Unknown" بدل ما تتجمع بالتاريخ الصح.
// effectiveDateMs بيجرب dateTaken أولاً، لو 0 يجرب dateModified، لو 0 يجرب dateAdded.
private fun MediaPhoto.groupKey(groupBy: GroupBy): String {
    if (effectiveDateMs <= 0L) return "unknown"
    val calendar = Calendar.getInstance().apply { timeInMillis = effectiveDateMs }
    return when (groupBy) {
        GroupBy.DAY -> "%04d-%02d-%02d".format(
            Locale.ROOT,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        GroupBy.MONTH -> "%04d-%02d".format(
            Locale.ROOT,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1
        )
        GroupBy.YEAR -> "%04d".format(Locale.ROOT, calendar.get(Calendar.YEAR))
        GroupBy.LOCATION -> if (latitude != null && longitude != null) "with_location" else "unknown_location"
    }
}

private fun MediaPhoto.groupTitle(groupBy: GroupBy): String {
    if (effectiveDateMs <= 0L) return "Unknown Date"
    val locale = Locale.getDefault()
    return when (groupBy) {
        GroupBy.DAY -> SimpleDateFormat("MMMM d, yyyy", locale).format(Date(effectiveDateMs))
        GroupBy.MONTH -> SimpleDateFormat("MMMM yyyy", locale).format(Date(effectiveDateMs))
        GroupBy.YEAR -> SimpleDateFormat("yyyy", locale).format(Date(effectiveDateMs))
        GroupBy.LOCATION -> if (latitude != null && longitude != null) "With location" else "Unknown location"
    }
}

private fun buildHeaderLabel(title: String, count: Int): String {
    val localizedCount = NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)
    val unit = if (count == 1) "photo" else "photos"
    return "$title • $localizedCount $unit"
}

// ─────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val sortPresetRepository:  SortPresetRepository
) : ViewModel() {

    // ── Stats ────────────────────────────────────────────────────────────────────
    private val _mediaStats = MutableStateFlow(MediaStats())
    val mediaStats: StateFlow<MediaStats> = _mediaStats.asStateFlow()

    // ── On This Day ──────────────────────────────────────────────────────────────
    private val _onThisDayPhotos = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val onThisDayPhotos: StateFlow<List<MediaPhoto>> = _onThisDayPhotos.asStateFlow()

    // ── Dynamic Accent ───────────────────────────────────────────────────────────
    private val _dynamicAccent = MutableStateFlow<Color?>(null)
    val dynamicAccent: StateFlow<Color?> = _dynamicAccent.asStateFlow()

    // ── Multi-select ─────────────────────────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isInSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Adaptive Grid columns ────────────────────────────────────────────────────
    private val _columnCount = MutableStateFlow(3)
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    // ── Sort config ──────────────────────────────────────────────────────────────
    val activeSortConfig: StateFlow<SortConfig> = sortPresetRepository.getCurrentSort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortConfig())

    val currentSortLabel: StateFlow<String> = activeSortConfig
        .map { config ->
            val sortByLabel = when (config.sortBy) {
                SortBy.DATE_TAKEN -> "Date"
                SortBy.DATE_MODIFIED -> "Modified"
                SortBy.SIZE -> "Size"
                SortBy.NAME -> "Name"
                SortBy.TYPE -> "Type"
                SortBy.RESOLUTION -> "Resolution"
                SortBy.DURATION -> "Duration"
                SortBy.FAVORITES_FIRST -> "Favorites"
            }
            val direction = if (config.sortOrder == SortOrder.ASCENDING) "↑" else "↓"
            "$sortByLabel $direction"
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Date ↓")

    private val _activeFilterConfig = MutableStateFlow(FilterConfig())
    val activeFilterConfig: StateFlow<FilterConfig> = _activeFilterConfig.asStateFlow()
    private val _activeBucketId = MutableStateFlow<String?>(null)
    val activeBucketId: StateFlow<String?> = _activeBucketId.asStateFlow()

    val groupedPhotos: Flow<PagingData<GalleryItem>> = combine(
        activeSortConfig,
        activeBucketId
    ) { sortConfig, bucketId -> sortConfig to bucketId }
        .flatMapLatest { (sortConfig, bucketId) ->
            if (sortConfig.groupBy == null) {
                mediaStoreRepository.getPhotosPaged(sortConfig, bucketId).map { pagingData ->
                    pagingData.map { photo -> GalleryItem.PhotoItem(photo) as GalleryItem }
                }
            } else {
                flow {
                    val groupBy = sortConfig.groupBy
                    val countsByKey = mediaStoreRepository
                        .getAllNonVaultPhotos(sortConfig, bucketId)
                        .groupingBy { photo -> photo.groupKey(groupBy) }
                        .eachCount()

                    emitAll(
                        mediaStoreRepository.getPhotosPaged(sortConfig, bucketId).map { pagingData ->
                            pagingData
                                .map { photo -> GalleryItem.PhotoItem(photo) as GalleryItem }
                                .insertSeparators { before, after ->
                                    val afterPhoto = (after as? GalleryItem.PhotoItem)?.photo ?: return@insertSeparators null
                                    val beforePhoto = (before as? GalleryItem.PhotoItem)?.photo
                                    val beforeKey = beforePhoto?.groupKey(groupBy)
                                    val afterKey = afterPhoto.groupKey(groupBy)
                                    if (before == null || beforeKey != afterKey) {
                                        val count = countsByKey[afterKey] ?: 0
                                        GalleryItem.HeaderItem(buildHeaderLabel(afterPhoto.groupTitle(groupBy), count))
                                    } else {
                                        null
                                    }
                                }
                        }
                    )
                }
            }
        }
        .cachedIn(viewModelScope)

    // ── Init ─────────────────────────────────────────────────────────────────────
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

    // ── Multi-select Actions ──────────────────────────────────────────────────────
    fun toggleSelection(photoId: Long) {
        _selectedIds.update { if (photoId in it) it - photoId else it + photoId }
    }
    fun clearSelection() { _selectedIds.value = emptySet() }
    fun selectAll(ids: List<Long>) { _selectedIds.value = ids.toSet() }

    // ── Adaptive Grid ─────────────────────────────────────────────────────────────
    fun onPinchZoom(zoomDelta: Float) {
        val c = _columnCount.value
        when {
            zoomDelta > 1.25f -> _columnCount.value = (c - 1).coerceAtLeast(2)
            zoomDelta < 0.75f -> _columnCount.value = (c + 1).coerceAtMost(5)
        }
    }
    fun setColumnCount(count: Int) { _columnCount.value = count.coerceIn(2, 5) }

    fun updateSort(config: SortConfig) {
        viewModelScope.launch {
            sortPresetRepository.saveLastUsed(config)
        }
    }

    fun updateFilter(config: FilterConfig) {
        _activeFilterConfig.value = config
    }

    fun updateBucketFilter(bucketId: String?) {
        _activeBucketId.value = bucketId
    }
}
