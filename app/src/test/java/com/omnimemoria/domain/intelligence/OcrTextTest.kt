package com.omnimemoria.domain.intelligence

import org.junit.Assert.*
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
    @Test fun arabicReplacesOverlappingLatinNoiseButKeepsSeparateEnglish() {
        val lines = listOf(
            OcrLine("wgisll aayslsli ca 2026", 0f, 0f, 1f, 0.1f),
            OcrLine("البورصة المصرية 2026", 0f, 0f, 1f, 0.1f, "arabic", 85f),
            OcrLine("ChatGPT", 0f, 0.2f, 0.4f, 0.3f))
        assertEquals("البورصة المصرية 2026\nChatGPT", OcrText.text(lines))
    }
    @Test fun failedArabicRetryCannotReplaceSuccessfulLatin() {
        val lines = listOf(OcrLine("Hello", 0f, 0f, 1f, 0.1f),
            OcrLine("كلام خاطئ", 0f, 0f, 1f, 0.1f, "arabic", 10f))
        assertEquals("Hello", OcrText.text(lines))
    }
    @Test fun malformedCoordinatesAndNumericNoiseDoNotReachTheOverlay() {
        assertTrue(OcrText.resolve(listOf(OcrLine("invalid", Float.NaN, 0f, 1f, 0.1f),
            OcrLine("invalid", 0.7f, 0f, 0.1f, 0.2f),
            OcrLine("© 9009687909968", 0f, 0f, 1f, 0.1f, "arabic", 80f))).isEmpty())
    }
    @Test fun duplicatePageAndLinePassesKeepTheMoreConfidentResult() {
        val page = OcrLine("هناك مشكلة في طريقة الدقع", 0f, 0f, 1f, 0.1f, "arabic", 45f)
        val crop = page.copy(text = "هناك مشكلة في طريقة الدفع", confidence = 90f)
        assertEquals(crop.text, OcrText.text(listOf(page, crop)))
    }
    @Test fun presentationFormsAndBidiMarkersAreNormalized() {
        assertEquals("لا 2026", OcrText.normalize("\u200fﻻ 2026\u200e"))
    }
}
