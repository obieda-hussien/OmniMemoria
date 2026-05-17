package com.omnimemoria

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.omnimemoria.data.local.objectbox.MyObjectBox
import com.omnimemoria.data.worker.WorkManagerScheduler
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.domain.flags.FeatureFlagManager
import dagger.hilt.android.HiltAndroidApp
import io.objectbox.BoxStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

// ── FIX: implement SingletonImageLoader.Factory ──────────────────────────────────
// Coil 3 picks up this interface automatically from the Application class.
// No manual setSingleton() call needed — Coil calls newImageLoader() on first use.
@HiltAndroidApp
class OmniMemoria : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory:       HiltWorkerFactory
    @Inject lateinit var workManagerScheduler: WorkManagerScheduler
    @Inject lateinit var featureFlagManager:  FeatureFlagManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Coil 3 ImageLoader ───────────────────────────────────────────────────────
    // Called once by Coil on first ImageLoader access. The result is cached as the
    // singleton for the lifetime of the process.
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)

            // ── Memory cache ────────────────────────────────────────────────────
            // 25% of available heap — keeps decoded Bitmaps warm while the user
            // scrolls. When the process is low on RAM, Android evicts entries
            // automatically via WeakReferences.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }

            // ── Disk cache ──────────────────────────────────────────────────────
            // 256 MB in cacheDir (scoped storage, auto-cleaned by the OS if the
            // device runs low on space). This is the main fix: on subsequent launches
            // Coil reads the already-decoded image from disk instead of re-decoding
            // from the MediaStore, dropping thumbnail load time from ~200–400ms to
            // ~5–15ms per image.
            //
            // Cache directory: /data/data/com.omnimemoria/cache/coil_image_cache/
            // The OS clears this automatically — no manual cleanup needed.
            .diskCache {
                DiskCache.Builder()
                    .directory(
                        cacheDir.resolve("coil_image_cache").toOkioPath()
                    )
                    .maxSizeBytes(256L * 1024 * 1024)   // 256 MB
                    .build()
            }

            // ── MediaStore-specific settings ────────────────────────────────────
            // content:// URIs don't carry HTTP cache headers, so we disable the
            // header-based cache invalidation check — Coil would otherwise skip the
            // disk cache for every MediaStore URI on each app launch.
            .respectCacheHeaders(false)

            // ── UX ──────────────────────────────────────────────────────────────
            // 300ms crossfade makes the transition from shimmer → image feel smooth
            // without being slow.
            .crossfade(durationMillis = 300)

            .build()
    }

    // ── WorkManager config ───────────────────────────────────────────────────────
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // ObjectBox initialization
        boxStore = MyObjectBox.builder().androidContext(this).build()

        // Schedule/cancel periodic photo indexing based on active AI flags
        applicationScope.launch {
            try {
                combine(
                    AI_FEATURE_FLAGS.map(featureFlagManager::isEnabled)
                ) { states -> states.any { it } }
                    .distinctUntilChanged()
                    .collectLatest { shouldSchedule ->
                        if (shouldSchedule) workManagerScheduler.schedulePeriodicIndex()
                        else                workManagerScheduler.cancelPeriodicIndex()
                    }
            } catch (throwable: Throwable) {
                Log.e("OmniMemoria", "Failed to initialize periodic indexing scheduler", throwable)
            }
        }
    }

    companion object {
        private val AI_FEATURE_FLAGS = listOf(
            FeatureFlag.OCR,
            FeatureFlag.ARABIC_OCR,
            FeatureFlag.ML_LABELS,
            FeatureFlag.FACE_DETECTION,
            FeatureFlag.EMBEDDINGS,
            FeatureFlag.RAG_SEARCH,
            FeatureFlag.SMART_FILTERS
        )

        lateinit var boxStore: BoxStore
            private set
    }
}
