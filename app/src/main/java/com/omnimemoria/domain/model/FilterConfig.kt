package com.omnimemoria.domain.model

enum class MediaType { IMAGE, VIDEO, GIF, RAW }

data class FilterConfig(
    val mediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val mimeFormats: Set<String> = emptySet(),      // e.g. "image/avif"
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val dateRange: LongRange? = null,
    val minResolutionMp: Float? = null,
    // AI filters — null = "any", true/false = filter
    val hasText: Boolean? = null,                   // Phase 6
    val hasFaces: Boolean? = null,                  // Phase 6
    val hasPhoneNumber: Boolean? = null,            // Phase 6
    val isFavorite: Boolean? = null
)
