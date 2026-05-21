package com.omnimemoria.domain.model

data class FilterConfig(
    // RAW is intentionally excluded by default per the requested baseline behavior.
    val mediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.GIF),
    val mimeTypes: Set<String> = emptySet(),
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val dateRange: Pair<Long, Long>? = null
)

enum class MediaType {
    IMAGE,
    VIDEO,
    GIF,
    RAW
}
