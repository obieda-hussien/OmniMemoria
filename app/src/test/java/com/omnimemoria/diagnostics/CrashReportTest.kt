package com.omnimemoria.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = android.app.Application::class)
class CrashReportTest {
    @Test fun recordsCauseAndAlwaysDelegatesToAndroidHandler() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val error = IllegalStateException("screen failed", NoSuchMethodException("factory"))
        var received: Throwable? = null
        try {
            CrashReport.clear(context)
            Thread.setDefaultUncaughtExceptionHandler { _, failure -> received = failure }
            CrashReport.install(context)
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertSame(error, received)
            val report = CrashReport.read(context)!!
            assertTrue(report.contains("IllegalStateException: screen failed"))
            assertTrue(report.contains("NoSuchMethodException: factory"))
            CrashReport.clear(context)
            assertNull(CrashReport.read(context))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
