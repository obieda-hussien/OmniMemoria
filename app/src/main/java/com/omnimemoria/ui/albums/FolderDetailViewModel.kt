package com.omnimemoria.ui.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {
    val bucketId: String = savedStateHandle["bucketId"] ?: ""

    private val _sortConfig = MutableStateFlow(SortConfig())
    val sortConfig: StateFlow<SortConfig> = _sortConfig.asStateFlow()

    private val _folder = MutableStateFlow<MediaFolder?>(null)
    val folder: StateFlow<MediaFolder?> = _folder.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val photos: Flow<PagingData<MediaPhoto>> = sortConfig
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

    fun toggleSelection(photoId: Long) {
        _selectedIds.value = if (photoId in _selectedIds.value) {
            _selectedIds.value - photoId
        } else {
            _selectedIds.value + photoId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
}
