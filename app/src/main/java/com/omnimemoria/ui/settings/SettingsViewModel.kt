package com.omnimemoria.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.omnimemoria.data.preferences.AppPreferences
import com.omnimemoria.data.worker.WorkManagerScheduler
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.domain.flags.FeatureFlagManager
import com.omnimemoria.domain.flags.OfflineModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val featureFlagManager: FeatureFlagManager,
    private val appPreferences: AppPreferences,
    @ApplicationContext context: Context
) : ViewModel() {
    val featureStates = featureFlagManager.featureStates.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), FeatureFlag.entries.associateWith { false }
    )
    val modelRequests = appPreferences.snapshots.map { preferences ->
        OfflineModel.entries.associate { it.id to (preferences[requestKey(it)] ?: false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val indexingState = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(WorkManagerScheduler.UNIQUE_PERIODIC_WORK_NAME)
        .map { jobs -> when (jobs.firstOrNull { !it.state.isFinished }?.state) {
            androidx.work.WorkInfo.State.ENQUEUED -> "Waiting for idle device and sufficient battery"
            androidx.work.WorkInfo.State.RUNNING -> "Checking feature preferences"
            androidx.work.WorkInfo.State.BLOCKED -> "Waiting for prerequisites"
            else -> "Off"
        } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Loading")

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        viewModelScope.launch { safely { featureFlagManager.setEnabled(flag, enabled) } }
    }

    fun requestModel(id: String, requested: Boolean = true) {
        val model = OfflineModel.fromId(id) ?: return
        viewModelScope.launch { safely { appPreferences.setBoolean(requestKey(model), requested) } }
    }

    fun dismissError() { _error.value = null }

    private suspend fun safely(action: suspend () -> Unit) {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _error.value = "Unable to save settings. Please try again." }
    }

    private fun requestKey(model: OfflineModel) = when (model) {
        OfflineModel.TESSERACT_ARABIC -> AppPreferences.PreferencesKeys.REQUEST_TESSERACT_DOWNLOAD
        OfflineModel.EMBEDDING_GEMMA -> AppPreferences.PreferencesKeys.REQUEST_EMBEDDER_DOWNLOAD
    }
}
