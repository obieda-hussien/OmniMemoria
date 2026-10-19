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
    private val models: com.omnimemoria.data.intelligence.ArabicModelStore,
    private val scheduler: WorkManagerScheduler,
    @ApplicationContext context: Context
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    val featureStates = featureFlagManager.featureStates.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), FeatureFlag.entries.associateWith { false }
    )
    val modelRequests = appPreferences.snapshots.map { preferences ->
        OfflineModel.entries.associate { it.id to (preferences[requestKey(it)] ?: false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val modelStatuses = combine(appPreferences.snapshots,
        workManager.getWorkInfosForUniqueWorkFlow(com.omnimemoria.data.worker.ModelDownloadWorker.UNIQUE_ARABIC_DOWNLOAD)) { _, jobs ->
        val job = jobs.lastOrNull()
        val status = when {
            models.isInstalled() -> "Installed · verified"
            job?.state == androidx.work.WorkInfo.State.RUNNING -> "Downloading ${job.progress.getInt("percent", 0)}%"
            job?.state == androidx.work.WorkInfo.State.ENQUEUED -> "Waiting for network / battery"
            job?.state == androidx.work.WorkInfo.State.FAILED -> "Download failed · retry"
            else -> "Not installed"
        }
        mapOf(OfflineModel.TESSERACT_ARABIC.id to status, OfflineModel.EMBEDDING_GEMMA.id to "Semantic indexing update pending")
    }.flowOn(kotlinx.coroutines.Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val indexingState = workManager.getWorkInfosForUniqueWorkFlow(WorkManagerScheduler.UNIQUE_SCAN_WORK_NAME)
        .map { jobs ->
            val active = jobs.lastOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                ?: jobs.lastOrNull { !it.state.isFinished } ?: jobs.lastOrNull()
            when (active?.state) {
                androidx.work.WorkInfo.State.RUNNING -> "Analyzing · ${active.progress.getInt("processed", 0)} completed · ${active.progress.getInt("failed", 0)} need attention"
                androidx.work.WorkInfo.State.ENQUEUED -> "Waiting for idle device and sufficient battery"
                androidx.work.WorkInfo.State.SUCCEEDED -> "Scan complete · ${active.outputData.getInt("processed", 0)} completed in last batch"
                androidx.work.WorkInfo.State.FAILED -> "Scan needs storage access or retry"
                else -> "Off"
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Loading")

    fun scanNow() { scheduler.scheduleBackgroundIndex() }

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        viewModelScope.launch { safely { featureFlagManager.setEnabled(flag, enabled) } }
    }

    fun requestModel(id: String, requested: Boolean = true) {
        val model = OfflineModel.fromId(id) ?: return
        viewModelScope.launch { safely {
            appPreferences.setBoolean(requestKey(model), requested)
            if (model == OfflineModel.TESSERACT_ARABIC) {
                if (!requested) workManager.cancelUniqueWork(com.omnimemoria.data.worker.ModelDownloadWorker.UNIQUE_ARABIC_DOWNLOAD)
                else {
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.omnimemoria.data.worker.ModelDownloadWorker>()
                        .setInputData(androidx.work.workDataOf(com.omnimemoria.data.worker.ModelDownloadWorker.MODEL_NAME to model.id))
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                        .build()
                    workManager.enqueueUniqueWork(com.omnimemoria.data.worker.ModelDownloadWorker.UNIQUE_ARABIC_DOWNLOAD, androidx.work.ExistingWorkPolicy.KEEP, request)
                }
            }
        } }
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
