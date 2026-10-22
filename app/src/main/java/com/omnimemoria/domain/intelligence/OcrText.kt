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

    private data class TextRow(val lines: MutableList<OcrLine>) {
        val top: Float get() = lines.minOf { it.top }
        val bottom: Float get() = lines.maxOf { it.bottom }
        val centerY: Float get() = (top + bottom) / 2f
        val height: Float get() = (bottom - top).coerceAtLeast(0.001f)
    }

    private fun sameVisualRow(line: OcrLine, row: TextRow): Boolean {
        val overlap = (min(line.bottom, row.bottom) - max(line.top, row.top)).coerceAtLeast(0f)
        val smallerHeight = min(line.bottom - line.top, row.bottom - row.top).coerceAtLeast(0.001f)
        val centerDistance = kotlin.math.abs((line.top + line.bottom) / 2f - row.centerY)
        return overlap / smallerHeight >= 0.30f || centerDistance <= max(line.bottom - line.top, row.height) * 0.38f
    }

    /** Group detections sharing a baseline, ordering side-by-side text by the dominant script. */
    private fun rowsInReadingOrder(input: List<OcrLine>): List<TextRow> {
        val rows = mutableListOf<TextRow>()
        input.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left }).forEach { line ->
            val row = rows.filter { sameVisualRow(line, it) }
                .minByOrNull { kotlin.math.abs((line.top + line.bottom) / 2f - it.centerY) }
            if (row == null) rows += TextRow(mutableListOf(line)) else row.lines += line
        }
        return rows.sortedBy { it.top }.onEach { row ->
            val arabicCount = row.lines.sumOf { arabicLetters(it.text) }
            val latinCount = row.lines.sumOf { it.text.count { ch ->
                Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.LATIN && ch.isLetter()
            } }
            if (arabicCount >= 2 && arabicCount >= latinCount) {
                row.lines.sortByDescending { (it.left + it.right) / 2f }
            } else {
                row.lines.sortBy { (it.left + it.right) / 2f }
            }
        }
    }

    /** Prefer confident Arabic recognition in the same region over Latin transliteration noise. */
    fun resolve(input: List<OcrLine>): List<OcrLine> {
        val valid = input.asSequence().filter(OcrLine::isValid).map { it.copy(text = normalize(it.text)) }
            .filter { it.text.isNotBlank() && it.text.any(Char::isLetterOrDigit) }.take(1500).toList()
        val arabic = mutableListOf<OcrLine>()
        valid.filter { it.language == "arabic" && it.confidence >= 45f &&
            (arabicLetters(it.text) >= 2 || (it.confidence >= 78f && it.text.count(Char::isDigit) >= 3 &&
                it.text.all { ch -> ch.isDigit() || ch.isWhitespace() || ch in "+-.,/():٪%$€£٫٬" })) }
            .sortedByDescending { it.confidence }.forEach { line ->
                if (arabic.none { it.area > 0f && line.overlap(it) / min(line.area, it.area) >= 0.65f }) arabic += line
            }
        val latin = valid.filter { it.language != "arabic" && (it.language == "code" || arabic.none { ar ->
            arabicLetters(ar.text) >= 2 && ar.area > 0f && it.overlap(ar) / it.area >= 0.45f
        }) }

        // Legitimate repeated words at different positions must survive. Collapse only
        // duplicate text detections occupying essentially the same image region.
        val unique = (arabic + latin).distinctBy { line ->
            val normalized = normalize(line.text).replace(Regex("\\s+"), " ").lowercase()
            normalized + "@" + (line.left * 100).toInt() + ":" + (line.top * 100).toInt() +
                ":" + (line.right * 100).toInt() + ":" + (line.bottom * 100).toInt()
        }.take(1000)
        return rowsInReadingOrder(deduplicateOverlappingText(unique)).flatMap { it.lines }
    }

    private fun deduplicateOverlappingText(input: List<OcrLine>): List<OcrLine> {
        val kept = mutableListOf<OcrLine>()
        input.sortedByDescending { it.confidence }.forEach { candidate ->
            fun comparable(value: String): String {
                val normalized = normalize(value).removePrefix("QR code:").trim()
                return normalized.replace(Regex("\\s+"), " ").lowercase()
            }
            val candidateText = comparable(candidate.text)
            val duplicate = kept.indexOfFirst { existing ->
                if (candidateText != comparable(existing.text)) return@indexOfFirst false
                val smaller = min(candidate.area, existing.area).coerceAtLeast(0.001f)
                candidate.overlap(existing) / smaller >= 0.45f
            }
            if (duplicate < 0) kept += candidate
            else if (candidate.language == "code" && kept[duplicate].language != "code") kept[duplicate] = candidate
        }
        return kept
    }

    private fun isUrlStart(value: String) =
        Regex("(?i)^\\s*(?:https?://|www\\.)").containsMatchIn(value)

    private fun compactUrl(value: String) = value.replace(Regex("\\s+"), "")

    private fun canJoinUrl(previous: String, current: String): Boolean {
        val next = current.trim()
        if (!isUrlStart(previous) || next.isEmpty() || isUrlStart(next) ||
            next.any(Char::isWhitespace) || arabicLetters(next) > 0) return false
        if (next.any { !it.isLetterOrDigit() && it !in "./?&=#%_-~:+@!$'()[],;" }) return false
        return next.first() in "./?&=#%_-" || previous.trimEnd().lastOrNull() in "/?&=#%-_."
    }

    /** Rejoin URL fragments split across adjacent OCR lines without flattening normal paragraphs. */
    fun text(lines: List<OcrLine>): String {
        val rows = rowsInReadingOrder(resolve(lines))
        if (rows.isEmpty()) return ""
        val rendered = mutableListOf<Pair<String, Boolean>>()
        var previousRow: TextRow? = null
        rows.forEach { row ->
            val prior = previousRow
            val paragraphGap = prior != null &&
                row.top - prior.bottom > max(0.018f, max(row.height, prior.height) * 1.25f)
            val current = row.lines.joinToString(" ") { it.text.trim() }
            val previousText = rendered.lastOrNull()?.first
            if (previousText != null && prior != null && !paragraphGap && canJoinUrl(previousText, current)) {
                rendered[rendered.lastIndex] = compactUrl(previousText) + compactUrl(current) to rendered.last().second
            } else {
                rendered += current to paragraphGap
            }
            previousRow = row
        }
            (if (index == 0) "" else if (paragraphBefore) "\n\n" else "\n") + value
            (if (index == 0) "" else if (paragraphBefore) "\\n\\n" else "\\n") + value
        }.joinToString("").take(100_000)
    }

    fun merge(existing: String, incoming: String): String =
        (existing.lineSequence() + incoming.lineSequence())
            .map(::normalize).filter(String::isNotBlank).distinct()
            .joinToString("\n").take(100_000)
}
