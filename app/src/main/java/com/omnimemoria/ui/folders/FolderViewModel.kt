package com.omnimemoria.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.MediaFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {

    private val _currentFolderSort = MutableStateFlow(FolderSortConfig())
    val currentFolderSort: StateFlow<FolderSortConfig> = _currentFolderSort.asStateFlow()

    val folders: Flow<PagingData<MediaFolder>> = currentFolderSort
        .flatMapLatest { sortConfig -> mediaStoreRepository.getFoldersPaged(sortConfig) }
        .cachedIn(viewModelScope)

    fun updateFolderSort(config: FolderSortConfig) {
        _currentFolderSort.value = config
    }
}
