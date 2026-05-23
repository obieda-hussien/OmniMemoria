package com.omnimemoria.ui.gallery

import android.content.Context
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

// ── sealed class للـ Grid items ──────────────────────────────────────────────────
sealed class GalleryItem {
    data class DateHeader(val label: String, val anchorPhotoId: Long) : GalleryItem()
    data class Photo(val photo: MediaPhoto)  : GalleryItem()
}

// ── FIX: يستخدم effectiveDateMs بدلاً من dateTaken مباشرة ───────────────────────
// Snapchat وتطبيقات كتير بتحفظ الصور بـ dateTaken = 0،
// فكانت بتتجمع كلها تحت "Unknown" بدل ما تتجمع بالتاريخ الصح.
// effectiveDateMs بيجرب dateTaken أولاً، لو 0 يجرب dateModified، لو 0 يجرب dateAdded.
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
    get(Calendar.YEAR)       == other.get(Calendar.YEAR) &&
    get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

// ─────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val sortPresetRepository:  SortPresetRepository,
    @ApplicationContext private val context: Context
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

    private val _compactTopBar = MutableStateFlow(false)
    val compactTopBar: StateFlow<Boolean> = _compactTopBar.asStateFlow()

    // ── Sort config ──────────────────────────────────────────────────────────────
    val activeSortConfig: StateFlow<SortConfig> = sortPresetRepository.getCurrentSort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortConfig())

    // ── Grouped photos with Date Headers ─────────────────────────────────────────
    // FIX: يستخدم toDateGroupLabel() على الـ MediaPhoto مباشرة (وبالتالي effectiveDateMs)
    val groupedPhotos: Flow<PagingData<GalleryItem>> = activeSortConfig
        .flatMapLatest { config -> mediaStoreRepository.getPhotosPaged(config) }
        .map { pagingData ->
            pagingData
                .map { photo -> GalleryItem.Photo(photo) as GalleryItem }
                .insertSeparators { before, after ->
                    val bLabel = (before as? GalleryItem.Photo)?.photo?.toDateGroupLabel()
                    val aLabel = (after  as? GalleryItem.Photo)?.photo?.toDateGroupLabel()
                        when {
                            after == null || after !is GalleryItem.Photo -> null
                            before == null || bLabel != aLabel ->
                                GalleryItem.DateHeader(
                                    label = aLabel ?: "",
                                    anchorPhotoId = after.photo.id
                                )
                            else -> null
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

    fun setCompactTopBar(enabled: Boolean) {
        _compactTopBar.value = enabled
    }
}
