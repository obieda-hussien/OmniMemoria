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
import kotlinx.coroutines.flow.collectLatest
import com.omnimemoria.ui.gallery.GalleryStateHolder
import com.omnimemoria.ui.gallery.PreviewCollection
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.FilterConfig
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val mediaStoreRepository: MediaStoreRepository,
    private val galleryStateHolder: GalleryStateHolder
) : ViewModel() {

    val gridState = LazyGridState()
    val sortConfig = MutableStateFlow(SortConfig())
    val filterConfig = MutableStateFlow(FilterConfig())
    // ── Resolved MediaPhoto list sorted by addedAt DESC ──────────────────────
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState
    val favoritesCount = uiState.map { it.photos.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(favoritesRepository.getAllSortedByDate(), sortConfig, filterConfig,
                mediaStoreRepository.observeMediaStoreChanges().onStart { emit(Unit) }) { rows, sort, filter, _ -> Triple(rows, sort, filter) }
                .collectLatest { (rows, sort, filter) ->
                    val photos = mediaStoreRepository.getMatchingPhotos(sort, filter, ids = rows.map { it.id }.toSet())
                    _uiState.value = FavoritesUiState(photos, false)
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
        mediaStoreRepository.getPhotosByIds(rows.map { it.id })

    fun prepareForNavigation(photoId: Long) {
        val photos = uiState.value.photos
        val seed = photos.firstOrNull { it.id == photoId } ?: return
        galleryStateHolder.prepare(PreviewCollection(seed, sortConfig.value, filterConfig.value.copy(isFavorite = true), snapshot = photos))
    }

    fun updateSortAndFilter(sort: SortConfig, filter: FilterConfig) { sortConfig.value = sort; filterConfig.value = filter }

    /** Remove a photo from favorites (called by long-press in the grid). */
    fun removeFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.removeFavorite(photoId)
        }
    }
}
