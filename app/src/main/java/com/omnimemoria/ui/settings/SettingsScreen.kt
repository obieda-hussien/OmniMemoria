package com.omnimemoria.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.domain.flags.FeatureFlag

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val featureStates by viewModel.featureStates.collectAsState()
    val ocrEnabled = featureStates[FeatureFlag.OCR] == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader(title = "🧠 AI Features")
        }
        items(AI_FEATURE_ITEMS) { item ->
            FeatureToggleItem(
                title = item.title,
                subtitle = item.subtitle,
                checked = featureStates[item.flag] == true,
                enabled = if (item.flag == FeatureFlag.ARABIC_OCR) ocrEnabled else true,
                onCheckedChange = { viewModel.toggle(item.flag) }
            )
        }

        item {
            SectionHeader(title = "🎨 Visual Features")
        }
        items(VISUAL_FEATURE_ITEMS) { item ->
            FeatureToggleItem(
                title = item.title,
                checked = featureStates[item.flag] == true,
                onCheckedChange = { viewModel.toggle(item.flag) }
            )
        }

        item {
            SectionHeader(title = "🗜️ Compression")
        }
        items(COMPRESSION_ITEMS) { item ->
            FeatureToggleItem(
                title = item.title,
                checked = featureStates[item.flag] == true,
                onCheckedChange = { viewModel.toggle(item.flag) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun FeatureToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Column {
        ListItem(
            headlineContent = { Text(text = title) },
            supportingContent = subtitle?.let { { Text(text = it) } },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
        HorizontalDivider()
    }
}

private data class FeatureItem(
    val title: String,
    val flag: FeatureFlag,
    val subtitle: String? = null
)

private val AI_FEATURE_ITEMS = listOf(
    FeatureItem("Text Extraction (OCR)", FeatureFlag.OCR),
    FeatureItem("Arabic OCR (Tesseract)", FeatureFlag.ARABIC_OCR),
    FeatureItem("Image Classification (ML Kit)", FeatureFlag.ML_LABELS),
    FeatureItem("Face Detection", FeatureFlag.FACE_DETECTION),
    FeatureItem(
        title = "Semantic Search (RAG)",
        flag = FeatureFlag.RAG_SEARCH,
        subtitle = "Requires embeddings"
    )
)

private val VISUAL_FEATURE_ITEMS = listOf(
    FeatureItem("Color Explorer", FeatureFlag.PIXEL_PALETTE),
    FeatureItem("Duplicate Photo Detection", FeatureFlag.PHOTO_DNA),
    FeatureItem("Vibe Albums", FeatureFlag.VIBE_ALBUMS),
    FeatureItem("Temporal Wave", FeatureFlag.TEMPORAL_WAVE),
    FeatureItem("Memory Stats", FeatureFlag.MEMORIA_STATS)
)

private val COMPRESSION_ITEMS = listOf(
    FeatureItem("Image Compression", FeatureFlag.SMART_COMPRESSION),
    FeatureItem("Video Compression", FeatureFlag.VIDEO_COMPRESSION)
)
