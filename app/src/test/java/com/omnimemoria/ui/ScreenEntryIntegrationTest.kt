package com.omnimemoria.ui

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.omnimemoria.MainActivity
import com.omnimemoria.data.local.db.AppDatabase
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.domain.flags.FeatureFlagManager
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.detail.PhotoMetadataCard
import com.omnimemoria.ui.search.SearchScreen
import com.omnimemoria.ui.settings.SettingsScreen
import com.omnimemoria.ui.theme.OmniMemoriaTheme
import com.omnimemoria.ui.vault.VaultTabScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

// Robolectric 4.14 has no API-30 shadow for this no-arg framework method.
// Keep the initial permission gate closed before replacing Activity content.
@Implements(android.os.Environment::class)
class ScreenEntryStorageShadow {
    companion object {
        @JvmStatic @Implementation fun isExternalStorageManager() = false
    }
}

/** Exercises real Hilt factories, Room and preference flows, not just UI fixtures. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = HiltTestApplication::class, shadows = [ScreenEntryStorageShadow::class])
class ScreenEntryIntegrationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var flags: FeatureFlagManager
    @Inject lateinit var database: AppDatabase

    @Before fun initialize() {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(compose.activity,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        // Opens and validates the real Room schema before any collectors run.
        runBlocking { database.photoIntelligenceDao().getAll() }
        runBlocking { flags.setEnabled(FeatureFlag.VAULT, false) }
        runBlocking { flags.setEnabled(FeatureFlag.ARABIC_OCR, false) }
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent { OmniMemoriaTheme(darkTheme = true, content = content) }
        }
    }

    @Test fun settingsOpensWithRealViewModel() {
        show { SettingsScreen() }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Scan library now").assertIsDisplayed()
    }

    @Test fun searchOpensWithRealViewModel() {
        show { SearchScreen({}, {}) }
        compose.onNodeWithText("Quick Filters").assertIsDisplayed()
        compose.onNodeWithText("Phone Numbers").assertIsDisplayed()
    }

    @Test fun confirmingArabicSetupEnqueuesTheActualModelRequest() {
        show { SettingsScreen() }
        compose.onNodeWithText("Arabic OCR (Tesseract)").performScrollTo().performClick()
        compose.onNodeWithText("Download").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            androidx.work.WorkManager.getInstance(compose.activity).getWorkInfosForUniqueWork(
                com.omnimemoria.data.worker.ModelDownloadWorker.UNIQUE_ARABIC_DOWNLOAD
            ).get().isNotEmpty()
        }
    }

    @Test fun disabledVaultOpensWithRealViewModel() {
        show { VaultTabScreen({}) }
        compose.onNodeWithText("Vault is disabled").assertIsDisplayed()
    }

    @Test fun enabledVaultOpensWithRealViewModels() {
        runBlocking { flags.setEnabled(FeatureFlag.VAULT, true) }
        show { VaultTabScreen({}) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Vault is disabled").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun photoDetailsSheetOpensWithRealAnalysisViewModel() {
        val photo = MediaPhoto(42, Uri.parse("content://media/external/images/media/42"), "test.jpg",
            100, "image/jpeg", 0, width = 640, height = 480, latitude = null, longitude = null)
        show { ModalBottomSheet(onDismissRequest = {}) { PhotoMetadataCard(photo) } }
        compose.onNodeWithText("Media details").assertIsDisplayed()
        compose.onNodeWithText("On-device analysis").performScrollTo().assertIsDisplayed()
    }
}
