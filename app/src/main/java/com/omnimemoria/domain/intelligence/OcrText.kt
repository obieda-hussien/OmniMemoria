package com.omnimemoria.domain.intelligence

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

data class OcrLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val language: String = "latin", val confidence: Float = 100f) {
    val area: Float get() = (right - left) * (bottom - top)
    fun isValid() = listOf(left, top, right, bottom, confidence).all(Float::isFinite) &&
        left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && right > left && bottom > top
    fun overlap(other: OcrLine): Float = (min(right, other.right) - max(left, other.left)).coerceAtLeast(0f) *
        (min(bottom, other.bottom) - max(top, other.top)).coerceAtLeast(0f)
}

/** Preserve successful language results when another OCR stage is retried. */
object OcrText {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]"), "").trim()
    fun arabicLetters(text: String) = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC && it.isLetter() }

    /** Prefer Arabic recognition in the same region over Latin transliteration noise. */
    fun resolve(input: List<OcrLine>): List<OcrLine> {
        val valid = input.asSequence().filter(OcrLine::isValid).map { it.copy(text = normalize(it.text)) }
            .filter { it.text.isNotBlank() && it.text.any(Char::isLetterOrDigit) }.take(1500).toList()
        val arabic = mutableListOf<OcrLine>()
        valid.filter { it.language == "arabic" && it.confidence >= 35f &&
            (arabicLetters(it.text) >= 2 || (it.confidence >= 75f && it.text.count(Char::isDigit) >= 3 &&
                it.text.all { c -> c.isDigit() || c.isWhitespace() || c in "+-.,/():٪%$€£٫٬" })) }
            .sortedByDescending { it.confidence }.forEach { line ->
                if (arabic.none { line.overlap(it) / min(line.area, it.area) >= 0.65f }) arabic += line
            }
        val latin = valid.filter { it.language != "arabic" && arabic.none { ar ->
            arabicLetters(ar.text) >= 2 && it.overlap(ar) / it.area >= 0.45f
        } }
        return (arabic + latin).sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
            .distinctBy { normalize(it.text).replace(Regex("\\s+"), " ") }.take(1000)
    }
    fun text(lines: List<OcrLine>) = resolve(lines).joinToString("\n") { it.text }.take(100_000)
    fun merge(existing: String, incoming: String): String =
        (existing.lineSequence() + incoming.lineSequence())
            .map(::normalize).filter(String::isNotBlank).distinct()
            .joinToString("\n").take(100_000)
}
