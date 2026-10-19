package com.omnimemoria.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/** Versioned, deterministic encoding; independent of Android for migration/round-trip tests. */
object FilterPresetCodec {
    fun encode(filter: FilterConfig): String = with(filter) {
        mapOf(
            "v" to "1",
            "types" to mediaTypes.map { it.name }.sorted().joinToString(","),
            "formats" to mimeFormats.sorted().joinToString(","),
            "minSize" to minSizeBytes?.toString().orEmpty(),
            "maxSize" to maxSizeBytes?.toString().orEmpty(),
            "from" to dateRange?.first?.toString().orEmpty(),
            "to" to dateRange?.last?.toString().orEmpty(),
            "mp" to minResolutionMp?.toString().orEmpty(),
            "text" to hasText?.toString().orEmpty(),
            "faces" to hasFaces?.toString().orEmpty(),
            "phone" to hasPhoneNumber?.toString().orEmpty(),
            "favorite" to isFavorite?.toString().orEmpty()
        ).entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
    }

    fun decode(encoded: String): FilterConfig = runCatching {
        if (encoded.isBlank()) return FilterConfig() // legacy presets
        val values = encoded.split('&').associate {
            val pair = it.split('=', limit = 2)
            require(pair.size == 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        require(values["v"] == "1")
        require(values.keys.containsAll(setOf("types", "formats", "minSize", "maxSize", "from", "to", "mp", "text", "faces", "phone", "favorite")))
        fun optionalLong(key: String) = values.getValue(key).takeIf { it.isNotEmpty() }?.toLong()
        fun optionalBoolean(key: String) = values.getValue(key).takeIf { it.isNotEmpty() }?.toBooleanStrict()
        val from = optionalLong("from")
        val to = optionalLong("to")
        val min = optionalLong("minSize")
        val max = optionalLong("maxSize")
        val mp = values.getValue("mp").takeIf { it.isNotEmpty() }?.toFloat()
        require((from == null) == (to == null))
        require(min == null || min >= 0)
        require(max == null || max >= 0)
        require(min == null || max == null || min <= max)
        require(from == null || to == null || from <= to)
        require(mp == null || (mp.isFinite() && mp >= 0))
        FilterConfig(
            mediaTypes = values["types"]?.takeIf { it.isNotEmpty() }?.split(',')?.map { MediaType.valueOf(it) }?.toSet() ?: emptySet(),
            mimeFormats = values["formats"]?.takeIf { it.isNotEmpty() }?.split(',')?.toSet() ?: emptySet(),
            minSizeBytes = min, maxSizeBytes = max,
            dateRange = if (from != null && to != null) from..to else null,
            minResolutionMp = mp,
            hasText = optionalBoolean("text"),
            hasFaces = optionalBoolean("faces"),
            hasPhoneNumber = optionalBoolean("phone"),
            isFavorite = optionalBoolean("favorite")
        )
    }.getOrDefault(FilterConfig())
}
