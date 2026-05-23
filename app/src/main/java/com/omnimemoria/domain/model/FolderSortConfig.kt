package com.omnimemoria.domain.model

data class FolderSortConfig(
    val sortBy: FolderSortBy = FolderSortBy.DATE_LATEST_PHOTO,
    val sortOrder: SortOrder = SortOrder.DESCENDING
)

enum class FolderSortBy {
    DATE_LATEST_PHOTO,
    NAME,
    PHOTO_COUNT
}
