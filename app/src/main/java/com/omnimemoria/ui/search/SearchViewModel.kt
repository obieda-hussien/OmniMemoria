package com.omnimemoria.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.data.preferences.AppPreferences
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchResultState {
    data object Idle : SearchResultState()
    data object Searching : SearchResultState()
    data class Results(val photos: List<MediaPhoto>, val query: String) : SearchResultState()
    data class Empty(val query: String) : SearchResultState()
}

data class SearchQuickFilterCounts(
    val phoneNumbers: Int = 0,
    val emails: Int = 0,
    val faces: Int = 0
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val photoIntelligenceDao: PhotoIntelligenceDao,
    private val mediaStoreRepository: MediaStoreRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {
    private val separator = '\u001F'

    val query = MutableStateFlow("")

    val recentSearches: StateFlow<List<String>> = appPreferences
        .getString(AppPreferences.PreferencesKeys.RECENT_SEARCHES)
        .map { raw ->
            raw.split(separator)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(5)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchResults = MutableStateFlow<SearchResultState>(SearchResultState.Idle)
    val searchResults: StateFlow<SearchResultState> = _searchResults.asStateFlow()

    val quickFilterCounts: StateFlow<SearchQuickFilterCounts> = combine(
        query.map { it },
        appPreferences.getString(AppPreferences.PreferencesKeys.RECENT_SEARCHES)
    ) { _, _ -> Unit }
        .map {
            SearchQuickFilterCounts(
                phoneNumbers = photoIntelligenceDao.getIdsWithPhoneNumbers().size,
                emails = photoIntelligenceDao.getIdsWithEmails().size,
                faces = photoIntelligenceDao.getIdsWithFaces().size
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchQuickFilterCounts())

    init {
        viewModelScope.launch {
            query
                .debounce(300)
                .collect { q ->
                    val trimmed = q.trim()
                    if (trimmed.isEmpty()) {
                        _searchResults.value = SearchResultState.Idle
                        return@collect
                    }
                    _searchResults.value = SearchResultState.Searching
                    val results = runSearch(trimmed)
                    _searchResults.value = if (results.isEmpty()) {
                        SearchResultState.Empty(trimmed)
                    } else {
                        saveRecent(trimmed)
                        SearchResultState.Results(results, trimmed)
                    }
                }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
        _searchResults.value = SearchResultState.Idle
    }

    fun deleteRecent(term: String) {
        viewModelScope.launch {
            val updated = recentSearches.value.filterNot { it == term }.take(5)
            appPreferences.setString(AppPreferences.PreferencesKeys.RECENT_SEARCHES, updated.joinToString(separator.toString()))
        }
    }

    fun applyQuickFilter(type: QuickFilterType) {
        viewModelScope.launch {
            _searchResults.value = SearchResultState.Searching
            val ids = when (type) {
                QuickFilterType.PHONE_NUMBERS -> photoIntelligenceDao.getIdsWithPhoneNumbers()
                QuickFilterType.EMAILS -> photoIntelligenceDao.getIdsWithEmails()
                QuickFilterType.PEOPLE -> photoIntelligenceDao.getIdsWithFaces()
                QuickFilterType.THIS_MONTH -> emptyList()
            }
            val photos = withContext(Dispatchers.IO) {
                if (type == QuickFilterType.THIS_MONTH) {
                    val all = mediaStoreRepository.getAllPhotos(com.omnimemoria.domain.model.SortConfig())
                    val now = java.util.Calendar.getInstance()
                    all.filter { p ->
                        val ms = p.effectiveDateMs
                        if (ms <= 0L) return@filter false
                        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
                        c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                            c.get(java.util.Calendar.MONTH) == now.get(java.util.Calendar.MONTH)
                    }
                } else {
                    ids.mapNotNull { mediaStoreRepository.getPhotoById(it) }
                }
            }
            _searchResults.value = if (photos.isEmpty()) {
                SearchResultState.Empty(type.label)
            } else {
                SearchResultState.Results(photos, type.label)
            }
        }
    }

    private suspend fun runSearch(text: String): List<MediaPhoto> = withContext(Dispatchers.IO) {
        val ftsQuery = buildString {
            append(text.replace("\"", ""))
            append('*')
        }
        val intelligenceMatches = runCatching { photoIntelligenceDao.searchByText(ftsQuery) }.getOrDefault(emptyList())
        if (intelligenceMatches.isNotEmpty()) {
            intelligenceMatches.mapNotNull { mediaStoreRepository.getPhotoById(it.id) }
        } else {
            mediaStoreRepository.searchPhotosByDisplayName(text)
        }
    }

    private fun saveRecent(term: String) {
        viewModelScope.launch {
            val updated = listOf(term) + recentSearches.value.filterNot { it.equals(term, ignoreCase = true) }
            appPreferences.setString(
                AppPreferences.PreferencesKeys.RECENT_SEARCHES,
                updated.take(5).joinToString(separator.toString())
            )
        }
    }
}

enum class QuickFilterType(val label: String) {
    PHONE_NUMBERS("Phone Numbers"),
    EMAILS("Emails"),
    PEOPLE("People"),
    THIS_MONTH("This Month")
}
