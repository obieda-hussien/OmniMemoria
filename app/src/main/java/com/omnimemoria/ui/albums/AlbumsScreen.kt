package com.omnimemoria.ui.albums

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.FolderSortBy
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.MediaFolder
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.components.ShimmerBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onFolderClick: (String) -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val folders = viewModel.folders.collectAsLazyPagingItems()
    val sortConfig by viewModel.folderSortConfig.collectAsState()
    var showSortSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Sort")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                folders.loadState.refresh is LoadState.Loading -> {
                    AlbumsSkeleton()
                }

                folders.itemCount == 0 -> {
                    EmptyAlbumsState()
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = folders.itemCount,
                            key = { index -> folders[index]?.bucketId ?: "folder_$index" }
                        ) { index ->
                            folders[index]?.let { folder ->
                                AlbumCard(
                                    folder = folder,
                                    index = index,
                                    onClick = { onFolderClick(folder.bucketId) }
                                )
                            } ?: AlbumSkeletonCard()
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        FolderSortSheet(
            initial = sortConfig,
            onDismiss = { showSortSheet = false },
            onApply = {
                viewModel.updateFolderSort(it)
                showSortSheet = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    folder: MediaFolder,
    index: Int,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val enterOffset = remember { Animatable(32f) }
    LaunchedEffect(folder.bucketId) {
        kotlinx.coroutines.delay(index * 40L)
        enterOffset.animateTo(0f, animationSpec = tween(260))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { translationY = enterOffset.value }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = folder.coverUri,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
            Text(
                text = folder.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
            Text(
                text = "${folder.photoCount} photos",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = Color.White)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Share All") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(text = { Text("Select") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(text = { Text("Sort Inside") }, onClick = { menuExpanded = false })
                }
            }
        }
    }
}

@Composable
private fun AlbumsSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) { AlbumSkeletonCard() }
    }
}

@Composable
private fun AlbumSkeletonCard() {
    ShimmerBox(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
private fun EmptyAlbumsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text("No albums yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Take a photo to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSortSheet(
    initial: FolderSortConfig,
    onDismiss: () -> Unit,
    onApply: (FolderSortConfig) -> Unit
) {
    var selectedSortBy by remember(initial) { mutableStateOf(initial.sortBy) }
    var selectedOrder by remember(initial) { mutableStateOf(initial.sortOrder) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sort albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            SortRadioRow(
                label = "Latest Photo",
                selected = selectedSortBy == FolderSortBy.DATE_LATEST_PHOTO,
                onSelect = { selectedSortBy = FolderSortBy.DATE_LATEST_PHOTO }
            )
            SortRadioRow(
                label = "Name A-Z",
                selected = selectedSortBy == FolderSortBy.NAME,
                onSelect = {
                    selectedSortBy = FolderSortBy.NAME
                    if (selectedOrder == SortOrder.DESCENDING) selectedOrder = SortOrder.ASCENDING
                }
            )
            SortRadioRow(
                label = "Most Photos",
                selected = selectedSortBy == FolderSortBy.PHOTO_COUNT,
                onSelect = { selectedSortBy = FolderSortBy.PHOTO_COUNT }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedOrder == SortOrder.DESCENDING,
                    onClick = { selectedOrder = SortOrder.DESCENDING },
                    label = { Text("Newest ↓") }
                )
                FilterChip(
                    selected = selectedOrder == SortOrder.ASCENDING,
                    onClick = { selectedOrder = SortOrder.ASCENDING },
                    label = { Text("Oldest ↑") }
                )
            }

            Button(
                onClick = { onApply(FolderSortConfig(selectedSortBy, selectedOrder)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun SortRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
