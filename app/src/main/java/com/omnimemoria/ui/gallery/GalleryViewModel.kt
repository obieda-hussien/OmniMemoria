package com.omnimemoria.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omnimemoria.data.repository.MediaStats          // ← Import الجديد
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val sortPresetRepository:  SortPresetRepository
) : ViewModel() {

    // ── إحصائيات حقيقية من الجهاز ──────────────────────────────────────────────
    // بتبدأ بـ defaults (أصفار) وبتتحدّث في الـ background فور فتح الشاشة
    private val _mediaStats = MutableStateFlow(MediaStats())
    val mediaStats: StateFlow<MediaStats> = _mediaStats.asStateFlow()

    // ── الـ Sort الحالي ─────────────────────────────────────────────────────────
    val activeSortConfig: StateFlow<SortConfig> = sortPresetRepository.getCurrentSort()
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.Eagerly,
            initialValue   = SortConfig()
        )

    // ── صور مع Paging ──────────────────────────────────────────────────────────
    val photos: Flow<PagingData<MediaPhoto>> = activeSortConfig
        .flatMapLatest { config -> mediaStoreRepository.getPhotosPaged(config) }
        .cachedIn(viewModelScope)   // ← يمنع السكرول من الرجوع للأعلى عند إعادة البناء

    // ── جلب الإحصائيات في الخلفية فور إنشاء الـ ViewModel ─────────────────────
    // Dispatchers.IO → لا يبلوك الـ Main Thread
    init {
        viewModelScope.launch(Dispatchers.IO) {
            _mediaStats.value = mediaStoreRepository.getMediaStats()
        }
    }
}
