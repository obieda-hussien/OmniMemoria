package com.omnimemoria.ui.gallery
import com.omnimemoria.domain.model.FilterConfig

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.omnimemoria.ui.components.OmniTopBar
import com.omnimemoria.ui.components.filters.GallerySortFilterSheetContent
import com.omnimemoria.domain.model.MediaType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.components.OmniSectionHeader
import com.omnimemoria.ui.components.OmniSelectionBar
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.detail.photosBoundsTransform
import com.omnimemoria.ui.photoSharedKey
import com.omnimemoria.ui.theme.OmniSheetContainerColor
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import kotlinx.coroutines.launch

private val FavoriteRose             = Color(0xFFFF4B6E)
private val SelectionBarBottomPadding = 12.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    onPhotoClick: (Long) -> Unit,
    viewModel:    GalleryViewModel = hiltViewModel()
) {
    val haptic        = LocalHapticFeedback.current
    val groupedPhotos = viewModel.groupedPhotos.collectAsLazyPagingItems()
    val selectedIds   by viewModel.selectedIds.collectAsState()
    val isSelecting   by viewModel.isInSelectionMode.collectAsState()
    val columnCount   by viewModel.columnCount.collectAsState()
    val mediaStats    by viewModel.mediaStats.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val sortConfig    by viewModel.activeSortConfig.collectAsState()

    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current


    if (isSelecting) {
        BackHandler {
            viewModel.clearSelection()
        }
    }

    val gridState          = rememberLazyGridState()
    var showSortFilterSheet by remember { mutableStateOf(false) }

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
                is GalleryUiEvent.RequestMediaPermission -> {
                    pendingOnConfirm = event.onConfirmed
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    )
                }
                is GalleryUiEvent.ShowSnackbar -> {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message     = event.message,
                            actionLabel = event.actionLabel,
                            duration    = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            event.onAction?.invoke()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 100
        }.collect { compact -> viewModel.setCompactTopBar(compact) }
    }

    var cumulativeZoom by remember { mutableFloatStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        cumulativeZoom *= zoomChange
        if (cumulativeZoom > 1.25f || cumulativeZoom < 0.75f) {
            viewModel.onPinchZoom(cumulativeZoom)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            cumulativeZoom = 1f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        LazyVerticalGrid(
            state             = gridState,
            columns           = GridCells.Fixed(columnCount),
            contentPadding    = PaddingValues(
                top    = 112.dp,
                bottom = 130.dp,
                start  = 6.dp,
                end    = 6.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformableState, lockRotationOnZoomPan = true)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val activeFilterCount by viewModel.activeFilterCount.collectAsState()
                OmniSectionHeader(
                    title = if (activeFilterCount == 0) "All Media" else "Filtered Media",
                    subtitle    = "${mediaStats.totalCount} items  ·  ${sortConfig.toDisplayLabel()}",
                    actionLabel = if (activeFilterCount > 0) "Filters: $activeFilterCount" else "Filter & Sort",
                    actionIcon  = Icons.Outlined.Tune,
                    onAction    = { showSortFilterSheet = true },
                    modifier    = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (groupedPhotos.loadState.refresh is LoadState.Loading) {
                items(count = 30, span = { GridItemSpan(1) }) {
                    SkeletonPhotoCell()
                }
            } else {
                items(
                    count = groupedPhotos.itemCount,
                    key   = { index ->
                        when (val item = groupedPhotos.peek(index)) {
                            is GalleryItem.DateHeader -> "header_${item.anchorPhotoId}"
                            is GalleryItem.Photo      -> "photo_${item.photo.id}"
                            null                      -> "placeholder_$index"
                        }
                    },
                    span = { index ->
                        when (groupedPhotos[index]) {
                            is GalleryItem.DateHeader -> GridItemSpan(maxLineSpan)
                            else                      -> GridItemSpan(1)
                        }
                    }
                ) { index ->
                    when (val item = groupedPhotos[index]) {
                        is GalleryItem.DateHeader -> Box(modifier = Modifier.animateItem()) { DateHeaderRow(label = item.label) }
                        is GalleryItem.Photo -> {
                            val photo      = item.photo
                            val isSelected = photo.id in selectedIds
                            Box(modifier = Modifier.animateItem(
                                fadeInSpec = androidx.compose.animation.core.tween(250),
                                fadeOutSpec = androidx.compose.animation.core.tween(250),
                                placementSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                )
                            )) {
                            PhotoCell(
                                uri                     = photo.uri.toString(),
                                photoId                 = photo.id,
                                isVideo                 = photo.mimeType.startsWith("video/", ignoreCase = true),
                                isSelected              = isSelected,
                                isSelecting             = isSelecting,
                                isFavorite              = item.isFavorite,
                                sharedTransitionScope   = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick = {
                                    if (isSelecting) viewModel.toggleSelection(photo.id)
                                    else { viewModel.prepareForNavigation(photo); onPhotoClick(photo.id) }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(photo.id)
                                }
                            )
                            }
                        }
                        null -> Box(modifier = Modifier.animateItem()) { SkeletonPhotoCell() }
                    }
                }
            }
        }

        // ── Selection bar ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isSelecting,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = SelectionBarBottomPadding)
        ) {
            OmniSelectionBar(
                count    = selectedIds.size,
                onClose  = viewModel::clearSelection,
                onShare  = { /* TODO Phase 4 */ },
                onDelete = { viewModel.deleteSelected() },   // ← FIXED
                onMore   = { /* TODO Phase 4 */ }
            )
        }

        // ── Snackbar ───────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 145.dp)
        )
    }

    if (showSortFilterSheet) {
        com.omnimemoria.ui.components.filters.GallerySortFilterSheetContent(
            currentFilter = currentFilter,
            currentSort   = sortConfig,
            onDismiss     = { showSortFilterSheet = false },
            onApply       = { sort, filter ->
                viewModel.updateSortAndFilter(sort, filter)
                showSortFilterSheet = false
            }
        )
    }
}

