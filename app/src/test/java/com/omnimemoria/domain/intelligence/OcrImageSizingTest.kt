package com.omnimemoria.domain.intelligence

import org.junit.Assert.*
import org.junit.Test

class OcrImageSizingTest {
    @Test fun ordinaryScreenshotsKeepTheirOriginalPixels() {
        assertEquals(1080 to 1920, OcrImageSizing.target(1080, 1920))
    }
    @Test fun smallTextCanBeEnlargedWithinTheMemoryBudget() {
        val (w, h) = OcrImageSizing.target(674, 1536, true)
        assertTrue(w > 1000 && h > 2000)
        assertTrue(w.toLong() * h <= OcrImageSizing.MAX_PIXELS)
        assertTrue(maxOf(w, h) <= OcrImageSizing.MAX_EDGE)
    }
    @Test fun largePhotosAndPanoramasNeverExceedThePixelBudget() {
        for ((width, height) in listOf(12000 to 9000, 20000 to 100, 100 to 20000)) {
            val (w, h) = OcrImageSizing.target(width, height)
            assertTrue(w > 0 && h > 0 && maxOf(w, h) <= OcrImageSizing.MAX_EDGE)
            assertTrue(w.toLong() * h <= OcrImageSizing.MAX_PIXELS)
        }
    }
}
