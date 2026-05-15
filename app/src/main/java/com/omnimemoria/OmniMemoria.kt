package com.omnimemoria

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.omnimemoria.data.worker.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
import com.omnimemoria.data.local.objectbox.MyObjectBox
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.domain.flags.FeatureFlagManager
import io.objectbox.BoxStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class OmniMemoria : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManagerScheduler: WorkManagerScheduler

    @Inject
    lateinit var featureFlagManager: FeatureFlagManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        boxStore = MyObjectBox.builder().androidContext(this).build()

        applicationScope.launch {
            try {
                val shouldSchedulePeriodicIndexing =
                    combine(
                        featureFlagManager.isEnabled(FeatureFlag.OCR),
                        featureFlagManager.isEnabled(FeatureFlag.ML_LABELS)
                    ) { ocrEnabled, mlLabelsEnabled ->
                        ocrEnabled || mlLabelsEnabled
                    }.first()
                if (shouldSchedulePeriodicIndexing) {
                    workManagerScheduler.schedulePeriodicIndex()
                }
            } catch (throwable: Throwable) {
                Log.e("OmniMemoria", "Failed to initialize periodic indexing scheduler", throwable)
            }
        }
    }

    companion object {
        lateinit var boxStore: BoxStore
            private set
    }
}
