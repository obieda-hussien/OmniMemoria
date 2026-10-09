package com.omnimemoria.domain.model

/** Shared SQL policy: filtering and global ordering happen before pagination. */
object MediaQuerySql {
    const val effectiveDate = "CASE WHEN datetaken > 0 THEN datetaken WHEN date_modified > 0 THEN date_modified * 1000 ELSE date_added * 1000 END"
    private val rawFormats = setOf("image/x-adobe-dng", "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef", "image/x-sony-arw", "image/x-panasonic-rw2", "image/x-fuji-raf", "image/x-olympus-orf", "image/x-pentax-pef")

    fun selection(filter: FilterConfig): Pair<String, Array<String>> {
        val clauses = mutableListOf("_size > 0")
        val args = mutableListOf<String>()
        val types = filter.mediaTypes.map { type ->
            when (type) {
                MediaType.IMAGE -> "(media_type = 1 OR mime_type LIKE 'image/%')"
                MediaType.VIDEO -> "(media_type = 3 OR mime_type LIKE 'video/%')"
                MediaType.GIF -> "mime_type = 'image/gif'"
                MediaType.RAW -> "mime_type IN (${rawFormats.joinToString { "'${it}'" }})"
            }
        }
        clauses += if (types.isEmpty()) "0" else types.joinToString(" OR ", "(", ")")
        if (filter.mimeFormats.isNotEmpty()) {
            clauses += "mime_type IN (${filter.mimeFormats.joinToString { "?" }})"
            args += filter.mimeFormats.sorted()
        }
        filter.minSizeBytes?.let { clauses += "_size >= ?"; args += it.coerceAtLeast(0).toString() }
        filter.maxSizeBytes?.let { clauses += "_size <= ?"; args += it.coerceAtLeast(0).toString() }
        filter.dateRange?.let {
            clauses += "($effectiveDate) BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)"
            args += it.first.toString(); args += it.last.toString()
        }
        filter.minResolutionMp?.let {
            clauses += "(CAST(width AS REAL) * height) >= CAST(? AS REAL)"
            args += (it.coerceAtLeast(0f).toDouble() * 1_000_000).toString()
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    fun idsClause(ids: Set<Long>, include: Boolean): String = when {
        ids.isEmpty() -> if (include) "0" else "1"
        else -> "_id ${if (include) "IN" else "NOT IN"} (${ids.sorted().joinToString()})"
    }

    fun sort(sort: SortConfig, favoriteIds: Set<Long> = emptySet()): String {
        val dir = if (sort.sortOrder == SortOrder.ASCENDING) "ASC" else "DESC"
        val primary = when (sort.sortBy) {
            SortBy.DATE_TAKEN -> "($effectiveDate)"
            SortBy.DATE_MODIFIED -> "date_modified"
            SortBy.SIZE -> "_size"
            SortBy.NAME -> "_display_name COLLATE NOCASE"
            SortBy.TYPE -> "mime_type COLLATE NOCASE"
            SortBy.RESOLUTION -> "(CAST(width AS REAL) * height)"
            SortBy.DURATION -> "duration"
            SortBy.FAVORITES_FIRST -> "($effectiveDate)"
        }
        val favorites = if (sort.sortBy == SortBy.FAVORITES_FIRST && favoriteIds.isNotEmpty())
            "CASE WHEN ${idsClause(favoriteIds, true)} THEN 0 ELSE 1 END ASC, " else ""
        return "$favorites$primary $dir, _id $dir"
    }
}
