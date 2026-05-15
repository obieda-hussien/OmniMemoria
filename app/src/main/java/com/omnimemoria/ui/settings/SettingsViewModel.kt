package com.omnimemoria.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.domain.flags.FeatureFlagManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val featureFlagManager: FeatureFlagManager
) : ViewModel() {

    val featureStates: StateFlow<Map<FeatureFlag, Boolean>> =
        combine(
            FEATURE_FLAGS.map { flag ->
                featureFlagManager.isEnabled(flag)
            }
        ) { states ->
            FEATURE_FLAGS.zip(states.toList()).toMap()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FEATURE_FLAGS.associateWith { false }
        )

    fun toggle(flag: FeatureFlag) {
        viewModelScope.launch {
            val current = featureStates.value[flag] ?: false
            featureFlagManager.setEnabled(flag, !current)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        private val FEATURE_FLAGS = listOf(
            FeatureFlag.OCR,
            FeatureFlag.ARABIC_OCR,
            FeatureFlag.ML_LABELS,
            FeatureFlag.FACE_DETECTION,
            FeatureFlag.RAG_SEARCH,
            FeatureFlag.PIXEL_PALETTE,
            FeatureFlag.PHOTO_DNA,
            FeatureFlag.VIBE_ALBUMS,
            FeatureFlag.TEMPORAL_WAVE,
            FeatureFlag.MEMORIA_STATS,
            FeatureFlag.SMART_COMPRESSION,
            FeatureFlag.VIDEO_COMPRESSION
        )
    }
}
