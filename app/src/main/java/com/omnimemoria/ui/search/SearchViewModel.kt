package com.omnimemoria.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.data.preferences.AppPreferences
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.*
import com.omnimemoria.ui.gallery.GalleryStateHolder
import com.omnimemoria.ui.gallery.PreviewCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class SearchResultState {
    data object Idle : SearchResultState()
    data object Searching : SearchResultState()
    data class Results(val photos: List<MediaPhoto>, val query: String) : SearchResultState()
    data class Empty(val query: String) : SearchResultState()
    data class Error(val query: String) : SearchResultState()
}
data class SearchQuickFilterCounts(val phoneNumbers: Int = 0, val emails: Int = 0, val faces: Int = 0)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val photoIntelligenceDao: PhotoIntelligenceDao,
    private val mediaStoreRepository: MediaStoreRepository,
    private val appPreferences: AppPreferences,
    private val galleryStateHolder: GalleryStateHolder
) : ViewModel() {
    private val separator = '\u001F'
    val gridState = LazyGridState()
    val query = MutableStateFlow("")
    private val quickFilter = MutableStateFlow<QuickFilterType?>(null)
    val sortConfig = MutableStateFlow(SortConfig())
    val filterConfig = MutableStateFlow(FilterConfig())
    private val refreshVersion = MutableStateFlow(0)
    val recentSearches = appPreferences.getString(AppPreferences.PreferencesKeys.RECENT_SEARCHES)
        .map { raw -> raw.split(separator).map(String::trim).filter(String::isNotBlank).take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _searchResults = MutableStateFlow<SearchResultState>(SearchResultState.Idle)
    val searchResults = _searchResults.asStateFlow()
    private val _counts = MutableStateFlow(SearchQuickFilterCounts())
    val quickFilterCounts = _counts.asStateFlow()

    private data class Request(val text: String, val quick: QuickFilterType?, val sort: SortConfig, val filter: FilterConfig, val version: Int)
    init {
        viewModelScope.launch {
            mediaStoreRepository.observeMediaQueryChanges(SortConfig(SortBy.FAVORITES_FIRST)).collect { refreshVersion.value++ }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _counts.value = SearchQuickFilterCounts(
                mediaStoreRepository.getPhotosByIds(photoIntelligenceDao.getIdsWithPhoneNumbers()).size,
                mediaStoreRepository.getPhotosByIds(photoIntelligenceDao.getIdsWithEmails()).size,
                mediaStoreRepository.getPhotosByIds(photoIntelligenceDao.getIdsWithFaces()).size
            )
        }
        viewModelScope.launch {
            combine(query, quickFilter, sortConfig, filterConfig, refreshVersion) { text, quick, sort, filter, version ->
                Request(text.trim(), quick, sort, filter, version)
            }.distinctUntilChanged().collectLatest { request ->
                if (request.text.isEmpty() && request.quick == null) {
                    _searchResults.value = SearchResultState.Idle
                    return@collectLatest
                }
                _searchResults.value = SearchResultState.Searching
                // Delay inside collectLatest: the next keystroke cancels even the pending delay/query.
                if (request.quick == null) delay(180)
                try {
                    val photos = withContext(Dispatchers.IO) { runSearch(request) }
                    ensureActive()
                    val label = request.quick?.label ?: request.text
                    _searchResults.value = if (photos.isEmpty()) SearchResultState.Empty(label) else SearchResultState.Results(photos, label)
                    if (request.quick == null && photos.isNotEmpty()) saveRecent(request.text)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { _searchResults.value = SearchResultState.Error(request.text) }
            }
        }
    }
    fun setQuery(value: String) { quickFilter.value = null; query.value = value }
    fun clearQuery() { quickFilter.value = null; query.value = ""; _searchResults.value = SearchResultState.Idle }
    fun retry() { refreshVersion.value++ }
    fun updateSortAndFilter(sort: SortConfig, filter: FilterConfig) { sortConfig.value = sort; filterConfig.value = filter }
    fun applyQuickFilter(type: QuickFilterType) { query.value = ""; quickFilter.value = type }
    fun prepareForNavigation(id: Long) {
        val photos = (_searchResults.value as? SearchResultState.Results)?.photos ?: return
        val seed = photos.firstOrNull { it.id == id } ?: return
        galleryStateHolder.prepare(PreviewCollection(seed, sortConfig.value, filterConfig.value, snapshot = photos))
    }
    fun deleteRecent(term: String) { viewModelScope.launch {
        appPreferences.setString(AppPreferences.PreferencesKeys.RECENT_SEARCHES,
            recentSearches.value.filterNot { it == term }.joinToString(separator.toString()))
    } }
    private suspend fun runSearch(request: Request): List<MediaPhoto> {
        val ids = when (request.quick) {
            QuickFilterType.PHONE_NUMBERS -> photoIntelligenceDao.getIdsWithPhoneNumbers().toSet()
            QuickFilterType.EMAILS -> photoIntelligenceDao.getIdsWithEmails().toSet()
            QuickFilterType.PEOPLE -> photoIntelligenceDao.getIdsWithFaces().toSet()
            else -> null
        }
        if (request.quick == QuickFilterType.THIS_MONTH) {
            val start = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 1) }.timeInMillis - 1
            val month = start.timeInMillis..end
            val requested = request.filter.dateRange
            val intersected = if (requested == null) month else maxOf(month.first, requested.first)..minOf(month.last, requested.last)
            return mediaStoreRepository.getMatchingPhotos(request.sort, request.filter.copy(dateRange = intersected))
        }
        if (ids != null) return mediaStoreRepository.getMatchingPhotos(request.sort, request.filter, ids = ids)
        // Quote individual FTS terms so punctuation cannot change query syntax.
        val terms = request.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val fts = terms.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
        val matches = try { photoIntelligenceDao.searchByText(fts).map { it.id }.toSet() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { emptySet<Long>() }
        return mediaStoreRepository.searchMatchingPhotos(request.text, request.sort, request.filter, matches)
    }
    private suspend fun saveRecent(term: String) {
        val updated = (listOf(term) + recentSearches.value.filterNot { it.equals(term, true) }).take(5)
        appPreferences.setString(AppPreferences.PreferencesKeys.RECENT_SEARCHES, updated.joinToString(separator.toString()))
    }
}
enum class QuickFilterType(val label: String) { PHONE_NUMBERS("Phone Numbers"), EMAILS("Emails"), PEOPLE("People"), THIS_MONTH("This Month") }
