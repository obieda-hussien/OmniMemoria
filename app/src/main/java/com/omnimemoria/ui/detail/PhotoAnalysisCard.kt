package com.omnimemoria.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.data.worker.WorkManagerScheduler
import com.omnimemoria.domain.flags.FeatureFlagManager
import com.omnimemoria.domain.intelligence.AnalysisPolicy
import com.omnimemoria.domain.intelligence.EntityExtractor
import com.omnimemoria.domain.intelligence.OcrText
import com.omnimemoria.data.intelligence.OcrRegions
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhotoAnalysisViewModel @Inject constructor(
    dao: PhotoIntelligenceDao,
    flags: FeatureFlagManager,
    private val scheduler: WorkManagerScheduler,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val photoId = MutableStateFlow(-1L)
    val analysis = photoId.flatMapLatest { if (it > 0) dao.observeById(it) else flowOf(null) }
        .catch { _error.value = "Unable to load analysis. Reopen the photo and try again."; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val enabled = flags.featureStates.map { AnalysisPolicy.required(it) != 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val busy = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkManagerScheduler.UNIQUE_IMMEDIATE_WORK_NAME)
        .map { jobs -> jobs.any { !it.state.isFinished } }
        .catch { _error.value = "Unable to read analysis progress. Please try again."; emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun schedule(id: Long) { if (id > 0 && enabled.value) scheduler.scheduleImmediateIndex(listOf(id)) }
}

@Composable
internal fun PhotoAnalysisCard(photo: MediaPhoto, viewModel: PhotoAnalysisViewModel = hiltViewModel()) {
    LaunchedEffect(photo.id) { viewModel.photoId.value = photo.id }
    val analysis by viewModel.analysis.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val row = analysis?.takeIf { it.id == photo.id && !it.isVaultItem }
    if (photo.id <= 0 || photo.mimeType.startsWith("video/")) return
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Text("On-device analysis", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    TextButton(enabled = enabled && !busy, onClick = { viewModel.schedule(photo.id) }) {
        Text(if (busy) "Analysis queued" else "Analyze this photo")
    }
    if (!enabled) Text("Enable photo analysis features in Settings.", style = MaterialTheme.typography.bodySmall)
    if (row == null) {
        Text("This photo has not been analyzed yet.", style = MaterialTheme.typography.bodySmall)
        return
    }
    if (row.indexError.isNotBlank()) Text(row.indexError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (row.analysisVersion < AnalysisPolicy.VERSION) Text("Analysis from an older engine · analyze again to improve text and number detection.",
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    val regions = remember(row.ocrBoxes) { if (row.ocrBoxes.length <= 1_000_000) runCatching { JSONObject(row.ocrBoxes) }.getOrNull() else null }
    val lines = remember(row.ocrBoxes) { OcrText.resolve(OcrRegions.read(row.ocrBoxes)) }
    if (regions != null && lines.isNotEmpty()) {
        val width = regions.optInt("width", 1).coerceIn(1, 100_000)
        val height = regions.optInt("height", 1).coerceIn(1, 100_000)
        Box(Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(width.toFloat()/height)) {
            AsyncImage(model = photo.uri, contentDescription = "Detected text regions", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val scale = minOf(size.width / width, size.height / height)
                val fitWidth = width * scale; val fitHeight = height * scale
                val offsetX = (size.width - fitWidth) / 2; val offsetY = (size.height - fitHeight) / 2
                for (box in lines) {
                    val left = offsetX + box.left * fitWidth
                    val top = offsetY + box.top * fitHeight
                    val right = offsetX + box.right * fitWidth
                    val bottom = offsetY + box.bottom * fitHeight
                    drawRect(Color(0xFF75D9B3), Offset(left, top), Size((right-left).coerceAtLeast(0f), (bottom-top).coerceAtLeast(0f)), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
    val displayText = remember(row.rawText, row.ocrBoxes, row.analysisVersion) {
        if (lines.isNotEmpty() && row.analysisVersion >= AnalysisPolicy.VERSION) OcrText.text(lines) else OcrText.normalize(row.rawText)
    }
    if (displayText.isNotBlank()) {
        Text("Detected text", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        SelectionContainer { Text(displayText.take(20_000), style = MaterialTheme.typography.bodyMedium) }
        EntityExtractor.phones(displayText).forEach { number -> TextButton(onClick = {
            openAnalysisAction(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        }) { Text("Call $number") } }
        EntityExtractor.emails(displayText).forEach { email -> TextButton(onClick = {
            openAnalysisAction(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)))
        }) { Text("Email $email") } }
        EntityExtractor.urls(displayText).forEach { url -> TextButton(onClick = {
            openAnalysisAction(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }) { Text(url.take(100)) } }
        val amounts = remember(displayText) { EntityExtractor.amounts(displayText) }
        if (amounts.isNotEmpty()) {
            Text("Detected amounts · verify against the photo", style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Column { amounts.forEach { Text("${it.value} ${it.currency}", style = MaterialTheme.typography.bodyMedium) } } }
        }
    } else if (row.completedMask and 3 != 0) Text("No readable text found.", style = MaterialTheme.typography.bodySmall)
    if (row.labels.isNotBlank()) Text("Labels: ${row.labels}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    if (row.completedMask and AnalysisPolicy.FACES != 0) Text(if (row.hasFaces) "Faces detected" else "No faces detected", style = MaterialTheme.typography.bodySmall)
    if (row.dominantColorHex.isNotBlank()) Text("Colors: ${row.dominantColorHex} ${row.secondaryColorHex}", style = MaterialTheme.typography.bodySmall)
}

private fun openAnalysisAction(context: Context, intent: Intent) {
    try { context.startActivity(intent) } catch (error: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app can handle this action", android.widget.Toast.LENGTH_SHORT).show()
    }
}
