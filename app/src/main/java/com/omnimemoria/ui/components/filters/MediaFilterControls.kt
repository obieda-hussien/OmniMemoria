package com.omnimemoria.ui.components.filters

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnimemoria.domain.model.*

@Composable
internal fun MediaFilterControls(filter: FilterConfig, onChange: (FilterConfig) -> Unit) {
    Text("Media type", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaType.entries.forEach { type -> FilterChip(
            selected = type in filter.mediaTypes,
            onClick = { onChange(filter.copy(mediaTypes = if (type in filter.mediaTypes) filter.mediaTypes - type else filter.mediaTypes + type)) },
            label = { Text(type.name) }
        ) }
    }
    Text("Format", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("image/jpeg" to "JPEG", "image/png" to "PNG", "image/webp" to "WebP", "image/heic" to "HEIC", "image/avif" to "AVIF", "video/mp4" to "MP4").forEach { (mime, label) ->
            FilterChip(selected = mime in filter.mimeFormats, onClick = {
                onChange(filter.copy(mimeFormats = if (mime in filter.mimeFormats) filter.mimeFormats - mime else filter.mimeFormats + mime))
            }, label = { Text(label) })
        }
    }
    Text("File size", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Any", 1 to "Under 1 MB", 2 to "1–10 MB", 3 to "Over 10 MB").forEach { (index, label) ->
            val range = when (index) { 1 -> null to 1_048_575L; 2 -> 1_048_576L to 10_485_760L; 3 -> 10_485_761L to null; else -> null to null }
            FilterChip(selected = filter.minSizeBytes == range.first && filter.maxSizeBytes == range.second,
                onClick = { onChange(filter.copy(minSizeBytes = range.first, maxSizeBytes = range.second)) }, label = { Text(label) })
        }
    }
    Text("Date", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Any", 7 to "Last 7 days", 30 to "Last 30 days", 365 to "Last year").forEach { (days, label) ->
            FilterChip(selected = if (days == 0) filter.dateRange == null else filter.dateRange?.let { (it.last - it.first) / 86_400_000L == days.toLong() } == true,
                onClick = { val now = System.currentTimeMillis(); onChange(filter.copy(dateRange = if (days == 0) null else (now - days * 86_400_000L)..now)) }, label = { Text(label) })
        }
    }
    Text("Minimum resolution", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, 2f, 8f, 12f).forEach { mp -> FilterChip(selected = filter.minResolutionMp == mp,
            onClick = { onChange(filter.copy(minResolutionMp = mp)) }, label = { Text(mp?.let { "${it.toInt()} MP" } ?: "Any") }) }
    }
    PresenceFilter("Favorites", filter.isFavorite) { onChange(filter.copy(isFavorite = it)) }
    PresenceFilter("Detected text", filter.hasText) { onChange(filter.copy(hasText = it)) }
    PresenceFilter("Detected faces", filter.hasFaces) { onChange(filter.copy(hasFaces = it)) }
    PresenceFilter("Detected phone numbers", filter.hasPhoneNumber) { onChange(filter.copy(hasPhoneNumber = it)) }
    Text("Detection filters apply to indexed media.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { onChange(FilterConfig()) }) { Text("Reset filters") }
}

@Composable
private fun PresenceFilter(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Any", true to "Yes", false to "No").forEach { (option, text) ->
            FilterChip(selected = value == option, onClick = { onChange(option) }, label = { Text(text) })
        }
    }
}
