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
    private val _photoWindow = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photoWindow: StateFlow<List<MediaPhoto>> = _photoWindow.asStateFlow()

    // ── حالة المفضلة للصورة الحالية ─────────────────────────────────────────────
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ── تحميل نافذة 61 صورة حول الصورة المطلوبة ─────────────────────────────────
    // FIX: كنا بنستخدم getAllPhotosSortedByDate() اللي بترجع كل الصور بما فيها
    // صور الـ Vault. لما الـ Gallery بتستثني الـ Vault items، الـ index بيختلف
    // فالصورة اللي بتفتح مش اللي اضغطت عليها.
    //
    // الحل: getAllNonVaultPhotosSortedByDate() بتعمل نفس الـ filter اللي
    // بتعمله الـ PagingSource في الـ Gallery.
    fun loadWindowAround(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val all   = mediaStoreRepository.getAllNonVaultPhotosSortedByDate()
            val index = all.indexOfFirst { it.id == photoId }
            if (index < 0) {
                // الصورة مش في القائمة — ممكن تكون vault item أو اتحذفت
                // ارجع نافذة فيها الصورة وحدها عشان الـ UI ميكسرش
                val single = mediaStoreRepository.getPhotoById(photoId)
                if (single != null) _photoWindow.value = listOf(single)
                return@launch
            }

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
            if (!currently) {
                favoritesDao.upsert(
                    FavoritePhoto(id = photoId, addedAt = System.currentTimeMillis())
                )
            }
            // TODO Phase 3: add deleteFavorite(photoId) to FavoritesDao
            _isFavorite.value = !currently
        }
    }

    // ── جلب صورة واحدة بالـ ID ─────────────────────────────────────────────────
    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }
}
