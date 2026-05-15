package com.omnimemoria.domain.flags

import androidx.datastore.preferences.core.Preferences
import com.omnimemoria.data.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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

enum class AiModelState {
    NONE,
    TESSERACT_ONLY,
    FULL
}

@Singleton
class FeatureFlagManager @Inject constructor(
    private val appPreferences: AppPreferences
) {
    fun isEnabled(flag: FeatureFlag): Flow<Boolean> {
        return appPreferences.getBoolean(flagKey(flag))
    }

    suspend fun setEnabled(flag: FeatureFlag, value: Boolean) {
        appPreferences.setBoolean(flagKey(flag), value)
    }

    fun areAiModelsDownloaded(): Flow<AiModelState> {
        return combine(
            appPreferences.getBoolean(AppPreferences.PreferencesKeys.MODEL_TESSERACT_ARA_DOWNLOADED),
            appPreferences.getBoolean(AppPreferences.PreferencesKeys.MODEL_MEDIAPIPE_EMBEDDER_DOWNLOADED)
        ) { tesseractDownloaded, embedderDownloaded ->
            when {
                !tesseractDownloaded && !embedderDownloaded -> AiModelState.NONE
                tesseractDownloaded && embedderDownloaded -> AiModelState.FULL
                else -> AiModelState.TESSERACT_ONLY
            }
        }
    }

    private fun flagKey(flag: FeatureFlag): Preferences.Key<Boolean> {
        return when (flag) {
            FeatureFlag.OCR -> AppPreferences.PreferencesKeys.ENABLE_OCR
            FeatureFlag.ARABIC_OCR -> AppPreferences.PreferencesKeys.ENABLE_ARABIC_OCR
            FeatureFlag.ML_LABELS -> AppPreferences.PreferencesKeys.ENABLE_ML_LABELS
            FeatureFlag.FACE_DETECTION -> AppPreferences.PreferencesKeys.ENABLE_FACE_DETECTION
            FeatureFlag.EMBEDDINGS -> AppPreferences.PreferencesKeys.ENABLE_EMBEDDINGS
            FeatureFlag.PIXEL_PALETTE -> AppPreferences.PreferencesKeys.ENABLE_PIXEL_PALETTE
            FeatureFlag.PHOTO_DNA -> AppPreferences.PreferencesKeys.ENABLE_PHOTO_DNA
            FeatureFlag.VIBE_ALBUMS -> AppPreferences.PreferencesKeys.ENABLE_VIBE_ALBUMS
            FeatureFlag.TEMPORAL_WAVE -> AppPreferences.PreferencesKeys.ENABLE_TEMPORAL_WAVE
            FeatureFlag.MEMORIA_STATS -> AppPreferences.PreferencesKeys.ENABLE_MEMORIA_STATS
            FeatureFlag.SMART_COMPRESSION -> AppPreferences.PreferencesKeys.ENABLE_SMART_COMPRESSION
            FeatureFlag.VIDEO_COMPRESSION -> AppPreferences.PreferencesKeys.ENABLE_VIDEO_COMPRESSION
            FeatureFlag.VAULT -> AppPreferences.PreferencesKeys.ENABLE_VAULT
            FeatureFlag.SILENT_STORY -> AppPreferences.PreferencesKeys.ENABLE_SILENT_STORY
            FeatureFlag.MEMORY_MAP -> AppPreferences.PreferencesKeys.ENABLE_MEMORY_MAP
            FeatureFlag.RAG_SEARCH -> AppPreferences.PreferencesKeys.ENABLE_RAG_SEARCH
            FeatureFlag.SMART_FILTERS -> AppPreferences.PreferencesKeys.ENABLE_SMART_FILTERS
            FeatureFlag.ULTRA_HDR -> AppPreferences.PreferencesKeys.ENABLE_ULTRA_HDR
        }
    }
}
