package com.omnimemoria.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.repository.FavoritesRepository
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val favoritesRepository: FavoritesRepository,
    private val sortPresetRepository: SortPresetRepository
) : ViewModel() {

    // ── FIX 1: كل الصور بدون حد — مش ±30 ────────────────────────────────────
    // السبب الجذري لمشكلة "31/31":
    //   الكود القديم كان بيعمل subList(index-30, index+31) يعني بيحصر
    //   الـ pager في نافذة ثابتة من 61 صورة بحد أقصى. كل مرة بتفتح صورة
    //   في مكان مختلف من الـ gallery بيتغير الـ window وبتتغير الـ "31/31".
    //
    // الحل: نحمّل كل الصور غير الـ vault في list واحدة. على الأجهزة الحديثة
    // قائمة بـ 10,000 MediaPhoto بتاخد ~5MB في الـ heap — مقبول تماماً.
    // الـ HorizontalPager بيعرض صورة واحدة بس في كل وقت، يعني Coil مش
    // هيحمّل أكثر من 3-5 Bitmaps في نفس الوقت (current + neighbors prefetch).

    private val _photoList  = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photoList: StateFlow<List<MediaPhoto>> = _photoList.asStateFlow()

    private val _initialPage = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

    val favoriteIds: StateFlow<Set<Long>> = favoritesRepository.getAllFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ── تحميل كل الصور مرة واحدة ─────────────────────────────────────────────
    fun loadAllPhotos(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaStoreRepository.getPhotoById(photoId)?.let { seed ->
                _photoList.value = listOf(seed)
                _initialPage.value = 0
            }

            // جلب الترتيب الحالي لكي يتطابق تماماً مع الـ Grid
            val currentSortConfig = sortPresetRepository.getCurrentSort().first()
            val all = mediaStoreRepository.getAllNonVaultPhotos(currentSortConfig)

            // لو الصورة مش موجودة (vault item أو اتحذفت) → اعرضها وحدها
            val targetIndex = all.indexOfFirst { it.id == photoId }
            if (targetIndex < 0) {
                val single = mediaStoreRepository.getPhotoById(photoId)
                if (single != null) {
                    _photoList.value  = listOf(single)
                    _initialPage.value = 0
                }
                return@launch
            }

            _photoList.value   = all
            _initialPage.value = targetIndex
        }
    }

    // ── Toggle Favorite ──────────────────────────────────────────────────────────
    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.toggleFavorite(photoId)
        }
    }

    fun isFavorite(photoId: Long): Flow<Boolean> = favoritesRepository.isFavorite(photoId)

    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }
}
