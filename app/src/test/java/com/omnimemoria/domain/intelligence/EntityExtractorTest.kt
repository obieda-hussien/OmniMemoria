package com.omnimemoria.domain.intelligence

import org.junit.Assert.*
import org.junit.Test

class EntityExtractorTest {
    @Test fun arabicAndPersianNumbersBecomeDialableNumbers() {
        assertEquals(listOf("01012345678"), EntityExtractor.phones("اتصل ٠١٠١٢٣٤٥٦٧٨"))
        assertEquals(listOf("+201012345678"), EntityExtractor.phones("Call +۲۰ ۱۰ ۱۲۳۴۵۶۷۸"))
    }
    @Test fun extractsEmailsAndHttpUrlsWithoutExecutingAnything() {
        assertEquals(listOf("hi@example.com"), EntityExtractor.emails("email hi@example.com please"))
        assertEquals(listOf("https://example.com/path"), EntityExtractor.urls("https://example.com/path."))
        assertTrue(EntityExtractor.urls("javascript:alert(1)").isEmpty())
    }
    @Test fun duplicatesAndNonPhoneLongNumbersAreIgnored() {
        assertEquals(1, EntityExtractor.phones("01012345678\n01012345678").size)
        assertTrue(EntityExtractor.phones("12345678901234567").isEmpty())
    }
}
