package com.omnimemoria.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val sortPresetRepository: SortPresetRepository
) : ViewModel() {

    val activeSortConfig: StateFlow<SortConfig> = sortPresetRepository.getCurrentSort()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SortConfig()
        )

    val photos: Flow<PagingData<MediaPhoto>> = activeSortConfig
        .flatMapLatest { config -> mediaStoreRepository.getPhotosPaged(config) }
}
