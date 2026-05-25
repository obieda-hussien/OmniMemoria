package com.omnimemoria.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val APP_PREFERENCES_NAME = "omnimemoria_settings"
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = APP_PREFERENCES_NAME)

class AppPreferences constructor(
    context: Context
) {
    private val dataStore = context.appDataStore

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> {
        return dataStore.data.map { preferences -> preferences[key] ?: default }
    }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.updateData { preferences ->
            DataStoreCompat.withBoolean(preferences, key, value)
        }
    }

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> {
        return dataStore.data.map { preferences -> preferences[key] ?: default }
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        dataStore.updateData { preferences ->
            DataStoreCompat.withString(preferences, key, value)
        }
    }

    object PreferencesKeys {
        val ENABLE_OCR = DataStoreCompat.booleanKey("enable_ocr")
        val ENABLE_ARABIC_OCR = DataStoreCompat.booleanKey("enable_arabic_ocr")
        val ENABLE_ML_LABELS = DataStoreCompat.booleanKey("enable_ml_labels")
        val ENABLE_FACE_DETECTION = DataStoreCompat.booleanKey("enable_face_detection")
        val ENABLE_EMBEDDINGS = DataStoreCompat.booleanKey("enable_embeddings")
        val ENABLE_PIXEL_PALETTE = DataStoreCompat.booleanKey("enable_pixel_palette")
        val ENABLE_PHOTO_DNA = DataStoreCompat.booleanKey("enable_photo_dna")
        val ENABLE_VIBE_ALBUMS = DataStoreCompat.booleanKey("enable_vibe_albums")
        val ENABLE_TEMPORAL_WAVE = DataStoreCompat.booleanKey("enable_temporal_wave")
        val ENABLE_MEMORIA_STATS = DataStoreCompat.booleanKey("enable_memoria_stats")
        val ENABLE_SMART_COMPRESSION = DataStoreCompat.booleanKey("enable_smart_compression")
        val ENABLE_VIDEO_COMPRESSION = DataStoreCompat.booleanKey("enable_video_compression")
        val ENABLE_VAULT = DataStoreCompat.booleanKey("enable_vault")
        val ENABLE_SILENT_STORY = DataStoreCompat.booleanKey("enable_silent_story")
        val ENABLE_MEMORY_MAP = DataStoreCompat.booleanKey("enable_memory_map")
        val ENABLE_RAG_SEARCH = DataStoreCompat.booleanKey("enable_rag_search")
        val ENABLE_SMART_FILTERS = DataStoreCompat.booleanKey("enable_smart_filters")
        val ENABLE_ULTRA_HDR = DataStoreCompat.booleanKey("enable_ultra_hdr")
        val MODEL_TESSERACT_ARA_DOWNLOADED = DataStoreCompat.booleanKey("model_tesseract_ara_downloaded")
        val MODEL_MEDIAPIPE_EMBEDDER_DOWNLOADED =
            DataStoreCompat.booleanKey("model_mediapipe_embedder_downloaded")
        val ARE_AI_MODELS_DOWNLOADED = DataStoreCompat.booleanKey("are_ai_models_downloaded")

        val VAULT_PIN_HASH = DataStoreCompat.stringKey("vault_pin_hash")
        val SORT_PRESETS_JSON = DataStoreCompat.stringKey("sort_presets_json")
        val RECENT_SEARCHES = DataStoreCompat.stringKey("recent_searches")
    }
}
