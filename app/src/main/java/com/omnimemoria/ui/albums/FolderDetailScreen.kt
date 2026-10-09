package com.omnimemoria.ui.albums

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.omnimemoria.ui.components.OmniEmptyState
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
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
    val filterConfig by viewModel.filter.collectAsState()
    val sortConfig       by viewModel.sortConfig.collectAsState()
    var showSortSheet    by remember { mutableStateOf(false) }


    if (isSelecting) {
        BackHandler {
            viewModel.clearSelection()
        }
    }

    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val gridState               = viewModel.gridState

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
        Column(Modifier.fillMaxSize()) {
            OmniDetailTopBar(
                title = folder?.name?.ifBlank { "Album" } ?: "Album",
                subtitle = if (photos.itemCount > 0) "${photos.itemCount} items" else null,
                onBack = { if (isSelecting) viewModel.clearSelection() else onBack() },
                actions = {
                    OmniActionChip(label = "Sort & Filter", icon = Icons.Outlined.Sort,
                        onClick = { showSortSheet = true })
                }
            )
            LazyVerticalGrid(
                state                 = gridState,
                columns               = GridCells.Fixed(3),
                contentPadding        = PaddingValues(
                    top    = 8.dp,
                    bottom = if (isSelecting) 104.dp else 24.dp,
                    start  = 12.dp,
                    end    = 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FolderHeroCard(folder = folder, photoCount = photos.itemCount)
                }

                if (photos.loadState.refresh is LoadState.Loading && photos.itemCount == 0) {
                    items(24) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                    }
                } else if (photos.loadState.refresh is LoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OmniEmptyState(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Could not load this album",
                            subtitle = "Try loading your photos and videos again.",
                            actionLabel = "Retry",
                            actionIcon = Icons.Outlined.Refresh,
                            onAction = { photos.retry() }
                        )
                    }
                } else if (photos.itemCount == 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OmniEmptyState(
                            icon = Icons.Outlined.PhotoLibrary,
                            title = "This album is empty",
                            subtitle = "Photos and videos in this folder will appear here.",
                            floating = true
                        )
                    }
                } else {
                    items(
                        count = photos.itemCount,
                        key   = { i -> photos.peek(i)?.id ?: "p_$i" }
                    ) { index ->
                        photos[index]?.let { photo ->
                            com.omnimemoria.ui.gallery.PhotoCell(
                                modifier                = Modifier.animateItem(),
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

        }

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
        com.omnimemoria.ui.components.filters.GallerySortFilterSheetContent(
            currentSort = sortConfig,
            currentFilter = filterConfig,
        showGrouping = false,
            onDismiss = { showSortSheet = false },
            onApply = { sort, filter -> viewModel.updateSortAndFilter(sort, filter); showSortSheet = false }
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
        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) {
            if (folder != null) {
                // Keep cover decoding bounded for low-memory devices.
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
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text("On this device", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
        } // end inner Row
    } // end OmniSurface
}

// FolderTopBar and FolderPhotoCell removed -- replaced by OmniDetailTopBar and
// PhotoCell (from GalleryScreen) in the main composable above.
