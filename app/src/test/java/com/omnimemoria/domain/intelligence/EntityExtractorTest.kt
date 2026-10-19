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
    @Test fun hallucinatedDocumentNumberAndDatesAreNotPhoneActions() {
        assertTrue(EntityExtractor.phones("1 © 9009687909968.. +\n2026/10/20\n38 7").isEmpty())
        assertTrue(EntityExtractor.phones("Invoice: 01012345678").isEmpty())
        assertTrue(EntityExtractor.phones("01012345678 جنيه").isEmpty())
    }
    @Test fun validatesInternationalPlansAndDeduplicatesDifferentFormats() {
        assertEquals(listOf("+442079460018"), EntityExtractor.phones("+44 20 7946 0018"))
        assertEquals(listOf("01012345678"), EntityExtractor.phones("01012345678 / +20 10 1234 5678"))
    }
    @Test fun extractsArabicAndLatinCurrencyAmountsExactly() {
        assertEquals(listOf(DetectedAmount("1234.50", "EGP"), DetectedAmount("25.75", "USD")),
            EntityExtractor.amounts("الإجمالي ١٬٢٣٤٫٥٠ ج.م و USD 25.75"))
        assertEquals(listOf(DetectedAmount("250", "ريال"), DetectedAmount("90", "EUR")),
            EntityExtractor.amounts("۲۵۰ ريال · €90"))
        assertTrue(EntityExtractor.amounts("2026/10/20 01012345678").isEmpty())
        assertTrue(EntityExtractor.amounts("١776/0٠ جنيه").isEmpty())
    }
    @Test fun ambiguousCurrencyWordsDoNotInventACountry() {
        assertEquals(listOf(DetectedAmount("25", "$"), DetectedAmount("30", "GBP"), DetectedAmount("40", "SAR")),
            EntityExtractor.amounts("$25 · 30 جنيه إسترليني · 40 ريال سعودي"))
    }
}
