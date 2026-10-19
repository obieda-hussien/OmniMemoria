package com.omnimemoria.domain.intelligence

import com.omnimemoria.domain.flags.FeatureFlag

object AnalysisPolicy {
    const val VERSION = 1
    const val TEXT = 1
    const val ARABIC = 2
    const val LABELS = 4
    const val FACES = 8
    const val COLORS = 16
    const val HASH = 32
    fun required(flags: Map<FeatureFlag, Boolean>): Int {
        fun on(vararg keys: FeatureFlag) = keys.any { flags[it] == true }
        var mask = 0
        if (on(FeatureFlag.OCR, FeatureFlag.SMART_FILTERS)) mask = mask or TEXT
        if (on(FeatureFlag.ARABIC_OCR)) mask = mask or ARABIC
        if (on(FeatureFlag.ML_LABELS, FeatureFlag.VIBE_ALBUMS)) mask = mask or LABELS
        if (on(FeatureFlag.FACE_DETECTION, FeatureFlag.SMART_FILTERS)) mask = mask or FACES
        if (on(FeatureFlag.PIXEL_PALETTE)) mask = mask or COLORS
        if (on(FeatureFlag.PHOTO_DNA)) mask = mask or HASH
        return mask
    }
    fun needsAnalysis(completed: Int, requested: Int, version: Int, oldModified: Long, modified: Long, oldSize: Long, size: Long) =
        version != VERSION || oldModified != modified || oldSize != size || (completed and requested) != requested
}
