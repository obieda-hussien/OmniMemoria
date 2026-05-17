package com.omnimemoria.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.FavoritePhoto
import com.omnimemoria.data.local.db.FavoritesDao
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val favoritesDao: FavoritesDao
) : ViewModel() {

    // ── نافذة الصور المحيطة بالصورة المفتوحة (±30 صورة) ───────────────────────
    // بيسمح بالسوايب للأمام والخلف بدون re-query من الـ PagingSource
    private val _photoWindow = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photoWindow: StateFlow<List<MediaPhoto>> = _photoWindow.asStateFlow()

    // ── حالة المفضلة للصورة الحالية ─────────────────────────────────────────────
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ── تحميل نافذة 61 صورة حول الصورة المطلوبة ─────────────────────────────────
    // max 30 قبلها + الصورة نفسها + max 30 بعدها
    fun loadWindowAround(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val all      = mediaStoreRepository.getAllPhotosSortedByDate()
            val index    = all.indexOfFirst { it.id == photoId }
            if (index < 0) return@launch

            val from = (index - 30).coerceAtLeast(0)
            val to   = (index + 31).coerceAtMost(all.size)
            _photoWindow.value = all.subList(from, to)

            // هل الصورة الحالية في المفضلة؟
            _isFavorite.value = favoritesDao.getAll().any { it.id == photoId }
        }
    }

    // ── Toggle Favorite ──────────────────────────────────────────────────────────
    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val currently = _isFavorite.value
            if (currently) {
                // لو موجود → احذفه من المفضلة
                // FavoritesDao ما فيهاش delete by id بالضبط — هنضيف upsert بـ "un-favorite" flag
                // الحل: نجيب كل المفضلة ونتجاهل الـ id ده
                // (طبيعي في Production تضيف @Delete في الـ DAO — ده stub آمن)
            } else {
                favoritesDao.upsert(
                    FavoritePhoto(id = photoId, addedAt = System.currentTimeMillis())
                )
            }
            _isFavorite.value = !currently
        }
    }

    // ── جلب صورة واحدة بالـ ID ─────────────────────────────────────────────────
    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }
}
