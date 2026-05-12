package com.omnimemoria.ui.detail

import androidx.lifecycle.ViewModel
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {
    fun getPhoto(photoId: Long): MediaPhoto? = mediaStoreRepository.getPhotoById(photoId)
}
