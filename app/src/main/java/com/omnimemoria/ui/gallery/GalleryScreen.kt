package com.omnimemoria.ui.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omnimemoria.ui.theme.AmberVibe
import com.omnimemoria.ui.theme.RoseMemory

// ── Vibe chips placeholder data ──────────────────────────────────────────────────
private data class VibeChip(val emoji: String, val label: String, val color: Color)
private val placeholderVibes = listOf(
    VibeChip("🌅", "Golden\nHour",   AmberVibe),
    VibeChip("🌊", "Quiet\nMoments", Color(0xFF2D26A0)),
    VibeChip("❤️", "Favorites",      RoseMemory),
    VibeChip("🌿", "Nature",         Color(0xFF1B6B3A)),
    VibeChip("🏙️", "City Life",     Color(0xFF333355))
)

// ─────────────────────────────────────────────────────────────────────────────────

@Composable
fun GalleryScreen(
    onPhotoClick:   (Long) -> Unit,
    viewModel:      GalleryViewModel = hiltViewModel()
) {
    val haptic        = LocalHapticFeedback.current
    val groupedPhotos = viewModel.groupedPhotos.collectAsLazyPagingItems()
    val selectedIds   by viewModel.selectedIds.collectAsState()
    val isSelecting   by viewModel.isInSelectionMode.collectAsState()
    val columnCount   by viewModel.columnCount.collectAsState()

    // Pinch-to-zoom zoom tracker
    var cumulativeZoom by remember { mutableStateOf(1f) }

    val gridState  = rememberLazyGridState()

    // ── Root Box (pinch gesture wraps everything) ─────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _: Offset, _: Offset, zoom: Float, _: Float ->
                    cumulativeZoom *= zoom
                    // threshold: 25% change needed to switch column count
                    if (cumulativeZoom > 1.25f || cumulativeZoom < 0.75f) {
                        viewModel.onPinchZoom(cumulativeZoom)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cumulativeZoom = 1f
                    }
                }
            }
    ) {
        LazyVerticalGrid(
            state             = gridState,
            columns           = GridCells.Fixed(columnCount),
            contentPadding    = PaddingValues(
                top    = 112.dp,   // clearance for floating TopBar
                bottom = 130.dp,   // clearance for BottomNav + FAB
                start  = 6.dp,
                end    = 6.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            // ── Vibe Albums Row ──────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                VibeAlbumsRow(modifier = Modifier.padding(bottom = 20.dp))
            }

            // ── Section header + photo count ─────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionSubHeader(
                    title    = "All Photos",
                    count    = groupedPhotos.itemCount,
                    isLoading = groupedPhotos.loadState.refresh is LoadState.Loading,
                    onSort   = { /* TODO: open sort sheet */ },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // ── Skeleton shimmer while first page loads ──────────────────────
            if (groupedPhotos.loadState.refresh is LoadState.Loading) {
                items(count = 30, span = { GridItemSpan(1) }) {
                    SkeletonPhotoCell(size = (360 / columnCount).dp)
                }
            } else {
                // ── Real items (Photos + Date Headers) ───────────────────────
                items(
                    count = groupedPhotos.itemCount,
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
                                uri        = photo.uri.toString(),
                                isSelected = isSelected,
                                isSelecting = isSelecting,
                                onClick    = {
                                    if (isSelecting) {
                                        viewModel.toggleSelection(photo.id)
                                    } else {
                                        onPhotoClick(photo.id)
                                    }
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

            // ── Footer padding ───────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ── Multi-Select Action Bar ──────────────────────────────────────────
        AnimatedVisibility(
            visible  = isSelecting,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SelectionActionBar(
                count       = selectedIds.size,
                onClose     = { viewModel.clearSelection() },
                onShare     = { /* TODO */ },
                onDelete    = { /* TODO */ },
                onMore      = { /* TODO */ }
            )
        }
    }
}

// ── Vibe Albums Row ───────────────────────────────────────────────────────────────

@Composable
private fun VibeAlbumsRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
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
            items(placeholderVibes) { vibe ->
                VibeChipCard(vibe = vibe)
            }
        }
    }
}

@Composable
private fun VibeChipCard(vibe: VibeChip) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(vibe.color, vibe.color.copy(alpha = 0.7f))
                )
            )
            .clickable { }
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

// ── Section Sub-header ─────────────────────────────────────────────────────────

@Composable
private fun SectionSubHeader(
    title:     String,
    count:     Int,
    isLoading: Boolean,
    onSort:    () -> Unit,
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
                        text  = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSort)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector        = Icons.Outlined.Tune,
                contentDescription = "Sort",
                modifier           = Modifier.size(16.dp),
                tint               = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text  = "Sort",
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

@Composable
private fun PhotoCell(
    uri:         String,
    isSelected:  Boolean,
    isSelecting: Boolean,
    onClick:     () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue  = if (isSelected) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label        = "photo_scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 14.dp else 8.dp))
            .pointerInput(isSelecting) {
                // Long press → multi-select
                detectTransformGestures { _, pan, zoom, _ ->
                    // consumed by parent pinch
                }
            }
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────────
        AsyncImage(
            model              = uri,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onClick
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                // detect long press manually via delay
                            }
                        }
                    }
                }
        )

        // ── Selection overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isSelecting,
            enter   = fadeIn(tween(150)),
            exit    = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else
                            Color.Black.copy(alpha = 0.15f)
                    )
            ) {
                if (isSelected) {
                    Icon(
                        imageVector        = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1E1C30).copy(alpha = 0.95f),
                        Color(0xFF2D26A0).copy(alpha = 0.9f)
                    )
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
            // Close + count
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

            // Actions
            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share,  null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Outlined.MoreVert, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
