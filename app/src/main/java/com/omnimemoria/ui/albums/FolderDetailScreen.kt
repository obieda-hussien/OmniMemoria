package com.omnimemoria.ui.albums

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.components.OmniActionChip
import com.omnimemoria.ui.components.OmniDetailTopBar
import com.omnimemoria.ui.components.OmniSelectionBar
import com.omnimemoria.ui.components.OmniSurface
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.detail.photosBoundsTransform
import com.omnimemoria.ui.photoSharedKey
import com.omnimemoria.ui.theme.OmniSheetContainerColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class)
@Composable
fun FolderDetailScreen(
    onPhotoClick: (Long) -> Unit,
    onBack:       () -> Unit,
    viewModel:    FolderDetailViewModel = hiltViewModel()
) {
    val haptic           = LocalHapticFeedback.current
    val photos           = viewModel.photos.collectAsLazyPagingItems()
    val folder           by viewModel.folder.collectAsState()
    val selectedIds      by viewModel.selectedIds.collectAsState()
    val isSelecting       = selectedIds.isNotEmpty()
    val sortConfig       by viewModel.sortConfig.collectAsState()
    var showSortSheet    by remember { mutableStateOf(false) }


    if (isSelecting) {
        BackHandler {
            viewModel.clearSelection()
        }
    }

    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val gridState               = rememberLazyGridState()

    // ── Delete / events wiring ─────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    var pendingOnConfirm  by remember { mutableStateOf<(() -> Unit)?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingOnConfirm?.invoke()
        pendingOnConfirm = null
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is FolderDetailUiEvent.RequestMediaPermission -> {
                    pendingOnConfirm = event.onConfirmed
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    )
                }
                is FolderDetailUiEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message  = event.message,
                            duration = SnackbarDuration.Long
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
        // Atmospheric blurred banner
        folder?.let { f ->
            AsyncImage(
                model              = f.coverUri,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .blur(40.dp)
                    .scale(1.2f)
                    .alpha(0.28f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        LazyVerticalGrid(
            state                 = gridState,
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(
                top    = 130.dp,
                bottom = 140.dp,
                start  = 6.dp,
                end    = 6.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FolderHeroCard(folder = folder, photoCount = photos.itemCount)
            }

            if (photos.loadState.refresh is LoadState.Loading) {
                items(24) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                }
            } else {
                items(
                    count = photos.itemCount,
                    key   = { i -> photos[i]?.id ?: "p_$i" }
                ) { index ->
                    photos[index]?.let { photo ->
                        com.omnimemoria.ui.gallery.PhotoCell(
                            uri                     = photo.uri.toString(),
                            photoId                 = photo.id,
                            isVideo                 = photo.mimeType.startsWith("video/", ignoreCase = true),
                            isSelected              = photo.id in selectedIds,
                            isSelecting             = isSelecting,
                            isFavorite              = false,
                            sharedTransitionScope   = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick                 = {
                                if (isSelecting) viewModel.toggleSelection(photo.id)
                                else { viewModel.prepareForNavigation(photo); onPhotoClick(photo.id) }
                            },
                            onLongClick             = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleSelection(photo.id)
                            }
                        )
                    } ?: ShimmerBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // Top bar scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        0.7f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        1.0f to Color.Transparent
                    )
                )
        )

        OmniDetailTopBar(
            title    = folder?.name?.ifBlank { "Album" } ?: "Album",
            subtitle = if (photos.itemCount > 0) "${photos.itemCount} items" else null,
            onBack   = onBack,
            actions  = { OmniActionChip(label = "Sort", icon = Icons.Outlined.Sort, onClick = { showSortSheet = true }) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Selection bar
        AnimatedVisibility(
            visible  = isSelecting,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            OmniSelectionBar(
                count    = selectedIds.size,
                onClose  = viewModel::clearSelection,
                onShare  = { },
                onDelete = { viewModel.deleteSelected() },   // ← FIXED
                onMore   = { }
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 145.dp)
        )
    }

    if (showSortSheet) {
        FolderSortBottomSheet(
            current   = sortConfig,
            onDismiss = { showSortSheet = false },
            onApply   = { viewModel.updateSort(it); showSortSheet = false }
        )
    }
}

// ── Folder hero card ───────────────────────────────────────────────────────────

@Composable
private fun FolderHeroCard(
    folder:     com.omnimemoria.domain.model.MediaFolder?,
    photoCount: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OmniSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))) {
            if (folder != null) {
                // Bound decode to 2x the displayed size (80dp * 2 = ~160dp at mdpi)
                AsyncImage(
                    model              = ImageRequest.Builder(context).data(folder.coverUri).size(Size(320, 320)).build(),
                    contentDescription = folder.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1.0f to Color.Black.copy(alpha = 0.4f))
                    )
                )
            } else {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (folder == null) {
                ShimmerBox(modifier = Modifier.width(120.dp).height(18.dp).clip(RoundedCornerShape(6.dp)))
            } else {
                Text(folder.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                if (photoCount > 0) "$photoCount items" else "Local storage folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("Local Storage", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
        } // end inner Row
    } // end OmniSurface
}

// FolderTopBar and FolderPhotoCell removed -- replaced by OmniDetailTopBar and
// PhotoCell (from GalleryScreen) in the main composable above.

// ── Sort bottom sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FolderSortBottomSheet(
    current:   SortConfig,
    onDismiss: () -> Unit,
    onApply:   (SortConfig) -> Unit
) {
    var sortBy    by remember(current) { mutableStateOf(current.sortBy) }
    var sortOrder by remember(current) { mutableStateOf(current.sortOrder) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = OmniSheetContainerColor,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3860)))
            Spacer(Modifier.height(20.dp))
            Text("Sort photos", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            listOf(SortBy.DATE_TAKEN to "Date Taken", SortBy.NAME to "File Name A–Z", SortBy.SIZE to "Largest First")
                .forEach { (candidate, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sortBy == candidate)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .combinedClickable(onClick = { sortBy = candidate })
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sortBy == candidate, onClick = { sortBy = candidate },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

            Spacer(Modifier.height(16.dp))
            Text("Direction", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(SortOrder.DESCENDING to "Newest ↓", SortOrder.ASCENDING to "Oldest ↑")
                    .forEach { (ord, lbl) ->
                        FilterChip(selected = sortOrder == ord, onClick = { sortOrder = ord },
                            label = { Text(lbl) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor     = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
            }
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF5548D9), Color(0xFF8B7FF5))))
                    .combinedClickable(onClick = { onApply(SortConfig(sortBy = sortBy, sortOrder = sortOrder)) }),
                contentAlignment = Alignment.Center
            ) {
                Text("Apply", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickable(onClick: () -> Unit) =
    this.combinedClickable(onClick = onClick)
