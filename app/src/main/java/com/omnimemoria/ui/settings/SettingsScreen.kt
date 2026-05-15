package com.omnimemoria.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.data.worker.ModelDownloadWorker
import com.omnimemoria.domain.flags.FeatureFlag

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val featureStates by viewModel.featureStates.collectAsState()
    val modelDownloadStates by viewModel.modelDownloadStates.collectAsState()
    val ocrEnabled = featureStates[FeatureFlag.OCR] == true
    var activeDownloadModel by remember { mutableStateOf<String?>(null) }

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

        item {
            SectionHeader(title = "📥 AI Models")
        }
        items(AI_MODEL_ITEMS) { item ->
            val isDownloaded = modelDownloadStates[item.modelName] == true
            ModelDownloadItem(
                title = item.title,
                downloaded = isDownloaded,
                onDownloadClick = { activeDownloadModel = item.modelName }
            )
        }

        item {
            SectionHeader(title = "🔒 Security")
        }
        items(SECURITY_FEATURE_ITEMS) { item ->
            FeatureToggleItem(
                title = item.title,
                checked = featureStates[item.flag] == true,
                onCheckedChange = { viewModel.toggle(item.flag) }
            )
        }

        item {
            SectionHeader(title = "ℹ️ About the App")
        }
        item {
            AboutItem()
        }
    }

    activeDownloadModel?.let { modelName ->
        DownloadModelDialog(
            modelName = modelName,
            onDismiss = { activeDownloadModel = null }
        )
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
private fun ModelDownloadItem(
    title: String,
    downloaded: Boolean,
    onDownloadClick: () -> Unit
) {
    Column {
        ListItem(
            headlineContent = { Text(text = title) },
            trailingContent = {
                if (downloaded) {
                    Text(text = "✅ Downloaded")
                } else {
                    Button(onClick = onDownloadClick) {
                        Text(text = "Download")
                    }
                }
            }
        )
        HorizontalDivider()
    }
}

@Composable
private fun AboutItem() {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        }.getOrDefault("Unknown")
    }
    var showLibrariesDialog by remember { mutableStateOf(false) }

    Column {
        ListItem(
            headlineContent = { Text(text = "App version") },
            supportingContent = { Text(text = versionName) }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(text = "Open source libraries") },
            trailingContent = {
                Button(onClick = { showLibrariesDialog = true }) {
                    Text(text = "Open")
                }
            }
        )
        HorizontalDivider()
    }

    if (showLibrariesDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLibrariesDialog = false },
            title = { Text(text = "Open source libraries") },
            text = { Text(text = "Licenses screen coming soon.") },
            confirmButton = {
                TextButton(onClick = { showLibrariesDialog = false }) {
                    Text(text = "OK")
                }
            }
        )
    }
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

private data class AiModelItem(
    val title: String,
    val modelName: String
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

private val AI_MODEL_ITEMS = listOf(
    AiModelItem("Tesseract Arabic (~30MB)", ModelDownloadWorker.MODEL_TESSERACT_ARA),
    AiModelItem("Embedding Model (~50MB)", ModelDownloadWorker.MODEL_MEDIAPIPE_EMBEDDER)
)

private val SECURITY_FEATURE_ITEMS = listOf(
    FeatureItem("Encrypted Vault", FeatureFlag.VAULT),
    FeatureItem("Silent Photos (EXIF)", FeatureFlag.SILENT_STORY),
    FeatureItem("Memory Map", FeatureFlag.MEMORY_MAP)
)
