package com.omnimemoria.ui.gallery

import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Navigation carries the actual source collection, never an implicit library fallback. */
data class PreviewCollection(
    val seed: MediaPhoto,
    val sort: SortConfig,
    val filter: FilterConfig,
    val bucketId: String? = null,
    val snapshot: List<MediaPhoto>? = null
)

@Singleton
class GalleryStateHolder @Inject constructor() {
    private var folderFilter: FilterConfig? = null
    @Synchronized fun prepareFolder(filter: FilterConfig) { folderFilter = filter }
    @Synchronized fun consumeFolderFilter(): FilterConfig = (folderFilter ?: FilterConfig()).also { folderFilter = null }
    private var pending: PreviewCollection? = null
    @Synchronized fun prepare(collection: PreviewCollection) { pending = collection }
    @Synchronized fun consumeCollection(): PreviewCollection? = pending.also { pending = null }
    fun cachePendingPhoto(photo: MediaPhoto) {
        prepare(PreviewCollection(photo, activeSortConfig.value, activeFilter.value))
    }
    val activeSortConfig = MutableStateFlow(SortConfig())
    val activeFilter = MutableStateFlow(FilterConfig())
}
