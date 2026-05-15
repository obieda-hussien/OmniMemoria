package com.omnimemoria

import android.app.Application
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
            val shouldSchedulePeriodicIndexing =
                featureFlagManager.isEnabled(FeatureFlag.OCR).first() ||
                    featureFlagManager.isEnabled(FeatureFlag.ML_LABELS).first()
            if (shouldSchedulePeriodicIndexing) {
                workManagerScheduler.schedulePeriodicIndex()
            }
        }
    }

    companion object {
        lateinit var boxStore: BoxStore
            private set
    }
}
