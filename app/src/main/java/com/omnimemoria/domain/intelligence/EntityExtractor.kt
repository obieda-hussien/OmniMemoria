package com.omnimemoria.domain.intelligence

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.math.BigDecimal

data class DetectedAmount(val value: String, val currency: String)

object EntityExtractor {
    private val phoneUtil by lazy { PhoneNumberUtil.getInstance() }
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val url = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    fun normalizeDigits(text: String) = text.map { c ->
        when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c }
    }.joinToString("")
    private val date = Regex("""\b\d{1,4}[-/]\d{1,2}[-/]\d{1,4}\b""")
    private val identifier = Regex("""(?:invoice|order|reference|account|رقم\s+(?:الفاتورة|الطلب|الحساب|المعاملة))\s*[:#]?\s*$""", RegexOption.IGNORE_CASE)
    private val currency = """(?:EGP|USD|EUR|GBP|SAR|AED|ج\s*\.?\s*م\.?|جنيه(?:ات)?|ريال|درهم|دولار|يورو|[\u0024€£])"""
    private val number = """(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?"""
    private val money = Regex("""(?<![\p{L}\d])(?:($currency)\s*(-?$number)|(-?$number)\s*($currency))(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private fun numericText(text: String) = normalizeDigits(text)
        .replace('٬', ',').replace('٫', '.').replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]"), "")

    /** Validate numbering plans; arbitrary OCR digit runs must not become calls. */
    fun phones(text: String, region: String = "EG"): List<String> {
        val normalized = numericText(text)
        val excluded = (date.findAll(normalized).map { it.range } + money.findAll(normalized).map { it.range }).toList()
        val found = linkedMapOf<String, String>()
        for (match in phoneUtil.findNumbers(normalized, region, PhoneNumberUtil.Leniency.VALID, 100)) {
            val range = match.start() until match.end()
            if (excluded.any { it.first <= range.last && range.first <= it.last }) continue
            val prefix = normalized.substring((match.start() - 60).coerceAtLeast(0), match.start()).substringAfterLast('\n')
            if (identifier.containsMatchIn(prefix)) continue
            val e164 = phoneUtil.format(match.number(), PhoneNumberUtil.PhoneNumberFormat.E164)
            val raw = match.rawString().trim()
            found.putIfAbsent(e164, if (raw.startsWith('+') || raw.startsWith("00")) e164
                else phoneUtil.format(match.number(), PhoneNumberUtil.PhoneNumberFormat.NATIONAL).filter(Char::isDigit))
            if (found.size == 20) break
        }
        return found.values.toList()
    }

    /** Amounts require an explicit currency; no inference from bare numbers. */
    fun amounts(text: String): List<DetectedAmount> = money.findAll(numericText(text)).mapNotNull { match ->
        val normalized = numericText(text)
        // Never extract a trailing fragment from a date or a broken OCR number.
        if (match.range.first >= 2 && normalized[match.range.first - 1] in "/.," && normalized[match.range.first - 2].isDigit()) return@mapNotNull null
        if (match.range.last + 2 < normalized.length && normalized[match.range.last + 1] in "/.," && normalized[match.range.last + 2].isDigit()) return@mapNotNull null
        val token = match.groupValues[1].ifBlank { match.groupValues[4] }.replace(Regex("[\\s.]"), "").uppercase()
        val code = when (token) {
            "EGP", "جم", "جنيه", "جنيهات" -> "EGP"
            "USD", "\u0024", "دولار" -> "USD"
            "EUR", "€", "يورو" -> "EUR"
            "GBP", "£" -> "GBP"
            "SAR", "ريال" -> "SAR"
            "AED", "درهم" -> "AED"
            else -> return@mapNotNull null
        }
        val value = match.groupValues[2].ifBlank { match.groupValues[3] }.replace(",", "")
        runCatching { DetectedAmount(BigDecimal(value).toPlainString(), code) }.getOrNull()
    }.distinct().take(20).toList()
    fun emails(text: String) = email.findAll(text).map { it.value }.distinct().take(20).toList()
    fun urls(text: String) = url.findAll(text).map { it.value.trimEnd('.', ',', ')', '،') }.distinct().take(20).toList()
}
