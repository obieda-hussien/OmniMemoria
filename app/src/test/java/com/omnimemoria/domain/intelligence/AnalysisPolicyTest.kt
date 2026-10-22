package com.omnimemoria.domain.intelligence

import com.omnimemoria.domain.flags.FeatureFlag
import org.junit.Assert.*
import org.junit.Test

class AnalysisPolicyTest {
    @Test fun disabledFeaturesDoNotExtractAnyData() { assertEquals(0, AnalysisPolicy.required(emptyMap())) }
    @Test fun smartFiltersRequestBothTextAndFaceMetadata() {
        val required = AnalysisPolicy.required(mapOf(FeatureFlag.SMART_FILTERS to true))
        assertEquals(AnalysisPolicy.TEXT or AnalysisPolicy.CODES or AnalysisPolicy.FACES, required)
    }
    @Test fun eachImplementedFeatureSelectsItsOwnStage() {
        val expected = mapOf(
            FeatureFlag.OCR to (AnalysisPolicy.TEXT or AnalysisPolicy.CODES),
            FeatureFlag.ARABIC_OCR to (AnalysisPolicy.TEXT or AnalysisPolicy.ARABIC or AnalysisPolicy.CODES),
            FeatureFlag.ML_LABELS to AnalysisPolicy.LABELS,
            FeatureFlag.FACE_DETECTION to AnalysisPolicy.FACES,
            FeatureFlag.PIXEL_PALETTE to AnalysisPolicy.COLORS,
            FeatureFlag.PHOTO_DNA to AnalysisPolicy.HASH
        )
        for ((flag, mask) in expected) assertEquals(flag.name, mask, AnalysisPolicy.required(mapOf(flag to true)))
    }
    @Test fun unchangedCompletedImageIsSkippedButNewFeaturesAndEditsAreNot() {
        assertFalse(AnalysisPolicy.needsAnalysis(3, 3, AnalysisPolicy.VERSION, 10, 10, 100, 100))
        assertTrue(AnalysisPolicy.needsAnalysis(1, 3, AnalysisPolicy.VERSION, 10, 10, 100, 100))
        assertTrue(AnalysisPolicy.needsAnalysis(3, 3, AnalysisPolicy.VERSION, 10, 11, 100, 100))
        assertTrue(AnalysisPolicy.needsAnalysis(3, 3, AnalysisPolicy.VERSION, 10, 10, 100, 101))
        assertTrue(AnalysisPolicy.needsAnalysis(3, 3, 0, 10, 10, 100, 100))
        assertTrue(AnalysisPolicy.needsAnalysis(3, 3, AnalysisPolicy.VERSION, 10, 10, 100, 100, AnalysisPolicy.version(true)))
    }
}
