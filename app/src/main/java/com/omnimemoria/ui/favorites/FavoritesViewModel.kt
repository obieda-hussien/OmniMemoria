package com.omnimemoria.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.FavoritePhoto
import com.omnimemoria.data.repository.FavoritesRepository
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state emitted to FavoritesScreen. */
data class FavoritesUiState(
    val photos:    List<MediaPhoto> = emptyList(),
    val isLoading: Boolean          = true
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository:  FavoritesRepository,
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {

    // ── Favorites count for the header chip ──────────────────────────────────
    val favoritesCount: StateFlow<Int> = favoritesRepository.getFavoritesCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ── Resolved MediaPhoto list sorted by addedAt DESC ──────────────────────
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.getAllSortedByDate().collect { favoriteRows ->
                val photos = resolvePhotos(favoriteRows)
                _uiState.value = FavoritesUiState(photos = photos, isLoading = false)
            }
        }
    }

    /**
     * Resolves each [FavoritePhoto] row into a [MediaPhoto] by querying
     * MediaStore. Rows whose IDs are no longer present in MediaStore are
     * silently skipped (photo was deleted from device).
     * Order is preserved (addedAt DESC from the DAO query).
     *
     * Must be suspend because [MediaStoreRepository.getPhotoById] is suspend
     * (it queries the corrupted-IDs DAO on the IO dispatcher).
     */
    private suspend fun resolvePhotos(rows: List<FavoritePhoto>): List<MediaPhoto> =
        rows.mapNotNull { row -> mediaStoreRepository.getPhotoById(row.id) }

    /** Remove a photo from favorites (called by long-press in the grid). */
    fun removeFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.removeFavorite(photoId)
        }
    }
}
