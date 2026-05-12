package com.omnimemoria.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class GalleryViewModel @Inject constructor(
    mediaStoreRepository: MediaStoreRepository
) : ViewModel() {
    val photos: Flow<PagingData<MediaPhoto>> = mediaStoreRepository.getPhotosPaged(SortConfig())
}
