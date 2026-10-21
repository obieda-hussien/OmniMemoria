package com.omnimemoria.domain.intelligence

import com.omnimemoria.domain.flags.FeatureFlag
import org.junit.Assert.*
import org.junit.Test

class AnalysisPolicyTest {
    @Test fun disabledFeaturesDoNotExtractAnyData() { assertEquals(0, AnalysisPolicy.required(emptyMap())) }
    @Test fun smartFiltersRequestBothTextAndFaceMetadata() {
        val required = AnalysisPolicy.required(mapOf(FeatureFlag.SMART_FILTERS to true))
        assertEquals(AnalysisPolicy.TEXT or AnalysisPolicy.FACES, required)
    }
    @Test fun eachImplementedFeatureSelectsItsOwnStage() {
        for ((flag, bit) in mapOf(FeatureFlag.OCR to 1, FeatureFlag.ARABIC_OCR to 2, FeatureFlag.ML_LABELS to 4,
            FeatureFlag.FACE_DETECTION to 8, FeatureFlag.PIXEL_PALETTE to 16, FeatureFlag.PHOTO_DNA to 32)) {
            assertEquals(bit, AnalysisPolicy.required(mapOf(flag to true)))
        }
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
