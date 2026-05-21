package com.omnimemoria.ui.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnimemoria.ui.gallery.components.FilterSortBottomSheet
import com.omnimemoria.ui.gallery.components.QuickSortBar
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.theme.AmberVibe
import com.omnimemoria.ui.theme.RoseMemory

// ── Shared Element key helper ────────────────────────────────────────────────────
fun photoSharedKey(photoId: Long) = "photo_$photoId"

// ── Vibe chips placeholder data ──────────────────────────────────────────────────
private data class VibeChip(val emoji: String, val label: String, val color: Color)
private val SelectionBarBottomPadding = 148.dp
private val QuickSortBarTopPadding = 112.dp
private val GalleryGridTopPadding = 164.dp
private val placeholderVibes = listOf(
    VibeChip("🌅", "Golden\nHour",   AmberVibe),
    VibeChip("🌊", "Quiet\nMoments", Color(0xFF2D26A0)),
    VibeChip("❤️", "Favorites",      RoseMemory),
    VibeChip("🌿", "Nature",         Color(0xFF1B6B3A)),
    VibeChip("🏙️", "City Life",     Color(0xFF333355))
)

// ─────────────────────────────────────────────────────────────────────────────────

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
    val activeSortConfig by viewModel.activeSortConfig.collectAsState()
    val currentSortLabel by viewModel.currentSortLabel.collectAsState()

    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    val gridState = rememberLazyGridState()
    var showSortSheet by remember { mutableStateOf(false) }

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
                top    = GalleryGridTopPadding,
                bottom = 130.dp,
                start  = 6.dp,
                end    = 6.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxSize()
                .transformable(
                    state               = transformableState,
                    lockRotationOnZoomPan = true
                )
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VibeAlbumsRow(modifier = Modifier.padding(bottom = 20.dp))
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionSubHeader(
                    title     = "All Media",
                    count     = mediaStats.totalCount,
                    isLoading = groupedPhotos.loadState.refresh is LoadState.Loading,
                    onSort    = { showSortSheet = true },
                    sortLabel = currentSortLabel,
                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (groupedPhotos.loadState.refresh is LoadState.Loading) {
                items(count = 30, span = { GridItemSpan(1) }) {
                    SkeletonPhotoCell(size = (360 / columnCount).dp)
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
                    span  = { index ->
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
                                uri                 = photo.uri.toString(),
                                photoId             = photo.id,
                                isVideo             = photo.mimeType.startsWith("video/", ignoreCase = true),
                                isSelected          = isSelected,
                                isSelecting         = isSelecting,
                                sharedTransitionScope   = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick     = {
                                    if (isSelecting) viewModel.toggleSelection(photo.id)
                                    else onPhotoClick(photo.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(photo.id)
                                }
                            )
                        }
                        null -> SkeletonPhotoCell(size = (360 / columnCount).dp)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        QuickSortBar(
            activeSortConfig = activeSortConfig,
            onSortChanged = { viewModel.updateSort(it) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = QuickSortBarTopPadding)
        )

        AnimatedVisibility(
            visible  = isSelecting,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = SelectionBarBottomPadding)
        ) {
            SelectionActionBar(
                count    = selectedIds.size,
                onClose  = { viewModel.clearSelection() },
                onShare  = { /* TODO */ },
                onDelete = { /* TODO */ },
                onMore   = { /* TODO */ }
            )
        }
    }

    if (showSortSheet) {
        FilterSortBottomSheet(
            activeSortConfig = activeSortConfig,
            onDismiss = { showSortSheet = false },
            onApply = { config ->
                viewModel.updateSort(config)
                showSortSheet = false
            }
        )
    }
}

// ── Vibe Albums Row ───────────────────────────────────────────────────────────────

@Composable
private fun VibeAlbumsRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Vibe Albums",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text  = "See all",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = 8.dp)
        ) {
            items(placeholderVibes) { vibe -> VibeChipCard(vibe = vibe) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VibeChipCard(vibe: VibeChip) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(vibe.color, vibe.color.copy(alpha = 0.7f))))
            .combinedClickable(onClick = {})
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Text(text = vibe.emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = vibe.label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                lineHeight = 18.sp
            )
        }
    }
}

// ── Section Sub-header ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionSubHeader(
    title:     String,
    count:     Int,
    isLoading: Boolean,
    onSort:    () -> Unit,
    sortLabel: String,
    modifier:  Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (!isLoading && count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "$count",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onSort)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Outlined.Tune, "Sort", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Date Header row ───────────────────────────────────────────────────────────────

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

// ── Photo Cell ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoCell(
    uri:                    String,
    photoId:                Long,
    isVideo:                Boolean,
    isSelected:             Boolean,
    isSelecting:            Boolean,
    sharedTransitionScope:   SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick:                () -> Unit,
    onLongClick:            () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "photo_scale_$photoId"
    )

    val sharedModifier: Modifier = if (
        sharedTransitionScope   != null &&
        animatedVisibilityScope != null &&
        !isSelecting
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = photoSharedKey(photoId)),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform   = com.omnimemoria.ui.detail.photosBoundsTransform
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 14.dp else 8.dp))
            .combinedClickable(
                onClick     = onClick,
                onLongClick = onLongClick
            )
    ) {
        CachedThumbnail(
            uri      = uri,
            modifier = Modifier
                .fillMaxSize()
                .then(sharedModifier)
        )

        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isSelecting,
            enter   = fadeIn(tween(150)),
            exit    = fadeOut(tween(150))
        ) {
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
                        imageVector        = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
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

// ── Cached Thumbnail ──────────────────────────────────────────────────────────────

@Composable
private fun CachedThumbnail(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(uri)
            .size(Size(512, 512))
            .build(),
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        modifier           = modifier
    )
}

// ── Skeleton shimmer cell ─────────────────────────────────────────────────────────

@Composable
private fun SkeletonPhotoCell(size: Dp) {
    val alpha by rememberInfiniteTransition(label = "skeleton")
        .animateFloat(
            initialValue  = 0.2f,
            targetValue   = 0.5f,
            animationSpec = infiniteRepeatable(
                animation  = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton_alpha"
        )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

// ── Selection Action Bar ──────────────────────────────────────────────────────────

@Composable
private fun SelectionActionBar(
    count:    Int,
    onClose:  () -> Unit,
    onShare:  () -> Unit,
    onDelete: () -> Unit,
    onMore:   () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1E1C30).copy(alpha = 0.95f), Color(0xFF2D26A0).copy(alpha = 0.9f))
                )
            )
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, null, tint = Color.White)
                }
                Text(
                    text       = "$count selected",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
            Row {
                IconButton(onClick = onShare)  { Icon(Icons.Outlined.Share,    null, tint = Color.White,       modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete,   null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onMore)   { Icon(Icons.Outlined.MoreVert, null, tint = Color.White,       modifier = Modifier.size(22.dp)) }
            }
        }
    }
}
