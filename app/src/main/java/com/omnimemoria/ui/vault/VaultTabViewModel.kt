package com.omnimemoria.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.domain.flags.FeatureFlagManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VaultTabViewModel @Inject constructor(
    featureFlagManager: FeatureFlagManager
) : ViewModel() {
    val vaultEnabled: StateFlow<Boolean> = featureFlagManager.isEnabled(FeatureFlag.VAULT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
