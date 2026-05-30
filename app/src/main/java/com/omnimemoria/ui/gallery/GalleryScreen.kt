package com.omnimemoria.ui.gallery

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.omnimemoria.ui.gallery.components.QuickSortBar
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.photoSharedKey
import com.omnimemoria.ui.components.OmniSectionHeader
import com.omnimemoria.ui.components.OmniSelectionBar
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.detail.photosBoundsTransform
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable

// Rose/crimson color for the favorite heart badge
private val FavoriteRose = Color(0xFFFF4B6E)

private val SelectionBarBottomPadding = 92.dp

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

    val gridState = rememberLazyGridState()
    var showSortFilterSheet by remember { mutableStateOf(false) }

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
                OmniSectionHeader(
                    title = when (currentFilter) {
                        MediaFilter.ALL         -> "All Media"
                        MediaFilter.PHOTOS_ONLY -> "Photos"
                        MediaFilter.VIDEOS_ONLY -> "Videos"
                    },
                    subtitle    = "${mediaStats.totalCount} items",
                    actionLabel = "Filter & Sort",
                    actionIcon  = Icons.Outlined.Tune,
                    onAction    = { showSortFilterSheet = true },
                    modifier    = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                QuickSortBar(
                    currentSort = sortConfig,
                    onSortChanged = { config -> viewModel.updateSortAndFilter(config, currentFilter) }
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
                        is GalleryItem.DateHeader -> {
                            DateHeaderRow(label = item.label)
                        }
                        is GalleryItem.Photo -> {
                            val photo      = item.photo
                            val isSelected = photo.id in selectedIds

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
                                    if (isSelecting) {
                                        viewModel.toggleSelection(photo.id)
                                    } else {
                                        viewModel.prepareForNavigation(photo)
                                        onPhotoClick(photo.id)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(photo.id)
                                }
                            )
                        }
                        null -> SkeletonPhotoCell()
                    }
                }
            }
        }

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
                onDelete = { /* TODO Phase 4 */ },
                onMore   = { /* TODO Phase 4 */ }
            )
        }
    }

    if (showSortFilterSheet) {
        GallerySortFilterSheet(
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
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
        CachedThumbnail(
            uri      = uri,
            modifier = Modifier
                .fillMaxSize()
                .then(sharedModifier)
        )

        // ── Video badge ────────────────────────────────────────────────────────
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

        // ── Favorite heart badge (bottom-right) ────────────────────────────────
        AnimatedVisibility(
            visible = isFavorite,
            enter   = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f),
            exit    = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint               = FavoriteRose,
                    modifier           = Modifier.size(14.dp)
                )
            }
        }

        // ── Selection overlay ──────────────────────────────────────────────────
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

// ── Cached thumbnail ───────────────────────────────────────────────────────────

@Composable
private fun CachedThumbnail(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model              = ImageRequest.Builder(context).data(uri).size(Size(512, 512)).build(),
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        modifier           = modifier
    )
}

// ── Skeleton cell ──────────────────────────────────────────────────────────────

@Composable
private fun SkeletonPhotoCell() {
    ShimmerBox(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
    )
}

// ── Sort / filter bottom sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GallerySortFilterSheet(
    currentFilter: MediaFilter,
    currentSort:   SortConfig,
    onDismiss:     () -> Unit,
    onApply:       (SortConfig, MediaFilter) -> Unit
) {
    var sortBy    by remember { mutableStateOf(currentSort.sortBy) }
    var sortOrder by remember { mutableStateOf(currentSort.sortOrder) }
    var groupBy   by remember { mutableStateOf(currentSort.groupBy) }
    var filterBy  by remember { mutableStateOf(currentFilter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = Color(0xFF141220),
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3A3860))
            )
            Spacer(Modifier.height(20.dp))

            Text("Filter & Sort",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(18.dp))

            Text("Show",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaFilter.entries.forEach { target ->
                    FilterChip(
                        selected = filterBy == target,
                        onClick  = { filterBy = target },
                        label    = {
                            Text(when (target) {
                                MediaFilter.ALL         -> "All"
                                MediaFilter.PHOTOS_ONLY -> "Photos"
                                MediaFilter.VIDEOS_ONLY -> "Videos"
                            })
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            listOf(
                SortBy.DATE_TAKEN to "Date Taken",
                SortBy.DATE_MODIFIED to "Date Modified",
                SortBy.SIZE       to "Storage Size",
                SortBy.NAME       to "File Name",
                SortBy.TYPE       to "File Type",
                SortBy.RESOLUTION to "Resolution",
                SortBy.DURATION   to "Duration",
                SortBy.FAVORITES_FIRST to "Favorites First"
            ).forEach { (candidate, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sortBy == candidate)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .clickable { sortBy = candidate }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortBy == candidate,
                        onClick  = { sortBy = candidate },
                        colors   = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Direction",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SortOrder.DESCENDING to "Descending ↓",
                    SortOrder.ASCENDING  to "Ascending ↑"
                ).forEach { (ord, lbl) ->
                    FilterChip(
                        selected = sortOrder == ord,
                        onClick  = { sortOrder = ord },
                        label    = { Text(lbl) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Group by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                listOf(
                    null to "None",
                    GroupBy.DAY to "Day",
                    GroupBy.MONTH to "Month",
                    GroupBy.YEAR to "Year",
                    GroupBy.LOCATION to "Location (requires GPS data)"
                ).forEach { (candidate, lbl) ->
                    FilterChip(
                        selected = groupBy == candidate,
                        onClick  = { groupBy = candidate },
                        label    = { Text(lbl) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onApply(SortConfig(sortBy = sortBy, sortOrder = sortOrder, groupBy = groupBy), filterBy) },
                contentAlignment = Alignment.Center
            ) {
                Text("Apply",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
