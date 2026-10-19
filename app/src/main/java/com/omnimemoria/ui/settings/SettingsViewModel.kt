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
import androidx.work.await
import com.omnimemoria.domain.intelligence.AnalysisPolicy

enum class ModelPhase { NOT_INSTALLED, QUEUED, DOWNLOADING, RETRYING, INSTALLED, FAILED, PLANNED }
data class ModelStatus(val phase: ModelPhase, val message: String, val percent: Int = 0)

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
        val job = jobs.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
            ?: jobs.firstOrNull { !it.state.isFinished } ?: jobs.lastOrNull()
        val status = when {
            models.isInstalled() -> ModelStatus(ModelPhase.INSTALLED, "Installed · file verified")
            job?.state == androidx.work.WorkInfo.State.RUNNING -> ModelStatus(ModelPhase.DOWNLOADING, "Downloading Arabic OCR", job.progress.getInt("percent", 0))
            job?.state == androidx.work.WorkInfo.State.ENQUEUED && job.runAttemptCount > 0 -> ModelStatus(ModelPhase.RETRYING, "Download interrupted · retry scheduled. Tap Retry now to start again.")
            job?.state == androidx.work.WorkInfo.State.ENQUEUED -> ModelStatus(ModelPhase.QUEUED, "Queued · internet access required")
            job?.state == androidx.work.WorkInfo.State.FAILED -> ModelStatus(ModelPhase.FAILED, job.outputData.getString("error") ?: "Download failed. Please retry.")
            else -> ModelStatus(ModelPhase.NOT_INSTALLED, "Optional · 1.4 MB download")
        }
        mapOf(OfflineModel.TESSERACT_ARABIC.id to status, OfflineModel.EMBEDDING_GEMMA.id to ModelStatus(ModelPhase.PLANNED, "Not included in this version. No Gemini Nano is required for photo analysis."))
    }.flowOn(kotlinx.coroutines.Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val indexingState = workManager.getWorkInfosForUniqueWorkFlow(WorkManagerScheduler.UNIQUE_SCAN_WORK_NAME)
        .map { jobs ->
            val active = jobs.lastOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                ?: jobs.lastOrNull { !it.state.isFinished } ?: jobs.lastOrNull()
            when (active?.state) {
                androidx.work.WorkInfo.State.RUNNING -> "Analyzing · ${active.progress.getInt("processed", 0)} completed · ${active.progress.getInt("failed", 0)} need attention"
                androidx.work.WorkInfo.State.ENQUEUED -> when {
                    active.constraints.requiresDeviceIdle() -> "Scheduled · waits for idle device and sufficient battery"
                    active.runAttemptCount > 0 -> "Retry scheduled · check library access"
                    else -> "Queued · starting photo analysis"
                }
                androidx.work.WorkInfo.State.BLOCKED -> "Waiting for the previous batch"
                androidx.work.WorkInfo.State.CANCELLED -> "Scan stopped"
                androidx.work.WorkInfo.State.SUCCEEDED -> when (active.outputData.getString(com.omnimemoria.data.worker.PhotoIndexWorker.OUTPUT_STATUS)) {
                    "disabled" -> "Enable an analysis feature to scan photos"
                    "continuing", "preferences_changed" -> "Continuing with the next batch"
                    else -> "Scan complete · ${active.outputData.getInt("processed", 0)} analyzed · ${active.outputData.getInt("failed", 0)} need attention in last batch"
                }
                androidx.work.WorkInfo.State.FAILED -> "Scan needs storage access or retry"
                else -> "Off"
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Loading")

    val scanEnabled = featureStates.map { AnalysisPolicy.required(it) != 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun scanNow() { viewModelScope.launch { safely("Unable to start photo analysis. Please try again.") {
        if (AnalysisPolicy.required(featureStates.value) == 0) {
            _error.value = "Enable Text Extraction, Image Classification or another analysis feature first."
        } else scheduler.startLibraryScan()
    } } }
    fun cancelScan() { viewModelScope.launch { safely("Unable to cancel the scan.") { scheduler.cancelLibraryScan().await() } } }

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        viewModelScope.launch { safely { featureFlagManager.setEnabled(flag, enabled) } }
    }

    fun requestModel(id: String, requested: Boolean = true) {
        val model = OfflineModel.fromId(id) ?: return
        if (model != OfflineModel.TESSERACT_ARABIC) return
        viewModelScope.launch { safely {
            appPreferences.setBoolean(requestKey(model), requested)
            if (model == OfflineModel.TESSERACT_ARABIC) {
                if (!requested) workManager.cancelUniqueWork(com.omnimemoria.data.worker.ModelDownloadWorker.UNIQUE_ARABIC_DOWNLOAD).await()
                else {
                    val request = com.omnimemoria.data.worker.ModelDownloadWorker.request()
                    workManager.enqueueUniqueWork(com.omnimemoria.data.worker.ModelDownloadWorker.UNIQUE_ARABIC_DOWNLOAD, androidx.work.ExistingWorkPolicy.REPLACE, request).await()
                }
            }
        } }
    }

    fun dismissError() { _error.value = null }

    private suspend fun safely(message: String = "Unable to save settings. Please try again.", action: suspend () -> Unit) {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _error.value = message }
    }

    private fun requestKey(model: OfflineModel) = when (model) {
        OfflineModel.TESSERACT_ARABIC -> AppPreferences.PreferencesKeys.REQUEST_TESSERACT_DOWNLOAD
        OfflineModel.EMBEDDING_GEMMA -> AppPreferences.PreferencesKeys.REQUEST_EMBEDDER_DOWNLOAD
    }
}
