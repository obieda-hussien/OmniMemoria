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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import com.omnimemoria.domain.model.FilterConfig
import androidx.compose.foundation.lazy.grid.LazyGridState

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val galleryStateHolder: com.omnimemoria.ui.gallery.GalleryStateHolder
) : ViewModel() {

    val gridState = LazyGridState()
    val filterConfig = MutableStateFlow(FilterConfig())
    private val _folderSortConfig = MutableStateFlow(FolderSortConfig())
    val folderSortConfig: StateFlow<FolderSortConfig> = _folderSortConfig.asStateFlow()

    val folders: Flow<PagingData<MediaFolder>> = combine(folderSortConfig, filterConfig) { sort, filter -> sort to filter }
        .flatMapLatest { (sort, filter) ->
            mediaStoreRepository.observeMediaQueryChanges(filter = filter).onStart { emit(Unit) }
                .flatMapLatest { mediaStoreRepository.getFoldersPaged(sort, filter) }
        }.cachedIn(viewModelScope)

    fun prepareFolderNavigation() { galleryStateHolder.prepareFolder(filterConfig.value) }

    fun updateFolderSort(config: FolderSortConfig, filter: FilterConfig) {
        _folderSortConfig.value = config
        filterConfig.value = filter
    }
}
