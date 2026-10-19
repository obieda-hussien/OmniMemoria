package com.omnimemoria.domain.intelligence

/** Preserve successful language results when another OCR stage is retried. */
object OcrText {
    fun merge(existing: String, incoming: String): String =
        (existing.lineSequence() + incoming.lineSequence())
            .map(String::trim).filter(String::isNotBlank).distinct()
            .joinToString("\n").take(100_000)
}