// ── Date header row ────────────────────────────────────────────────────────────

@Composable
private fun DateHeaderRow(label: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
}

// ── Photo cell ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun PhotoCell(
    uri:                     String,
    photoId:                 Long,
    isVideo:                 Boolean,
    isSelected:              Boolean,
    isSelecting:             Boolean,
    isFavorite:              Boolean = false,
    sharedTransitionScope:   SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick:                 () -> Unit,
    onLongClick:             () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "photo_scale"
    )
    val sharedModifier: Modifier = if (
        sharedTransitionScope   != null &&
        animatedVisibilityScope != null &&
        !isSelecting
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState      = rememberSharedContentState(key = photoSharedKey(photoId)),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform         = photosBoundsTransform
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 14.dp else 10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        CachedThumbnail(uri = uri, modifier = Modifier.fillMaxSize().then(sharedModifier))

        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, "Video",
                    tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        AnimatedVisibility(
            visible = isFavorite,
            enter   = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f),
            exit    = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Favorite, "Favorite",
                    tint = FavoriteRose, modifier = Modifier.size(14.dp))
            }
        }

        AnimatedVisibility(visible = isSelecting, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.15f)
                    )
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle, "Selected",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CachedThumbnail(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model              = ImageRequest.Builder(context).data(uri).size(Size(512, 512)).build(),
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        modifier           = modifier
    )
}

@Composable
private fun SkeletonPhotoCell() {
    ShimmerBox(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
}

// ── Sort / filter bottom sheet ─────────────────────────────────────────────────



private fun SortConfig.toDisplayLabel(): String {
    val by = when (sortBy) {
        SortBy.DATE_TAKEN      -> "Date"
        SortBy.DATE_MODIFIED   -> "Modified"
        SortBy.SIZE            -> "Size"
        SortBy.NAME            -> "Name"
        SortBy.TYPE            -> "Type"
        SortBy.RESOLUTION      -> "Resolution"
        SortBy.DURATION        -> "Duration"
        SortBy.FAVORITES_FIRST -> "Favorites"
    }
    return "$by ${if (sortOrder == SortOrder.DESCENDING) "↓" else "↑"}"
}
