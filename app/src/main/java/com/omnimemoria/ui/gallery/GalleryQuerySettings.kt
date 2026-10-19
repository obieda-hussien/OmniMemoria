package com.omnimemoria.ui.gallery

import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.MediaQueryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class GalleryQuerySettings(
    val sort: SortConfig,
    val filter: FilterConfig,
    val version: Int,
    val favorites: Set<Long>
)

// Favorites only invalidate pages when they affect membership or ordering.
// Otherwise the grid keeps its current generation, loaded pages and position.
internal fun galleryQuerySettings(
    sort: Flow<SortConfig>,
    filter: Flow<FilterConfig>,
    version: Flow<Int>,
    favorites: Flow<Set<Long>>
): Flow<GalleryQuerySettings> = combine(sort, filter, version, favorites) { currentSort, currentFilter, currentVersion, ids ->
    GalleryQuerySettings(
        currentSort,
        currentFilter,
        currentVersion,
        if (currentFilter.isFavorite != null || currentSort.sortBy == SortBy.FAVORITES_FIRST) ids else emptySet()
    )
}.distinctUntilChanged()

internal fun galleryQuerySettings(
    query: Flow<MediaQueryConfig>,
    version: Flow<Int>,
    favorites: Flow<Set<Long>>
): Flow<GalleryQuerySettings> = combine(query, version, favorites) { current, generation, ids ->
    GalleryQuerySettings(current.sort, current.filter, generation,
        if (current.filter.isFavorite != null || current.sort.sortBy == SortBy.FAVORITES_FIRST) ids else emptySet())
}.distinctUntilChanged()
