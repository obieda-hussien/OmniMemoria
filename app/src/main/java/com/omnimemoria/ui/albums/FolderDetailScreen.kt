package com.omnimemoria.ui.albums

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.components.OmniActionChip
import com.omnimemoria.ui.components.OmniSelectionBar
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.detail.photosBoundsTransform
import com.omnimemoria.ui.photoSharedKey

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class)
@Composable
fun FolderDetailScreen(
    onPhotoClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel()
) {
    val haptic           = LocalHapticFeedback.current
    val photos           = viewModel.photos.collectAsLazyPagingItems()
    val folder           by viewModel.folder.collectAsState()
    val selectedIds      by viewModel.selectedIds.collectAsState()
    val isSelecting       = selectedIds.isNotEmpty()
    val sortConfig       by viewModel.sortConfig.collectAsState()
    var showSortSheet    by remember { mutableStateOf(false) }

    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    val gridState = rememberLazyGridState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ── Atmospheric blurred banner ───────────────────────────────────────
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

        // Gradient: banner → background
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

        // ── Main scrollable grid ─────────────────────────────────────────────
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

            // Folder info hero card
            item(span = { GridItemSpan(maxLineSpan) }) {
                FolderHeroCard(
                    folder     = folder,
                    photoCount = photos.itemCount
                )
            }

            // Loading skeletons
            if (photos.loadState.refresh is LoadState.Loading) {
                items(24) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            } else {
                items(
                    count = photos.itemCount,
                    key   = { i -> photos[i]?.id ?: "p_$i" }
                ) { index ->
                    photos[index]?.let { photo ->
                        FolderPhotoCell(
                            photo                   = photo,
                            selected                = photo.id in selectedIds,
                            selecting               = isSelecting,
                            sharedTransitionScope   = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                if (isSelecting) viewModel.toggleSelection(photo.id)
                                else onPhotoClick(photo.id)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleSelection(photo.id)
                            }
                        )
                    } ?: ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // ── Top bar scrim ────────────────────────────────────────────────────
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

        // ── Floating top bar ─────────────────────────────────────────────────
        FolderTopBar(
            folderName = folder?.name ?: "",
            photoCount = photos.itemCount,
            onBack     = onBack,
            onSort     = { showSortSheet = true },
            modifier   = Modifier.align(Alignment.TopCenter)
        )

        // ── Multi-select bar ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isSelecting,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp)
        ) {
            OmniSelectionBar(
                count    = selectedIds.size,
                onClose  = viewModel::clearSelection,
                onShare  = { },
                onDelete = { },
                onMore   = { }
            )
        }
    }

    // ── Sort sheet ───────────────────────────────────────────────────────────
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141220))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover thumbnail with gradient overlay
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            if (folder != null) {
                AsyncImage(
                    model              = folder.coverUri,
                    contentDescription = folder.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Bottom scrim on thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.4f)
                            )
                        )
                )
            } else {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (folder == null) {
                ShimmerBox(
                    modifier = Modifier
                        .width(120.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Text(
                    text       = folder.name,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color      = MaterialTheme.colorScheme.onBackground,
                    maxLines   = 1
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text  = if (photoCount > 0) "$photoCount items" else "Local storage folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            // Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text       = "Local Storage",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Photo cell with shared element ────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun FolderPhotoCell(
    photo:                   MediaPhoto,
    selected:                Boolean,
    selecting:               Boolean,
    sharedTransitionScope:   SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick:                 () -> Unit,
    onLongClick:             () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (selected) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "cell_scale"
    )
    val isVideo = photo.mimeType.startsWith("video/", ignoreCase = true)

    val sharedModifier: Modifier = if (
        sharedTransitionScope != null &&
        animatedVisibilityScope != null &&
        !selecting
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState      = rememberSharedContentState(key = photoSharedKey(photo.id)),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform         = photosBoundsTransform
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (selected) 14.dp else 8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Image with shared element modifier
        AsyncImage(
            model              = photo.uri,
            contentDescription = photo.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .then(sharedModifier)
        )

        // Video badge
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint               = Color.White,
                    modifier           = Modifier.size(13.dp)
                )
            }
        }

        // Selection overlay
        AnimatedVisibility(visible = selecting, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.18f)
                    )
            ) {
                if (selected) {
                    Icon(
                        imageVector        = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(22.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                }
            }
        }
    }
}

// ── Floating top bar ────────────────────────────────────────────────────────────

@Composable
private fun FolderTopBar(
    folderName: String,
    photoCount: Int,
    onBack:     () -> Unit,
    onSort:     () -> Unit,
    modifier:   Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back pill
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .combinedClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = MaterialTheme.colorScheme.onBackground,
                modifier           = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = folderName.ifBlank { "Album" },
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onBackground,
                maxLines   = 1
            )
            if (photoCount > 0) {
                Text(
                    text  = "$photoCount items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OmniActionChip(
            label   = "Sort",
            icon    = Icons.Outlined.Sort,
            onClick = onSort
        )
    }
}

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
        containerColor   = Color(0xFF141220),
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3A3860))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Sort photos",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))

            listOf(
                SortBy.DATE_TAKEN to "Date Taken",
                SortBy.NAME       to "File Name A–Z",
                SortBy.SIZE       to "Largest First"
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
                        .combinedClickable(onClick = { sortBy = candidate })
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortBy == candidate,
                        onClick  = { sortBy = candidate },
                        colors   = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = label,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Direction",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    SortOrder.DESCENDING to "Newest ↓",
                    SortOrder.ASCENDING  to "Oldest ↑"
                ).forEach { (ord, lbl) ->
                    FilterChip(
                        selected = sortOrder == ord,
                        onClick  = { sortOrder = ord },
                        label    = { Text(lbl) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF5548D9), Color(0xFF8B7FF5))
                        )
                    )
                    .combinedClickable(onClick = {
                        onApply(SortConfig(sortBy = sortBy, sortOrder = sortOrder))
                    }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Apply",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

// ── Helper extension ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickable(onClick: () -> Unit) =
    this.combinedClickable(onClick = onClick)
