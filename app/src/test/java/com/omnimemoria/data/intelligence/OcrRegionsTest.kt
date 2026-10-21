package com.omnimemoria.data.intelligence

import android.app.Application
import com.omnimemoria.domain.intelligence.OcrLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class OcrRegionsTest {
    @Test fun invalidOrIncompleteBoxesCannotReachTheCanvas() {
        assertTrue(OcrRegions.read("""{"lines":[{"text":"bad","left":"NaN","top":0,"right":1,"bottom":0.1}]}""").isEmpty())
        assertTrue(OcrRegions.read("""{"lines":[{"text":"bad","left":0.8,"top":0,"right":0.2,"bottom":0.1}]}""").isEmpty())
        assertTrue(OcrRegions.read("truncated {").isEmpty())
    }
    @Test fun languageConfidenceAndCoordinatesSurviveRoundTrip() {
        val line = OcrLine("رقم الهاتف", 0.1f, 0.2f, 0.8f, 0.3f, "arabic", 88f)
        assertEquals(listOf(line), OcrRegions.read(OcrRegions.write(1080, 1920, listOf(line))))
    }
}
