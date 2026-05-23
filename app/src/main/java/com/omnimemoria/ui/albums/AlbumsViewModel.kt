package com.omnimemoria.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.MediaFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {

    private val _folderSortConfig = MutableStateFlow(FolderSortConfig())
    val folderSortConfig: StateFlow<FolderSortConfig> = _folderSortConfig.asStateFlow()

    val folders: Flow<PagingData<MediaFolder>> = folderSortConfig
        .flatMapLatest { config -> mediaStoreRepository.getFoldersPaged(config) }
        .cachedIn(viewModelScope)

    fun updateFolderSort(config: FolderSortConfig) {
        _folderSortConfig.value = config
    }
}
