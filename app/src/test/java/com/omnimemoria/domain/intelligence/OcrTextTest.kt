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
    @Test fun preservesReadableArabicAtTheConfiguredPaddleConfidenceFloor() {
        val line = OcrLine("الرابط الكامل", 0.05f, 0.1f, 0.95f, 0.16f, "arabic", 38f)
        assertEquals("الرابط الكامل", OcrText.text(listOf(line)))
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

    @Test fun repeatedWordsAtDifferentLocationsAreNotCollapsed() {
        val lines = listOf(
            OcrLine("OK", 0.1f, 0.1f, 0.25f, 0.14f),
            OcrLine("OK", 0.1f, 0.3f, 0.25f, 0.34f))
        assertEquals("OK\n\nOK", OcrText.text(lines))
    }

    @Test fun mixedEnglishAndArabicOnOneVisualRowUseRightToLeftRegionOrder() {
        val lines = listOf(
            OcrLine("Hello", 0.1f, 0.1f, 0.3f, 0.15f),
            OcrLine("مرحبا", 0.55f, 0.101f, 0.9f, 0.151f, "arabic", 90f))
        assertEquals("مرحبا Hello", OcrText.text(lines))
    }

    @Test fun closeRowsStayTogetherWhileLargeVerticalGapsMarkParagraphs() {
        val lines = listOf(
            OcrLine("First line", 0.1f, 0.1f, 0.8f, 0.14f),
            OcrLine("Second line", 0.1f, 0.155f, 0.8f, 0.195f),
            OcrLine("Next paragraph", 0.1f, 0.35f, 0.8f, 0.39f))
        assertEquals("First line\nSecond line\n\nNext paragraph", OcrText.text(lines))
    }
    @Test fun splitUrlFragmentsAreJoinedAcrossAdjacentRows() {
        val lines = listOf(
            OcrLine("https://play.google.com/store/apps/details?id=app", 0.1f, 0.10f, 0.9f, 0.15f),
            OcrLine(".Source.getcontact&hl=en", 0.1f, 0.16f, 0.9f, 0.20f)
        )
        assertEquals(
            "https://play.google.com/store/apps/details?id=app.Source.getcontact&hl=en",
            OcrText.text(lines)
        )
    }

}
