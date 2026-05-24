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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.components.OmniActionChip
import com.omnimemoria.ui.components.OmniDetailTopBar
import com.omnimemoria.ui.components.OmniSelectionBar
import com.omnimemoria.ui.components.ShimmerBox

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    onPhotoClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel()
) {
    val photos      = viewModel.photos.collectAsLazyPagingItems()
    val folder      by viewModel.folder.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelecting  = selectedIds.isNotEmpty()
    var showSortSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ── Atmospheric blurred banner (behind top content) ──────────────────
        // Subtle — just enough to give depth without being distracting.
        folder?.let { f ->
            AsyncImage(
                model              = f.coverUri,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .blur(30.dp)
                    .scale(1.15f)
                    .alpha(0.22f)
            )
        }

        // Gradient fade from banner to background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.65f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        // ── Main scrollable grid ─────────────────────────────────────────────
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(
                top    = 120.dp,   // clearance below floating top bar
                bottom = 140.dp,   // clearance above bottom nav
                start  = 8.dp,
                end    = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement   = Arrangement.spacedBy(4.dp),
            modifier              = Modifier.fillMaxSize()
        ) {

            // Folder info card
            item(span = { GridItemSpan(maxLineSpan) }) {
                FolderInfoCard(
                    folder = folder,
                    photoCount = photos.itemCount
                )
            }

            // Loading skeletons
            if (photos.loadState.refresh is LoadState.Loading) {
                items(18) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            } else {
                items(
                    count = photos.itemCount,
                    key   = { i -> photos[i]?.id ?: "p_$i" }
                ) { index ->
                    photos[index]?.let { photo ->
                        FolderPhotoCell(
                            photo       = photo,
                            selected    = photo.id in selectedIds,
                            selecting   = isSelecting,
                            onClick     = {
                                if (isSelecting) viewModel.toggleSelection(photo.id)
                                else onPhotoClick(photo.id)
                            },
                            onLongClick = { viewModel.toggleSelection(photo.id) }
                        )
                    } ?: ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        // ── Floating top bar ─────────────────────────────────────────────────
        // Gradient scrim behind it so text stays readable over the blurred banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        1.0f to Color.Transparent
                    )
                )
        )

        OmniDetailTopBar(
            title    = folder?.name ?: "Album",
            subtitle = if (photos.itemCount > 0) "${photos.itemCount} items" else null,
            onBack   = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            OmniActionChip(
                label = "Sort",
                icon  = Icons.Outlined.Sort,
                onClick = { showSortSheet = true }
            )
        }

        // ── Multi-select bar ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isSelecting,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp)  // above bottom nav pill
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
            onDismiss = { showSortSheet = false },
            onApply   = { viewModel.updateSort(it); showSortSheet = false }
        )
    }
}

// ── Folder info card ───────────────────────────────────────────────────────────

@Composable
private fun FolderInfoCard(folder: com.omnimemoria.domain.model.MediaFolder?, photoCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141220))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover thumbnail
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            if (folder != null) {
                AsyncImage(
                    model              = folder.coverUri,
                    contentDescription = folder.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(
                text      = folder?.name ?: "Loading…",
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color     = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = if (photoCount > 0) "$photoCount items" else "Local storage folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            // Small badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text  = "Local Media",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Photo cell ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderPhotoCell(
    photo:       MediaPhoto,
    selected:    Boolean,
    selecting:   Boolean,
    onClick:     () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (selected) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "cell_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (selected) 14.dp else 10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model              = photo.uri,
            contentDescription = photo.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Selection overlay
        AnimatedVisibility(visible = selecting, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
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
                            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )
                }
            }
        }
    }
}

// ── Sort bottom sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FolderSortBottomSheet(
    onDismiss: () -> Unit,
    onApply:   (SortConfig) -> Unit
) {
    var sortBy    by remember { mutableStateOf(SortBy.DATE_TAKEN) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }

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
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
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
                text       = "Sort photos",
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
                text  = "Direction",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    SortOrder.DESCENDING to "Newest / Largest ↓",
                    SortOrder.ASCENDING  to "Oldest / Smallest ↑"
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
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(onClick = { onApply(SortConfig(sortBy = sortBy, sortOrder = sortOrder)) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "Apply",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}
