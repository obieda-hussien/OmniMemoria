package com.omnimemoria.domain.flags

import androidx.datastore.preferences.core.Preferences
import com.omnimemoria.data.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

enum class AiModelState {
    NONE,
    ARABIC_OCR_ONLY,
    EMBEDDER_ONLY,
    FULL
}

@Singleton
class FeatureFlagManager @Inject constructor(
    private val appPreferences: AppPreferences,
    private val models: com.omnimemoria.data.intelligence.ArabicModelStore,
    private val paddleModels: com.omnimemoria.data.intelligence.PaddleOcrModelStore
) {
    val featureStates: Flow<Map<FeatureFlag, Boolean>> = appPreferences.snapshots.map { preferences ->
        val requested = FeatureFlag.entries.associateWith { preferences[flagKey(it)] ?: false }
        FeatureFlag.entries.associateWith { FeaturePolicy.enabled(requested, it) }
    }.distinctUntilChanged()

    fun isEnabled(flag: FeatureFlag): Flow<Boolean> = featureStates.map { it[flag] == true }.distinctUntilChanged()

    suspend fun setEnabled(flag: FeatureFlag, value: Boolean) {
        appPreferences.updateBooleans { preferences ->
            val current = FeatureFlag.entries.associateWith { preferences[flagKey(it)] ?: false }
            FeaturePolicy.update(current, flag, value).mapKeys { flagKey(it.key) }
        }
    }

    // Read actual verified files; legacy preferences cannot establish installation.
    fun areAiModelsDownloaded(): Flow<AiModelState> = appPreferences.snapshots.map {
        if (paddleModels.isInstalled() || models.isInstalled()) AiModelState.ARABIC_OCR_ONLY else AiModelState.NONE
    }.flowOn(kotlinx.coroutines.Dispatchers.IO).distinctUntilChanged()

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
