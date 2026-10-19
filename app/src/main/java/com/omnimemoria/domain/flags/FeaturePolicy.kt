package com.omnimemoria.domain.flags

enum class FeatureFlag {
    OCR,
    ARABIC_OCR,
    ML_LABELS,
    FACE_DETECTION,
    EMBEDDINGS,
    PIXEL_PALETTE,
    PHOTO_DNA,
    VIBE_ALBUMS,
    TEMPORAL_WAVE,
    MEMORIA_STATS,
    SMART_COMPRESSION,
    VIDEO_COMPRESSION,
    VAULT,
    SILENT_STORY,
    MEMORY_MAP,
    RAG_SEARCH,
    SMART_FILTERS,
    ULTRA_HDR
}

/** Dependency rules shared by settings, persisted preferences and background workers. */
object FeaturePolicy {
    val dependencies: Map<FeatureFlag, Set<FeatureFlag>> = mapOf(
        FeatureFlag.ARABIC_OCR to setOf(FeatureFlag.OCR),
        FeatureFlag.EMBEDDINGS to setOf(FeatureFlag.OCR),
        FeatureFlag.RAG_SEARCH to setOf(FeatureFlag.EMBEDDINGS),
        FeatureFlag.VIBE_ALBUMS to setOf(FeatureFlag.ML_LABELS)
    )

    val indexingFlags = setOf(
        FeatureFlag.OCR, FeatureFlag.ARABIC_OCR, FeatureFlag.ML_LABELS,
        FeatureFlag.FACE_DETECTION, FeatureFlag.EMBEDDINGS, FeatureFlag.PIXEL_PALETTE,
        FeatureFlag.PHOTO_DNA, FeatureFlag.VIBE_ALBUMS, FeatureFlag.RAG_SEARCH,
        FeatureFlag.SMART_FILTERS, FeatureFlag.MEMORY_MAP
    )

    fun enabled(requested: Map<FeatureFlag, Boolean>, flag: FeatureFlag): Boolean =
        requested[flag] == true && dependencies[flag].orEmpty().all { enabled(requested, it) }

    /** Enabling also enables prerequisites; disabling turns off dependent requests atomically. */
    fun update(current: Map<FeatureFlag, Boolean>, flag: FeatureFlag, value: Boolean): Map<FeatureFlag, Boolean> {
        val result = FeatureFlag.entries.associateWith { current[it] == true }.toMutableMap()
        fun enable(next: FeatureFlag) {
            dependencies[next].orEmpty().forEach(::enable)
            result[next] = true
        }
        if (value) enable(flag) else {
            result[flag] = false
            do {
                val invalid = result.filter { (key, on) -> on && dependencies[key].orEmpty().any { result[it] != true } }.keys
                invalid.forEach { result[it] = false }
            } while (invalid.isNotEmpty())
        }
        return result
    }
}
