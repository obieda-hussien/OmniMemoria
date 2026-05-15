package com.omnimemoria.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val APP_PREFERENCES_NAME = "omnimemoria_settings"
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = APP_PREFERENCES_NAME)

class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    val data: Flow<Preferences> = context.appDataStore.data
    private val dataStore = context.appDataStore

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> {
        return dataStore.data.map { preferences -> preferences[key] ?: default }
    }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { preferences -> preferences[key] = value }
    }

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> {
        return dataStore.data.map { preferences -> preferences[key] ?: default }
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { preferences -> preferences[key] = value }
    }

    object PreferencesKeys {
        val ENABLE_OCR = booleanPreferencesKey("enable_ocr")
        val ENABLE_ARABIC_OCR = booleanPreferencesKey("enable_arabic_ocr")
        val ENABLE_ML_LABELS = booleanPreferencesKey("enable_ml_labels")
        val ENABLE_FACE_DETECTION = booleanPreferencesKey("enable_face_detection")
        val ENABLE_EMBEDDINGS = booleanPreferencesKey("enable_embeddings")
        val ENABLE_PIXEL_PALETTE = booleanPreferencesKey("enable_pixel_palette")
        val ENABLE_PHOTO_DNA = booleanPreferencesKey("enable_photo_dna")
        val ENABLE_VIBE_ALBUMS = booleanPreferencesKey("enable_vibe_albums")
        val ENABLE_TEMPORAL_WAVE = booleanPreferencesKey("enable_temporal_wave")
        val ENABLE_MEMORIA_STATS = booleanPreferencesKey("enable_memoria_stats")
        val ENABLE_SMART_COMPRESSION = booleanPreferencesKey("enable_smart_compression")
        val ENABLE_VIDEO_COMPRESSION = booleanPreferencesKey("enable_video_compression")
        val ENABLE_VAULT = booleanPreferencesKey("enable_vault")
        val ENABLE_SILENT_STORY = booleanPreferencesKey("enable_silent_story")
        val ENABLE_MEMORY_MAP = booleanPreferencesKey("enable_memory_map")
        val ENABLE_RAG_SEARCH = booleanPreferencesKey("enable_rag_search")
        val ENABLE_SMART_FILTERS = booleanPreferencesKey("enable_smart_filters")
        val ENABLE_ULTRA_HDR = booleanPreferencesKey("enable_ultra_hdr")

        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val SORT_PRESETS_JSON = stringPreferencesKey("sort_presets_json")
    }
}
