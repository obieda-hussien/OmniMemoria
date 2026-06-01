package com.omnimemoria.ui.trash

import android.app.Activity
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omnimemoria.data.local.db.TrashItem
import kotlinx.coroutines.launch

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (trashItems.isEmpty() && !isLoading) {
            TrashEmptyState(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top    = 110.dp,
                    bottom = 40.dp,
                    start  = 16.dp,
                    end    = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { TrashInfoBanner() }

                items(trashItems, key = { it.id }) { item ->
                    TrashItemCard(
                        item      = item,
                        onRestore = { viewModel.restore(item) },
                        onDelete  = { itemToDelete = item }
                    )
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }

        // Scrim فوق المحتوى
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f   to MaterialTheme.colorScheme.background,
                        0.85f to MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        1f   to Color.Transparent
                    )
                )
        )

        TrashTopBar(
            count        = trashCount,
            isLoading    = isLoading,
            onBack       = onBack,
            onEmptyTrash = { showEmptyConfirm = true },
            modifier     = Modifier.align(Alignment.TopCenter)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }

    // ── Dialog تفريغ الـ Trash ────────────────────────────────────────────────
    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            containerColor   = Color(0xFF1E1C30),
            title = {
                Text(
                    "Empty Recycle Bin?",
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "All $trashCount item${if (trashCount != 1) "s" else ""} will be permanently deleted and cannot be recovered.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmptyConfirm = false; viewModel.emptyTrash() }) {
                    Text("Empty Trash", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Dialog حذف نهائي لعنصر واحد ──────────────────────────────────────────
    itemToDelete?.let { item ->
        val name = item.originalPath.substringAfterLast('/').ifBlank { item.originalPath }
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor   = Color(0xFF1E1C30),
            title = {
                Text(
                    "Delete Permanently?",
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "\"$name\" will be permanently deleted and cannot be recovered.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
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

// ── Top Bar ────────────────────────────────────────────────────────────────────

@Composable
private fun TrashTopBar(
    count:        Int,
    isLoading:    Boolean,
    onBack:       () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier:     Modifier = Modifier
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                tint     = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Recycle Bin",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (count > 0) {
                Text(
                    "$count item${if (count != 1) "s" else ""} · auto-deleted after 30 days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = count > 0 && !isLoading) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.13f))
                    .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onEmptyTrash)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.DeleteSweep, null,
                    tint     = Color(0xFFFF6B6B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Empty",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Info Banner ────────────────────────────────────────────────────────────────

@Composable
private fun TrashInfoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Info, null,
            tint     = Color(0xFF8B7FF5).copy(alpha = 0.7f),
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Items are automatically deleted after 30 days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Trash Item Card ────────────────────────────────────────────────────────────

@Composable
private fun TrashItemCard(
    item:      TrashItem,
    onRestore: () -> Unit,
    onDelete:  () -> Unit
) {
    val expiryColor = when {
        item.daysUntilExpiry <= 3 -> Color(0xFFFF6B6B)
        item.daysUntilExpiry <= 7 -> Color(0xFFFBC02D)
        else                      -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isVideo = item.mediaType.startsWith("video/", ignoreCase = true)
    val name    = item.originalPath.substringAfterLast('/').ifBlank { item.originalPath }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
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
                model              = item.contentUri,
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

// ── Empty State ────────────────────────────────────────────────────────────────

@Composable
private fun TrashEmptyState(modifier: Modifier = Modifier) {
    val floatY by rememberInfiniteTransition(label = "float")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = -8f,
            animationSpec = infiniteRepeatable(
                tween(2200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "float_y"
        )

    Column(
        modifier            = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .offset(y = floatY.dp)
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DeleteOutline, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Recycle Bin is empty",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Deleted photos and videos will appear here for 30 days before being permanently removed.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
