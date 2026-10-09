package com.omnimemoria.ui.gallery

import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryQuerySettingsTest {
    private class Queries {
        val sort = MutableStateFlow(SortConfig())
        val filter = MutableStateFlow(FilterConfig())
        val version = MutableStateFlow(0)
        val favorites = MutableStateFlow<Set<Long>>(emptySet())
        val emitted = mutableListOf<GalleryQuerySettings>()
        val flow = galleryQuerySettings(sort, filter, version, favorites)
    }

    private fun observe(queries: Queries = Queries(), check: (Queries) -> Unit) = runBlocking {
        val collector = launch(Dispatchers.Unconfined) { queries.flow.collect { queries.emitted.add(it) } }
        try {
            assertEquals(1, queries.emitted.size)
            check(queries)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun repeatedFavoriteTogglesKeepTheNormalGalleryGeneration() = observe { queries ->
        repeat(20) {
            queries.favorites.value = setOf(42L)
            queries.favorites.value = emptySet()
        }
        // A badge change must never resubmit the current single-use PagingData.
        assertEquals(1, queries.emitted.size)
    }

    @Test
    fun favoriteAndNonFavoriteFiltersRefreshTheirMembership() {
        for (onlyFavorites in listOf(true, false)) {
            val queries = Queries().apply { filter.value = FilterConfig(isFavorite = onlyFavorites) }
            observe(queries) {
                it.favorites.value = setOf(42L)
                it.favorites.value = emptySet()
                assertEquals(listOf(emptySet<Long>(), setOf(42L), emptySet()), it.emitted.map { settings -> settings.favorites })
            }
        }
    }

    @Test
    fun favoritesFirstRefreshesOrderingButAnUnchangedSetDoesNot() {
        val queries = Queries().apply { sort.value = SortConfig(sortBy = SortBy.FAVORITES_FIRST) }
        observe(queries) {
            it.favorites.value = setOf(42L)
            it.favorites.value = setOf(42L)
            assertEquals(2, it.emitted.size)
            assertEquals(setOf(42L), it.emitted.last().favorites)
        }
    }

    @Test
    fun leavingFavoriteFilterRestoresStablePagesWhileMediaChangesStillRefresh() = observe { queries ->
        queries.filter.value = FilterConfig(isFavorite = true)
        queries.favorites.value = setOf(42L)
        queries.filter.value = FilterConfig()
        val generations = queries.emitted.size
        queries.favorites.value = emptySet()
        assertEquals(generations, queries.emitted.size)
        queries.version.value++
        assertEquals(generations + 1, queries.emitted.size)
        queries.sort.value = SortConfig(sortBy = SortBy.NAME)
        assertEquals(generations + 2, queries.emitted.size)
    }
}
