package com.omnimemoria.ui.albums

import android.app.PendingIntent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.TrashRepository
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.ui.gallery.GalleryStateHolder
import com.omnimemoria.domain.model.FilterConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI Events ─────────────────────────────────────────────────────────────────

sealed class FolderDetailUiEvent {
    data class RequestMediaPermission(
        val pendingIntent: PendingIntent,
        val onConfirmed: () -> Unit
    ) : FolderDetailUiEvent()

    data class ShowSnackbar(val message: String) : FolderDetailUiEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaStoreRepository: MediaStoreRepository,
    private val galleryStateHolder:   GalleryStateHolder,
    private val trashRepository:      TrashRepository           // جديد
) : ViewModel() {

    val bucketId: String = savedStateHandle["bucketId"] ?: ""

    private val _sortConfig = MutableStateFlow(SortConfig())
    val sortConfig: StateFlow<SortConfig> = _sortConfig.asStateFlow()

    private val _folder = MutableStateFlow<MediaFolder?>(null)
    val folder: StateFlow<MediaFolder?> = _folder.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _uiEvents = Channel<FolderDetailUiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<FolderDetailUiEvent> = _uiEvents.receiveAsFlow()

    val photos: Flow<PagingData<MediaPhoto>> = _sortConfig
        .flatMapLatest { mediaStoreRepository.getPhotosByFolder(bucketId, it) }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            _folder.value = mediaStoreRepository.getFolderByBucketId(bucketId)
        }
    }

    fun updateSort(config: SortConfig) {
        _sortConfig.value = config
    }

    fun prepareForNavigation(photo: MediaPhoto) {
        galleryStateHolder.cachePendingPhoto(photo)
        galleryStateHolder.activeSortConfig.value = _sortConfig.value
        galleryStateHolder.activeFilter.value     = FilterConfig()
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    fun deleteSelected() {
        val ids   = _selectedIds.value.toList()
        val count = ids.size
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val photos = ids.mapNotNull { mediaStoreRepository.getPhotoById(it) }
            if (photos.isEmpty()) return@launch
            val pi = trashRepository.moveAllToTrash(photos)
            if (pi != null) {
                _uiEvents.send(FolderDetailUiEvent.RequestMediaPermission(pi) {
                    viewModelScope.launch {
                        clearSelection()
                        _uiEvents.send(
                            FolderDetailUiEvent.ShowSnackbar(
                                "$count item${if (count > 1) "s" else ""} moved to Trash"
                            )
                        )
                    }
                })
            } else {
                clearSelection()
                _uiEvents.send(
                    FolderDetailUiEvent.ShowSnackbar(
                        "$count item${if (count > 1) "s" else ""} moved to Trash"
                    )
                )
            }
        }
    }

    fun toggleSelection(photoId: Long) {
        _selectedIds.value = if (photoId in _selectedIds.value)
            _selectedIds.value - photoId
        else
            _selectedIds.value + photoId
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
}
