package com.omnimemoria.ui.trash

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omnimemoria.data.local.db.TrashItem
import kotlinx.coroutines.launch
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnimemoria.ui.components.OmniDetailTopBar
import com.omnimemoria.ui.components.OmniEmptyState
import com.omnimemoria.ui.components.OmniInfoBanner
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val trashItems  by viewModel.trashItems.collectAsState()
    val trashCount  by viewModel.trashCount.collectAsState()
    val isLoading   by viewModel.isLoading.collectAsState()

    val snackbarHostState  = remember { SnackbarHostState() }
    val scope              = rememberCoroutineScope()
    var showEmptyConfirm   by remember { mutableStateOf(false) }
    var itemToDelete       by remember { mutableStateOf<TrashItem?>(null) }
    var previewItem        by remember { mutableStateOf<TrashItem?>(null) }
    var pendingOnConfirm   by remember { mutableStateOf<(() -> Unit)?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingOnConfirm?.invoke()
        pendingOnConfirm = null
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is TrashUiEvent.RequestMediaPermission -> {
                    pendingOnConfirm = event.onConfirmed
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    )
                }
                is TrashUiEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message  = event.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    BackHandler {
        if (previewItem != null) {
            previewItem = null
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OmniDetailTopBar(
                title    = "Recycle Bin",
                subtitle = if (trashCount > 0) "$trashCount item${if (trashCount != 1) "s" else ""} · auto-deleted after 30 days" else null,
                onBack   = onBack,
                actions  = {
                    if (trashCount > 0 && !isLoading) {
                        com.omnimemoria.ui.components.OmniActionChip(
                            label   = "Empty",
                            icon    = Icons.Outlined.DeleteSweep,
                            onClick = { showEmptyConfirm = true }
                        )
                    }
                }
            )

            if (trashItems.isEmpty() && !isLoading) {
                Box(
                    modifier         = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    OmniEmptyState(
                        icon     = Icons.Outlined.DeleteOutline,
                        title    = "Recycle Bin is empty",
                        subtitle = "Deleted photos and videos appear here for 30 days before being permanently removed.",
                        floating = true
                    )
                }
            } else {
                LazyColumn(
                    modifier      = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OmniInfoBanner(icon = Icons.Outlined.Info, text = "Items are automatically deleted after 30 days.")
                    }

                    items(
                        items = trashItems,
                        key   = { it.id }
                    ) { item ->
                        TrashItemCard(
                            item      = item,
                            onClick   = { previewItem = item },
                            onRestore = { viewModel.restore(item) },
                            onDelete  = { itemToDelete = item }
                        )
                    }

                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }

        // Preview Overlay
        AnimatedVisibility(
            visible  = previewItem != null,
            enter    = fadeIn() + scaleIn(initialScale = 0.95f),
            exit     = fadeOut() + scaleOut(targetScale = 0.95f),
            modifier = Modifier.fillMaxSize()
        ) {
            previewItem?.let { item ->
                TrashPreviewOverlay(
                    item      = item,
                    onClose   = { previewItem = null },
                    onRestore = { viewModel.restore(item); previewItem = null },
                    onDelete  = { itemToDelete = item; previewItem = null }
                )
            }
        }

        // Scrim فوق المحتوى
        if (showEmptyConfirm || itemToDelete != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        showEmptyConfirm = false
                        itemToDelete = null
                    }
            )
        }

        // Loading
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF8B7FF5))
            }
        }

        // Empty Confirm
        AnimatedVisibility(
            visible  = showEmptyConfirm,
            enter    = slideInVertically { (it / 2f).toInt() } + fadeIn(),
            exit     = slideOutVertically { (it / 2f).toInt() } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AlertDialog(
                onDismissRequest = { showEmptyConfirm = false },
                containerColor   = Color(0xFF1E1C30),
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Empty Recycle Bin?", fontWeight = FontWeight.Bold) },
                text = { Text("All items will be permanently deleted. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = { showEmptyConfirm = false; viewModel.emptyTrash() }) {
                        Text("Empty", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirm = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // Delete Item Confirm
        AnimatedVisibility(
            visible  = itemToDelete != null,
            enter    = slideInVertically { (it / 2f).toInt() } + fadeIn(),
            exit     = slideOutVertically { (it / 2f).toInt() } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            itemToDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    containerColor   = Color(0xFF1E1C30),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = { Text("Delete permanently?", fontWeight = FontWeight.Bold) },
                    text = { Text("This item will be permanently deleted and cannot be recovered.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.permanentlyDelete(item); itemToDelete = null }) {
                            Text("Delete", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDelete = null }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}

// ── Preview Overlay ────────────────────────────────────────────────────────────

@Composable
private fun TrashPreviewOverlay(
    item: TrashItem,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isVideo = item.mediaType.startsWith("video/", ignoreCase = true)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            // For video we just show a thumbnail and a message since we can't easily play it
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model              = item.contentUri,
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PlayCircle, null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Restore this video to play it",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            ZoomableAsyncImage(
                model = item.contentUri,
                contentDescription = "Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onRestore)
                    .padding(12.dp)
            ) {
                Icon(Icons.Outlined.Restore, "Restore", tint = Color(0xFF8B7FF5))
                Spacer(Modifier.height(4.dp))
                Text("Restore", color = Color(0xFF8B7FF5), style = MaterialTheme.typography.labelMedium)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDelete)
                    .padding(12.dp)
            ) {
                Icon(Icons.Outlined.DeleteForever, "Delete", tint = Color(0xFFFF6B6B))
                Spacer(Modifier.height(4.dp))
                Text("Delete", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}



// ── Trash Item Card ────────────────────────────────────────────────────────────

@Composable
private fun TrashItemCard(
    item:      TrashItem,
    onClick:   () -> Unit,
    onRestore: () -> Unit,
    onDelete:  () -> Unit
) {
    val expiryColor = when {
        item.daysUntilExpiry <= 3 -> Color(0xFFFF6B6B)
        item.daysUntilExpiry <= 7 -> Color(0xFFFBC02D)
        else                      -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isVideo = item.mediaType.startsWith("video/", ignoreCase = true)
    val name    = item.originalPath.substringAfterLast("/").ifBlank { item.originalPath }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model              = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.contentUri).size(Size(256, 256)).build(),
                contentDescription = name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PlayCircle, null,
                        tint     = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onBackground,
                maxLines   = 1
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Timer, null,
                    tint     = expiryColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (item.daysUntilExpiry) {
                        0    -> "Expires today"
                        1    -> "Expires tomorrow"
                        else -> "Expires in ${item.daysUntilExpiry} days"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = expiryColor
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Restore
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF8B7FF5).copy(alpha = 0.12f))
                .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .clickable(onClick = onRestore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Restore, "Restore",
                tint     = Color(0xFF8B7FF5),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        // Delete permanently
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.10f))
                .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DeleteForever, "Delete permanently",
                tint     = Color(0xFFFF6B6B),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

