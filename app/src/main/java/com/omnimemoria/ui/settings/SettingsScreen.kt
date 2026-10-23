package com.omnimemoria.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.data.worker.ModelDownloadWorker
import com.omnimemoria.data.worker.OnThisDayWorker
import com.omnimemoria.domain.flags.OfflineModel
import com.omnimemoria.domain.flags.FeatureFlag
import com.omnimemoria.ui.components.OmniDetailTopBar
import com.omnimemoria.ui.components.OmniSettingsGroup

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val features by viewModel.featureStates.collectAsState()
    val statuses by viewModel.modelStatuses.collectAsState()
    val indexing by viewModel.indexingState.collectAsState()
    val scanEnabled by viewModel.scanEnabled.collectAsState()
    val error by viewModel.error.collectAsState()
    var showDownload by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(OnThisDayWorker.isEnabled(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminders = granted
        if (granted) OnThisDayWorker.setEnabled(context, true)
    }
    fun updateReminders(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else { OnThisDayWorker.setEnabled(context, enabled); reminders = enabled }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { Surface(color = MaterialTheme.colorScheme.background) {
            OmniDetailTopBar(title = "Settings", subtitle = "Features & preferences", onBack = onBack)
        } },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("settings-list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                OmniSettingsGroup("Photo analysis", Icons.Outlined.AutoAwesome) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Runs on your device", style = MaterialTheme.typography.titleMedium)
                        Text("OCR, image labels and face detection run on your device. Arabic + English recognition uses the optional PaddleOCRv5 download (~13 MB); after installation it works offline. Gemini Nano and a Google account are not required.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(indexing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        AnalysisScanActions(scanEnabled, viewModel::scanNow, viewModel::cancelScan)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::dismissError) { Text("Dismiss") } }
                    }
                }
            }
            item {
                OmniSettingsGroup("Notifications", Icons.Outlined.NotificationsActive) {
                    FeatureToggleRow("On This Day reminders", Icons.Outlined.Notifications, reminders, ::updateReminders,
                        "Optional daily memories · notification access required", isLast = true)
                }
            }
            val groups = listOf(Triple("AI Features", Icons.Outlined.AutoAwesome, AI_FEATURE_ITEMS),
                Triple("Visual Features", Icons.Outlined.Palette, VISUAL_FEATURE_ITEMS),
                Triple("Storage & Compression", Icons.Outlined.SdStorage, COMPRESSION_ITEMS),
                Triple("Security & Privacy", Icons.Outlined.Security, SECURITY_FEATURE_ITEMS))
            groups.forEach { (title, icon, items) ->
                item {
                    OmniSettingsGroup(title, icon) {
                        items.forEachIndexed { index, item ->
                            val available = item.flag !in setOf(FeatureFlag.EMBEDDINGS, FeatureFlag.RAG_SEARCH)
                            FeatureToggleRow(item.title, item.icon, available && features[item.flag] == true,
                                { enabled -> viewModel.setEnabled(item.flag, enabled) },
                                subtitle = item.subtitle, enabled = available, isLast = index == items.lastIndex)
                        }
                    }
                }
                if (title == "AI Features") item {
                    OmniSettingsGroup("Offline models", Icons.Outlined.ModelTraining) {
                        AI_MODEL_ITEMS.forEachIndexed { index, model ->
                            ModelDownloadRow(model.title, model.icon,
                                statuses[model.modelName] ?: if (model.modelName == OfflineModel.EMBEDDING_GEMMA.id) ModelStatus(ModelPhase.PLANNED, "Not available in this version") else ModelStatus(ModelPhase.NOT_INSTALLED, "Checking installation"),
                                onDownload = { showDownload = true }, onCancel = { viewModel.requestModel(model.modelName, false) },
                                isLast = index == AI_MODEL_ITEMS.lastIndex)
                        }
                    }
                }
            }
            item { OmniSettingsGroup("About", Icons.Outlined.Info) {
                val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
                ListItem(headlineContent = { Text("OmniMemoria") }, supportingContent = { Text("Version $version · local photo library") })
            } }
        }
    }
    if (showDownload) DownloadModelDialog(OfflineModel.TESSERACT_ARABIC.id, { showDownload = false }, { viewModel.requestModel(it) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisScanActions(enabled: Boolean, onScan: () -> Unit, onCancel: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onScan, enabled = enabled) { Text("Scan library now") }
        TextButton(onClick = onCancel) { Text("Stop scan") }
    }
}

@Composable
private fun FeatureToggleRow(title: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit,
    subtitle: String? = null, enabled: Boolean = true, isLast: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        if (!isLast) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelDownloadRow(title: String, icon: ImageVector, status: ModelStatus, onDownload: () -> Unit,
    onCancel: () -> Unit, isLast: Boolean = false) {
    Column {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(status.message, style = MaterialTheme.typography.bodyMedium,
                color = if (status.phase == ModelPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.phase == ModelPhase.DOWNLOADING) {
                LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${status.percent}%", style = MaterialTheme.typography.labelMedium)
            }
            if (status.canUpgrade || status.phase !in setOf(ModelPhase.INSTALLED, ModelPhase.PLANNED)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDownload) { Text(when {
                        status.canUpgrade -> "Upgrade · ${com.omnimemoria.data.intelligence.PaddleOcrModelStore.SIZE_LABEL}"
                        status.phase in setOf(ModelPhase.FAILED, ModelPhase.RETRYING) -> "Retry now"
                        else -> "Download ${com.omnimemoria.data.intelligence.PaddleOcrModelStore.SIZE_LABEL}"
                    }) }
                    if (status.phase in setOf(ModelPhase.DOWNLOADING, ModelPhase.QUEUED, ModelPhase.RETRYING)) TextButton(onClick = onCancel) { Text("Cancel download") }
                }
            }
        }
        if (!isLast) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

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
    FeatureItem("Arabic + English OCR",        FeatureFlag.ARABIC_OCR,     Icons.Outlined.Translate,      "PP-OCRv5 mixed-script recognition · optional ~13 MB model below"),
    FeatureItem("Image Classification",     FeatureFlag.ML_LABELS,      Icons.Outlined.Category),
    FeatureItem("Face Detection",           FeatureFlag.FACE_DETECTION,  Icons.Outlined.Face),
    FeatureItem("Embeddings",              FeatureFlag.EMBEDDINGS,      Icons.Outlined.Polyline, "Not available in this version"),
    FeatureItem("Semantic Search (RAG)",    FeatureFlag.RAG_SEARCH,      Icons.Outlined.TravelExplore,  "Not available in this version"),
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
    AiModelItem("Arabic + English OCR (PaddleOCRv5)", ModelDownloadWorker.MODEL_TESSERACT_ARA,    Icons.Outlined.Language),
    AiModelItem("EmbeddingGemma",  ModelDownloadWorker.MODEL_MEDIAPIPE_EMBEDDER, Icons.Outlined.Psychology)
)

private val SECURITY_FEATURE_ITEMS = listOf(
    FeatureItem("Vault PIN Preview",       FeatureFlag.VAULT,          Icons.Outlined.Lock, "PIN demo only · encrypted photo storage unavailable"),
    FeatureItem("Hidden Photo Notes",       FeatureFlag.SILENT_STORY,   Icons.Outlined.VisibilityOff),
    FeatureItem("Memory Map",               FeatureFlag.MEMORY_MAP,     Icons.Outlined.Map)
)
