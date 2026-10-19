package com.omnimemoria.domain.intelligence

object EntityExtractor {
    private val phone = Regex("""(?<![\p{L}\d])\+?[\d][\d ()\-.]{5,24}[\d](?![\p{L}\d])""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val url = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    fun normalizeDigits(text: String) = text.map { c ->
        when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c }
    }.joinToString("")
    fun phones(text: String): List<String> = phone.findAll(normalizeDigits(text)).map { it.value.filter { c -> c.isDigit() || c == '+' } }
        .filter { it.count(Char::isDigit) in 7..15 }.distinct().take(20).toList()
    fun emails(text: String) = email.findAll(text).map { it.value }.distinct().take(20).toList()
    fun urls(text: String) = url.findAll(text).map { it.value.trimEnd('.', ',', ')', '،') }.distinct().take(20).toList()
}
