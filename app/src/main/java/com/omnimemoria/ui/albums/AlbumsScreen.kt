package com.omnimemoria.ui.albums

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.FolderSortBy
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.components.OmniActionChip
import com.omnimemoria.ui.components.OmniEmptyState
import com.omnimemoria.ui.components.OmniSectionHeader
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.theme.AmberVibe
import com.omnimemoria.ui.theme.RoseMemory
import kotlinx.coroutines.delay

// ── Vibe data ──────────────────────────────────────────────────────────────────

private data class VibeEntry(val emoji: String, val label: String, val color: Color)

private val Vibes = listOf(
    VibeEntry("🌅", "Golden\nHour",   AmberVibe),
    VibeEntry("🌊", "Quiet\nMoments", Color(0xFF2D26A0)),
    VibeEntry("❤️", "People",         RoseMemory),
    VibeEntry("🌿", "Nature",         Color(0xFF1B6B3A)),
    VibeEntry("🏙️", "City Life",     Color(0xFF333355)),
    VibeEntry("🌙", "Night Shots",    Color(0xFF1A1040))
)

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onFolderClick: (String) -> Unit,
    viewModel:     AlbumsViewModel = hiltViewModel()
) {
    val folders    = viewModel.folders.collectAsLazyPagingItems()
    val sortConfig by viewModel.folderSortConfig.collectAsState()
    var showSheet  by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            contentPadding        = PaddingValues(
                top    = 112.dp,   // clearance for floating OmniTopBar
                bottom = 100.dp,   // clearance for bottom nav pill
                start  = 12.dp,
                end    = 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxSize()
        ) {

            // ── Vibe albums ────────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                VibesSection()
            }

            // ── Albums section header ──────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                OmniSectionHeader(
                    title       = "Albums",
                    subtitle    = if (folders.itemCount > 0) "${folders.itemCount} albums" else null,
                    actionLabel = "Sort",
                    actionIcon  = Icons.Outlined.Sort,
                    onAction    = { showSheet = true },
                    modifier    = Modifier.padding(horizontal = 4.dp)
                )
            }

            // ── Loading skeleton ───────────────────────────────────────────
            if (folders.loadState.refresh is LoadState.Loading) {
                items(6) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            } else if (folders.itemCount == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OmniEmptyState(
                        icon     = Icons.Outlined.FolderOpen,
                        title    = "No albums yet",
                        subtitle = "Photos will appear here once you take some"
                    )
                }
            } else {
                items(
                    count = folders.itemCount,
                    key   = { idx -> folders[idx]?.bucketId ?: "folder_$idx" }
                ) { idx ->
                    folders[idx]?.let { folder ->
                        AlbumCard(
                            folder  = folder,
                            index   = idx,
                            onClick = { onFolderClick(folder.bucketId) }
                        )
                    } ?: ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ── Sort bottom sheet ────────────────────────────────────────────────────
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = Color(0xFF141220),
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            AlbumSortSheet(
                initial   = sortConfig,
                onDismiss = { showSheet = false },
                onApply   = { viewModel.updateFolderSort(it); showSheet = false }
            )
        }
    }
}

// ── Vibe albums row ────────────────────────────────────────────────────────────

@Composable
private fun VibesSection() {
    Column {
        OmniSectionHeader(
            title       = "Vibe Albums",
            actionLabel = "See all",
            onAction    = { /* TODO: navigate to vibe albums full list */ },
            modifier    = Modifier.padding(horizontal = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(vertical = 4.dp)
        ) {
            items(Vibes) { vibe ->
                VibeCard(vibe = vibe)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color     = Color.White.copy(alpha = 0.05f),
            thickness = 0.5.dp
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun VibeCard(vibe: VibeEntry) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(vibe.color, vibe.color.copy(alpha = 0.65f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .clickable(onClick = {}) // Corrected: Use standard clickable
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Text(vibe.emoji, fontSize = 26.sp)
            Spacer(Modifier.height(5.dp))
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

// ── Album card ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(folder: MediaFolder, index: Int, onClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Staggered entrance
    val offsetY = remember { Animatable(28f) }
    LaunchedEffect(folder.bucketId) {
        delay(index * 38L)
        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
    }
    val scale by animateFloatAsState(
        targetValue   = if (menuExpanded) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "album_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { translationY = offsetY.value; scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))  // ← unified 20dp everywhere
            .combinedClickable(
                onClick     = onClick,
                onLongClick = { menuExpanded = true }
            )
    ) {
        // Cover image
        AsyncImage(
            model              = folder.coverUri,
            contentDescription = folder.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Bottom scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f   to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )

        // Folder name + count
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text       = folder.name,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                maxLines   = 1
            )
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = "${folder.photoCount}",
                    style      = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Context menu
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick  = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    tint     = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded         = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor   = Color(0xFF1E1C30)
            ) {
                DropdownMenuItem(
                    text    = { Text("Share All", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { menuExpanded = false }
                )
                DropdownMenuItem(
                    text    = { Text("Select", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { menuExpanded = false }
                )
            }
        }
    }
}

// ── Sort bottom sheet ──────────────────────────────────────────────────────────

@Composable
private fun AlbumSortSheet(
    initial:   FolderSortConfig,
    onDismiss: () -> Unit,
    onApply:   (FolderSortConfig) -> Unit
) {
    var sortBy    by remember(initial) { mutableStateOf(initial.sortBy) }
    var sortOrder by remember(initial) { mutableStateOf(initial.sortOrder) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
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
            "Sort albums",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))

        listOf(
            FolderSortBy.DATE_LATEST_PHOTO to "Latest Photo",
            FolderSortBy.NAME             to "Name A–Z",
            FolderSortBy.PHOTO_COUNT      to "Most Photos"
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
                    .clickable { sortBy = candidate } // Corrected: Standard clickable resolves modifier cleanly
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = sortBy == candidate,
                    onClick  = { sortBy = candidate },
                    colors   = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
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

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onApply(FolderSortConfig(sortBy, sortOrder)) }, // Corrected: Standard clickable
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
