package com.omnimemoria.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.data.worker.ModelDownloadWorker
import com.omnimemoria.domain.flags.FeatureFlag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val featureStates by viewModel.featureStates.collectAsState()
    val modelDownloadStates by viewModel.modelDownloadStates.collectAsState()
    val ocrEnabled = featureStates[FeatureFlag.OCR] == true
    var activeDownloadModel by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = "Settings", 
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ) 
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp) // مسافة كبيرة بين المجموعات
        ) {
            
            item {
                SettingsGroup(title = "🧠 AI Features") {
                    AI_FEATURE_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            subtitle = item.subtitle,
                            checked = featureStates[item.flag] == true,
                            enabled = if (item.flag == FeatureFlag.ARABIC_OCR) ocrEnabled else true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == AI_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "🎨 Visual Features") {
                    VISUAL_FEATURE_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            checked = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == VISUAL_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "🗜️ Storage & Compression") {
                    COMPRESSION_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            checked = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == COMPRESSION_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "📥 Offline AI Models") {
                    AI_MODEL_ITEMS.forEachIndexed { index, item ->
                        val isDownloaded = modelDownloadStates[item.modelName] == true
                        ModelDownloadItem(
                            title = item.title,
                            downloaded = isDownloaded,
                            onDownloadClick = { activeDownloadModel = item.modelName },
                            isLast = index == AI_MODEL_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "🔒 Security & Privacy") {
                    SECURITY_FEATURE_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            checked = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == SECURITY_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "ℹ️ About") {
                    AboutItems()
                }
            }
            
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    activeDownloadModel?.let { modelName ->
        DownloadModelDialog(
            modelName = modelName,
            onDismiss = { activeDownloadModel = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun FeatureToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null, // Handled by Row click
                enabled = enabled
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun ModelDownloadItem(
    title: String,
    downloaded: Boolean,
    onDownloadClick: () -> Unit,
    isLast: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            if (downloaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ready", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4CAF50))
                }
            } else {
                FilledTonalButton(
                    onClick = onDownloadClick,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Get")
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun AboutItems() {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        }.getOrDefault("Unknown")
    }
    var showLibrariesDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("App Version", style = MaterialTheme.typography.bodyLarge)
            Text(versionName, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLibrariesDialog = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Open Source Licenses", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    if (showLibrariesDialog) {
        AlertDialog(
            onDismissRequest = { showLibrariesDialog = false },
            title = { Text(text = "Open Source Libraries") },
            text = { Text(text = "Licenses screen coming soon.") },
            confirmButton = {
                TextButton(onClick = { showLibrariesDialog = false }) {
                    Text(text = "OK")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

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
    FeatureItem("Embeddings", FeatureFlag.EMBEDDINGS),
    FeatureItem("Semantic Search (RAG)", FeatureFlag.RAG_SEARCH, "Requires embeddings"),
    FeatureItem("Smart Filters", FeatureFlag.SMART_FILTERS, "Uses AI-generated metadata")
)

private val VISUAL_FEATURE_ITEMS = listOf(
    FeatureItem("Color Explorer", FeatureFlag.PIXEL_PALETTE),
    FeatureItem("Duplicate Photo Detection", FeatureFlag.PHOTO_DNA),
    FeatureItem("Vibe Albums", FeatureFlag.VIBE_ALBUMS),
    FeatureItem("Temporal Wave", FeatureFlag.TEMPORAL_WAVE),
    FeatureItem("Memory Stats", FeatureFlag.MEMORIA_STATS),
    FeatureItem("Ultra HDR", FeatureFlag.ULTRA_HDR)
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
