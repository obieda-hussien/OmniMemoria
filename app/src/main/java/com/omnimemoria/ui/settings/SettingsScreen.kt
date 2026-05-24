package com.omnimemoria.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.data.worker.ModelDownloadWorker
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.ui.components.OmniDetailTopBar
import com.omnimemoria.ui.components.OmniSettingsGroup

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val featureStates      by viewModel.featureStates.collectAsState()
    val modelDownloadStates by viewModel.modelDownloadStates.collectAsState()
    val ocrEnabled          = featureStates[FeatureFlag.OCR] == true
    var activeDownloadModel by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle indigo gradient behind top bar only
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f   to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                        1f   to Color.Transparent
                    )
                )
        )

        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = 110.dp,   // clearance below floating top bar
                bottom = 40.dp,
                start  = 16.dp,
                end    = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            item {
                OmniSettingsGroup(title = "AI Features", icon = Icons.Outlined.AutoAwesome) {
                    AI_FEATURE_ITEMS.forEachIndexed { index, item ->
                        OmniFeatureToggleItem(
                            title           = item.title,
                            subtitle        = item.subtitle,
                            icon            = item.icon,
                            checked         = featureStates[item.flag] == true,
                            enabled         = if (item.flag == FeatureFlag.ARABIC_OCR) ocrEnabled else true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast          = index == AI_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                OmniSettingsGroup(title = "Visual Features", icon = Icons.Outlined.Palette) {
                    VISUAL_FEATURE_ITEMS.forEachIndexed { index, item ->
                        OmniFeatureToggleItem(
                            title           = item.title,
                            subtitle        = item.subtitle,
                            icon            = item.icon,
                            checked         = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast          = index == VISUAL_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                OmniSettingsGroup(title = "Storage & Compression", icon = Icons.Outlined.SdStorage) {
                    COMPRESSION_ITEMS.forEachIndexed { index, item ->
                        OmniFeatureToggleItem(
                            title           = item.title,
                            subtitle        = item.subtitle,
                            icon            = item.icon,
                            checked         = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast          = index == COMPRESSION_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                OmniSettingsGroup(title = "Offline AI Models", icon = Icons.Outlined.ModelTraining) {
                    AI_MODEL_ITEMS.forEachIndexed { index, item ->
                        OmniModelDownloadItem(
                            title          = item.title,
                            icon           = item.icon,
                            downloaded     = modelDownloadStates[item.modelName] == true,
                            onDownloadClick = { activeDownloadModel = item.modelName },
                            isLast         = index == AI_MODEL_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                OmniSettingsGroup(title = "Security & Privacy", icon = Icons.Outlined.Security) {
                    SECURITY_FEATURE_ITEMS.forEachIndexed { index, item ->
                        OmniFeatureToggleItem(
                            title           = item.title,
                            subtitle        = item.subtitle,
                            icon            = item.icon,
                            checked         = featureStates[item.flag] == true,
                            onCheckedChange = { viewModel.toggle(item.flag) },
                            isLast          = index == SECURITY_FEATURE_ITEMS.lastIndex
                        )
                    }
                }
            }

            item {
                OmniSettingsGroup(title = "About", icon = Icons.Outlined.Info) {
                    OmniAboutItems()
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        // Floating top bar — same language as every other secondary screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.background,
                        0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        1.0f to Color.Transparent
                    )
                )
        )

        OmniDetailTopBar(
            title    = "Settings",
            subtitle = "Features & preferences",
            onBack   = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    activeDownloadModel?.let { modelName ->
        DownloadModelDialog(modelName = modelName, onDismiss = { activeDownloadModel = null })
    }
}

// ── Feature toggle row ─────────────────────────────────────────────────────────

@Composable
private fun OmniFeatureToggleItem(
    title:           String,
    icon:            ImageVector,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier:        Modifier = Modifier,
    enabled:         Boolean  = true,
    subtitle:        String?  = null,
    isLast:          Boolean  = false
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon pill
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f * contentAlpha)
                        else
                            Color(0xFF1E1C30).copy(alpha = contentAlpha)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = if (checked)
                        MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (subtitle != null) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Switch(
                checked         = checked,
                onCheckedChange = null,
                enabled         = enabled,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor  = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor  = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = Color(0xFF2A2840)
                )
            )
        }

        if (!isLast) {
            HorizontalDivider(
                modifier  = Modifier.padding(start = 70.dp, end = 18.dp),
                thickness = 0.5.dp,
                color     = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

// ── Model download row ────────────────────────────────────────────────────────

@Composable
private fun OmniModelDownloadItem(
    title:          String,
    icon:           ImageVector,
    downloaded:     Boolean,
    onDownloadClick: () -> Unit,
    isLast:         Boolean = false
) {
    Column {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1C30)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f)
            )

            if (downloaded) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A3A1A))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        tint     = Color(0xFF4CAF50),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Ready",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onDownloadClick)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            null,
                            tint     = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Get",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!isLast) {
            HorizontalDivider(
                modifier  = Modifier.padding(start = 70.dp, end = 18.dp),
                thickness = 0.5.dp,
                color     = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

// ── About section ──────────────────────────────────────────────────────────────

@Composable
private fun OmniAboutItems() {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    var showLibrariesDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("App Version", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text       = versionName,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.bodyLarge
            )
        }

        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 18.dp),
            thickness = 0.5.dp,
            color     = Color.White.copy(alpha = 0.05f)
        )

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { showLibrariesDialog = true }
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Open Source Licenses", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(18.dp)
            )
        }
    }

    if (showLibrariesDialog) {
        AlertDialog(
            onDismissRequest = { showLibrariesDialog = false },
            title            = { Text("Open Source Libraries") },
            text             = { Text("License viewer coming soon.") },
            confirmButton    = {
                TextButton(onClick = { showLibrariesDialog = false }) { Text("OK") }
            }
        )
    }
}

// ── Data models ────────────────────────────────────────────────────────────────

private data class FeatureItem(
    val title:    String,
    val flag:     FeatureFlag,
    val icon:     ImageVector,
    val subtitle: String? = null
)

private data class AiModelItem(
    val title:     String,
    val modelName: String,
    val icon:      ImageVector
)

private val AI_FEATURE_ITEMS = listOf(
    FeatureItem("Text Extraction (OCR)",    FeatureFlag.OCR,            Icons.Outlined.DocumentScanner),
    FeatureItem("Arabic OCR (Tesseract)",   FeatureFlag.ARABIC_OCR,     Icons.Outlined.Translate,      "Requires OCR to be enabled"),
    FeatureItem("Image Classification",     FeatureFlag.ML_LABELS,      Icons.Outlined.Category),
    FeatureItem("Face Detection",           FeatureFlag.FACE_DETECTION,  Icons.Outlined.Face),
    FeatureItem("Embeddings",              FeatureFlag.EMBEDDINGS,      Icons.Outlined.Polyline),
    FeatureItem("Semantic Search (RAG)",    FeatureFlag.RAG_SEARCH,      Icons.Outlined.TravelExplore,  "Requires Embeddings"),
    FeatureItem("Smart Content Filters",    FeatureFlag.SMART_FILTERS,   Icons.Outlined.FilterList,     "Uses AI-indexed metadata")
)

private val VISUAL_FEATURE_ITEMS = listOf(
    FeatureItem("Color Explorer",           FeatureFlag.PIXEL_PALETTE,   Icons.Outlined.ColorLens),
    FeatureItem("Duplicate Detection",      FeatureFlag.PHOTO_DNA,       Icons.Outlined.Difference),
    FeatureItem("Vibe Albums",              FeatureFlag.VIBE_ALBUMS,     Icons.Outlined.AutoAwesomeMosaic),
    FeatureItem("Temporal Wave",            FeatureFlag.TEMPORAL_WAVE,   Icons.Outlined.Waves),
    FeatureItem("Memory Stats",             FeatureFlag.MEMORIA_STATS,   Icons.Outlined.Analytics),
    FeatureItem("Ultra HDR Viewer",         FeatureFlag.ULTRA_HDR,       Icons.Outlined.HdrOn)
)

private val COMPRESSION_ITEMS = listOf(
    FeatureItem("Smart Image Compression",  FeatureFlag.SMART_COMPRESSION, Icons.Outlined.Image),
    FeatureItem("Video Compression",        FeatureFlag.VIDEO_COMPRESSION, Icons.Outlined.VideoFile)
)

private val AI_MODEL_ITEMS = listOf(
    AiModelItem("Tesseract Arabic (~30 MB)", ModelDownloadWorker.MODEL_TESSERACT_ARA,    Icons.Outlined.Language),
    AiModelItem("Embedding Model (~50 MB)",  ModelDownloadWorker.MODEL_MEDIAPIPE_EMBEDDER, Icons.Outlined.Psychology)
)

private val SECURITY_FEATURE_ITEMS = listOf(
    FeatureItem("Encrypted Vault",          FeatureFlag.VAULT,          Icons.Outlined.Lock),
    FeatureItem("Hidden Photo Notes",       FeatureFlag.SILENT_STORY,   Icons.Outlined.VisibilityOff),
    FeatureItem("Memory Map",               FeatureFlag.MEMORY_MAP,     Icons.Outlined.Map)
)
