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
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.ui.components.OmniActionChip
import com.omnimemoria.ui.components.OmniEmptyState
import com.omnimemoria.ui.components.OmniSectionHeader
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.theme.AmberVibe
import com.omnimemoria.ui.theme.RoseMemory
import kotlinx.coroutines.delay

// Albums only show working actions. Curated 'Vibe' collections can return once indexed
// and backed by real filters instead of placeholder cards.

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
                top    = 12.dp,   // app shell reserves room for the header
                bottom = 24.dp,   // app shell reserves room for bottom navigation
                start  = 12.dp,
                end    = 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxSize()
        ) {

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
        com.omnimemoria.ui.components.filters.AlbumSortFilterSheetContent(
            currentSort = sortConfig,
            onDismiss = { showSheet = false },
            onApply = { viewModel.updateFolderSort(it); showSheet = false }
        )
    }
}

// ── Album card ─────────────────────────────────────────────────────────────────

@Composable
private fun AlbumCard(folder: MediaFolder, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Coil resolves the real grid cell dimensions; don't decode every cover at fixed 400x400.
    val coverRequest = remember(folder.coverUri, context) {
        ImageRequest.Builder(context).data(folder.coverUri).build()
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coverRequest,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // One subtle scrim keeps album captions legible regardless of cover brightness.
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.52f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.76f)
                        )
                    )
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color.Black.copy(alpha = 0.48f)
                ) {
                    Text(
                        text = "${folder.photoCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
