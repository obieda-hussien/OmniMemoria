package com.omnimemoria.data.worker

import android.app.Application
import androidx.work.NetworkType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ScanSchedulingTest {
    private fun scheduler() = WorkManagerScheduler(RuntimeEnvironment.getApplication())

    @Test fun idleScanBuildsWithoutInvalidExplicitBackoff() {
        // This invokes the real WorkManager builder. Combining idle and explicit
        // backoff throws on API 23+, which previously crashed the Settings button.
        val request = scheduler().scanRequest()
        assertTrue(request.workSpec.constraints.requiresDeviceIdle())
        assertTrue(request.workSpec.constraints.requiresBatteryNotLow())
    }
    @Test fun explicitScanDoesNotWaitForIdleOrBatteryAndRetainsItsMode() {
        val request = scheduler().scanRequest(userInitiated = true)
        assertFalse(request.workSpec.constraints.requiresDeviceIdle())
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
        assertTrue(request.workSpec.input.getBoolean(PhotoIndexWorker.WORK_INPUT_USER_INITIATED, false))
    }
    @Test fun individualPhotoAnalysisAlsoStartsWithoutBatteryRestrictions() {
        val request = scheduler().immediateRequest(listOf(42))
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
        assertFalse(request.workSpec.constraints.requiresDeviceIdle())
        assertTrue(request.workSpec.input.getBoolean(PhotoIndexWorker.WORK_INPUT_USER_INITIATED, false))
        assertArrayEquals(longArrayOf(42), request.workSpec.input.getLongArray(PhotoIndexWorker.WORK_INPUT_PHOTO_IDS))
    }
    @Test fun continuationKeepsManualModeAndCursor() {
        val request = scheduler().scanRequest(afterId = 120, userInitiated = true)
        assertFalse(request.workSpec.constraints.requiresDeviceIdle())
        assertEquals(120L, request.workSpec.input.getLong(PhotoIndexWorker.WORK_INPUT_AFTER_ID, 0))
    }
    @Test fun arabicDownloadNeedsNetworkButNotIdleOrBattery() {
        val request = ModelDownloadWorker.request()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
        assertFalse(request.workSpec.constraints.requiresDeviceIdle())
    }
}
