package com.omnimemoria.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    
    // Add scroll behavior for a modern collapsing toolbar effect
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = "Settings", 
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ) 
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            
            item {
                SettingsGroup(title = "AI Features", icon = Icons.Outlined.AutoAwesome) {
                    AI_FEATURE_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            checked = featureStates[item.flag] == true,
                            enabled = if (item.flag == FeatureFlag.ARABIC_OCR) ocrEnabled else true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == AI_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Visual Features", icon = Icons.Outlined.Palette) {
                    VISUAL_FEATURE_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            checked = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == VISUAL_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Storage & Compression", icon = Icons.Outlined.SdStorage) {
                    COMPRESSION_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            checked = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == COMPRESSION_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Offline AI Models", icon = Icons.Outlined.ModelTraining) {
                    AI_MODEL_ITEMS.forEachIndexed { index, item ->
                        val isDownloaded = modelDownloadStates[item.modelName] == true
                        ModelDownloadItem(
                            title = item.title,
                            icon = item.icon,
                            downloaded = isDownloaded,
                            onDownloadClick = { activeDownloadModel = item.modelName },
                            isLast = index == AI_MODEL_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Security & Privacy", icon = Icons.Outlined.Security) {
                    SECURITY_FEATURE_ITEMS.forEachIndexed { index, item ->
                        FeatureToggleItem(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            checked = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast = index == SECURITY_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "About", icon = Icons.Outlined.Info) {
                    AboutItems()
                }
            }
            
            item { Spacer(modifier = Modifier.height(60.dp)) }
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
private fun SettingsGroup(
    title: String, 
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
    isLast: Boolean = false
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = contentAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ModelDownloadItem(
    title: String,
    icon: ImageVector,
    downloaded: Boolean,
    onDownloadClick: () -> Unit,
    isLast: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            
            if (downloaded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ready", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDownloadClick,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Get", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App Version", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(versionName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLibrariesDialog = true }
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Open Source Licenses", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
    val icon: ImageVector,
    val subtitle: String? = null
)

private data class AiModelItem(
    val title: String,
    val modelName: String,
    val icon: ImageVector
)

private val AI_FEATURE_ITEMS = listOf(
    FeatureItem("Text Extraction (OCR)", FeatureFlag.OCR, Icons.Outlined.DocumentScanner),
    FeatureItem("Arabic OCR (Tesseract)", FeatureFlag.ARABIC_OCR, Icons.Outlined.Translate),
    FeatureItem("Image Classification", FeatureFlag.ML_LABELS, Icons.Outlined.Category),
    FeatureItem("Face Detection", FeatureFlag.FACE_DETECTION, Icons.Outlined.Face),
    FeatureItem("Embeddings", FeatureFlag.EMBEDDINGS, Icons.Outlined.Polyline),
    FeatureItem("Semantic Search (RAG)", FeatureFlag.RAG_SEARCH, Icons.Outlined.TravelExplore, "Requires embeddings"),
    FeatureItem("Smart Filters", FeatureFlag.SMART_FILTERS, Icons.Outlined.FilterList, "Uses AI-generated metadata")
)

private val VISUAL_FEATURE_ITEMS = listOf(
    FeatureItem("Color Explorer", FeatureFlag.PIXEL_PALETTE, Icons.Outlined.ColorLens),
    FeatureItem("Duplicate Photo Detection", FeatureFlag.PHOTO_DNA, Icons.Outlined.Difference),
    FeatureItem("Vibe Albums", FeatureFlag.VIBE_ALBUMS, Icons.Outlined.AutoAwesomeMosaic),
    FeatureItem("Temporal Wave", FeatureFlag.TEMPORAL_WAVE, Icons.Outlined.Waves),
    FeatureItem("Memory Stats", FeatureFlag.MEMORIA_STATS, Icons.Outlined.Analytics),
    FeatureItem("Ultra HDR", FeatureFlag.ULTRA_HDR, Icons.Outlined.HdrOn)
)

private val COMPRESSION_ITEMS = listOf(
    FeatureItem("Image Compression", FeatureFlag.SMART_COMPRESSION, Icons.Outlined.Image),
    FeatureItem("Video Compression", FeatureFlag.VIDEO_COMPRESSION, Icons.Outlined.VideoFile)
)

private val AI_MODEL_ITEMS = listOf(
    AiModelItem("Tesseract Arabic (~30MB)", ModelDownloadWorker.MODEL_TESSERACT_ARA, Icons.Outlined.Language),
    AiModelItem("Embedding Model (~50MB)", ModelDownloadWorker.MODEL_MEDIAPIPE_EMBEDDER, Icons.Outlined.Psychology)
)

private val SECURITY_FEATURE_ITEMS = listOf(
    FeatureItem("Encrypted Vault", FeatureFlag.VAULT, Icons.Outlined.Lock),
    FeatureItem("Silent Photos (EXIF)", FeatureFlag.SILENT_STORY, Icons.Outlined.VisibilityOff),
    FeatureItem("Memory Map", FeatureFlag.MEMORY_MAP, Icons.Outlined.Map)
)
