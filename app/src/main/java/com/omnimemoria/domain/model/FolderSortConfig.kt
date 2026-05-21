package com.omnimemoria.domain.model

data class FolderSortConfig(
    val sortBy: FolderSortBy = FolderSortBy.LATEST_PHOTO_DATE
)

enum class FolderSortBy {
    LATEST_PHOTO_DATE,
    NAME,
    COUNT
}
