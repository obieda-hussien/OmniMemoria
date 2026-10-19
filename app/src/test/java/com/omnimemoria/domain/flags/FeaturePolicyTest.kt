package com.omnimemoria.domain.flags

import org.junit.Assert.*
import org.junit.Test

class FeaturePolicyTest {
    @Test fun allEighteenFeaturesAreAddressable() { assertEquals(18, FeatureFlag.entries.size) }

    @Test fun ragEnablesItsTransitivePrerequisites() {
        val state = FeaturePolicy.update(emptyMap(), FeatureFlag.RAG_SEARCH, true)
        assertTrue(state[FeatureFlag.OCR] == true)
        assertTrue(state[FeatureFlag.EMBEDDINGS] == true)
        assertTrue(FeaturePolicy.enabled(state, FeatureFlag.RAG_SEARCH))
    }

    @Test fun disablingOcrDisablesDependentPreferencesAndKeepsUnrelatedFeatures() {
        var state = FeaturePolicy.update(emptyMap(), FeatureFlag.RAG_SEARCH, true)
        state = FeaturePolicy.update(state, FeatureFlag.ARABIC_OCR, true)
        state = FeaturePolicy.update(state, FeatureFlag.PHOTO_DNA, true)
        state = FeaturePolicy.update(state, FeatureFlag.OCR, false)
        for (flag in listOf(FeatureFlag.OCR, FeatureFlag.ARABIC_OCR, FeatureFlag.EMBEDDINGS, FeatureFlag.RAG_SEARCH)) {
            assertEquals(false, state[flag])
        }
        assertEquals(true, state[FeatureFlag.PHOTO_DNA])
        state = FeaturePolicy.update(state, FeatureFlag.OCR, true)
        assertEquals(false, state[FeatureFlag.RAG_SEARCH])
    }

    @Test fun inconsistentLegacyFlagsCannotStartDependentFeatures() {
        assertFalse(FeaturePolicy.enabled(mapOf(FeatureFlag.RAG_SEARCH to true), FeatureFlag.RAG_SEARCH))
        assertFalse(FeaturePolicy.enabled(mapOf(FeatureFlag.ARABIC_OCR to true), FeatureFlag.ARABIC_OCR))
    }

    @Test fun everySingleToggleAndDisableKeepsDependencyInvariant() {
        for (flag in FeatureFlag.entries) {
            val enabled = FeaturePolicy.update(emptyMap(), flag, true)
            assertTrue(FeaturePolicy.enabled(enabled, flag))
            for (disabled in FeatureFlag.entries) {
                val state = FeaturePolicy.update(enabled, disabled, false)
                for (candidate in FeatureFlag.entries) {
                    assertEquals(state[candidate] == true, FeaturePolicy.enabled(state, candidate))
                }
            }
        }
    }

    @Test fun everyExtractionConsumerParticipatesInScheduling() {
        assertTrue(FeaturePolicy.indexingFlags.containsAll(setOf(FeatureFlag.PIXEL_PALETTE, FeatureFlag.PHOTO_DNA, FeatureFlag.VIBE_ALBUMS)))
        assertFalse(FeatureFlag.VAULT in FeaturePolicy.indexingFlags)
    }

    @Test fun modelIdsRemainCompatibleWithOldRequests() {
        assertEquals(OfflineModel.TESSERACT_ARABIC, OfflineModel.fromId("tesseract_ara"))
        assertEquals(OfflineModel.EMBEDDING_GEMMA, OfflineModel.fromId("mediapipe_embedder"))
        assertNull(OfflineModel.fromId("unknown"))
    }
}
