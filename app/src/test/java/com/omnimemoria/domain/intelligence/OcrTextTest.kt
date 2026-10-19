package com.omnimemoria.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextTest {
    @Test fun successfulArabicSurvivesLaterLatinRetry() {
        assertEquals("مرحبا بالعالم\nHello", OcrText.merge("مرحبا بالعالم", "Hello"))
    }
    @Test fun trimsAndDeduplicatesLinesAcrossEngines() {
        assertEquals("Hello\nمرحبا", OcrText.merge(" Hello\n\nمرحبا", "Hello \n مرحبا"))
    }
    @Test fun limitsPersistedText() {
        assertEquals(100_000, OcrText.merge("a".repeat(99_990), "b".repeat(100)).length)
    }
}
