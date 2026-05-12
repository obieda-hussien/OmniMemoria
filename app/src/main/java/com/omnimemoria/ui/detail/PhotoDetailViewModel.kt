package com.omnimemoria.ui.detail

import androidx.lifecycle.ViewModel
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {
    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }
}
