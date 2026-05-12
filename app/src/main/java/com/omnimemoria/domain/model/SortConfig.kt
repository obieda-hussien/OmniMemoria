package com.omnimemoria.domain.model

data class SortConfig(
    val sortBy: SortBy = SortBy.DATE_TAKEN,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val groupBy: GroupBy? = null
)

enum class SortBy { DATE_TAKEN, DATE_MODIFIED, SIZE, NAME, TYPE, RESOLUTION, DURATION, FAVORITES_FIRST }

enum class SortOrder { ASCENDING, DESCENDING }

enum class GroupBy { DAY, MONTH, YEAR, LOCATION }
