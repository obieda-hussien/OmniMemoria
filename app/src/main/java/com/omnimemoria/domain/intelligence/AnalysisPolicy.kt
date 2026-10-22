package com.omnimemoria.domain.intelligence

import com.omnimemoria.domain.flags.FeatureFlag

object AnalysisPolicy {
    // Version bump forces previously indexed images through the stronger mixed-script OCR pipeline.
    const val VERSION = 6
    fun version(highAccuracyArabic: Boolean) = if (highAccuracyArabic) 7 else VERSION
    const val TEXT = 1
    const val ARABIC = 2
    const val LABELS = 4
    const val FACES = 8
    const val COLORS = 16
    const val HASH = 32
    const val CODES = 64
    fun required(flags: Map<FeatureFlag, Boolean>): Int {
        fun on(vararg keys: FeatureFlag) = keys.any { flags[it] == true }
        var mask = 0
        if (on(FeatureFlag.OCR, FeatureFlag.ARABIC_OCR, FeatureFlag.SMART_FILTERS)) mask = mask or TEXT or CODES
        if (on(FeatureFlag.ARABIC_OCR)) mask = mask or ARABIC
        if (on(FeatureFlag.ML_LABELS, FeatureFlag.VIBE_ALBUMS)) mask = mask or LABELS
        if (on(FeatureFlag.FACE_DETECTION, FeatureFlag.SMART_FILTERS)) mask = mask or FACES
        if (on(FeatureFlag.PIXEL_PALETTE)) mask = mask or COLORS
        if (on(FeatureFlag.PHOTO_DNA)) mask = mask or HASH
        return mask
    }
    fun needsAnalysis(completed: Int, requested: Int, version: Int, oldModified: Long, modified: Long, oldSize: Long, size: Long,
        expectedVersion: Int = VERSION) =
        version != expectedVersion || oldModified != modified || oldSize != size || (completed and requested) != requested
}
