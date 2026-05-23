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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.omnimemoria.ui.components.ShimmerBox

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDetailScreen(
    onPhotoClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel()
) {
    val photos = viewModel.photos.collectAsLazyPagingItems()
    val folder by viewModel.folder.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0914)) // خلفية داكنة OLED فاخرة للألبومات
    ) {
        // 1. الخلفية الضبابية العلوية (Immersive Blurred Banner)
        folder?.let {
            AsyncImage(
                model = it.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .blur(24.dp)
                    .scale(1.2f)
                    .alpha(0.35f)
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = folder?.name ?: "Album",
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSortSheet = true },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Outlined.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // الكرت الرئيسي المميز للألبوم داخل الجريد
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp, start = 4.dp, end = 4.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            border = BoxedBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    folder?.let {
                                        AsyncImage(
                                            model = it.coverUri,
                                            contentDescription = it.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } ?: ShimmerBox(Modifier.fillMaxSize())
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = folder?.name ?: "Loading...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Local Media Storage Directory",
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    if (photos.loadState.refresh is LoadState.Loading) {
                        items(18) {
                            ShimmerBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                        }
                    } else {
                        items(
                            count = photos.itemCount,
                            key = { i -> photos[i]?.id ?: "p_$i" }
                        ) { index ->
                            photos[index]?.let { photo ->
                                FolderPhotoCell(
                                    photo = photo,
                                    selected = photo.id in selectedIds,
                                    selecting = selectedIds.isNotEmpty(),
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) viewModel.toggleSelection(photo.id)
                                        else onPhotoClick(photo.id)
                                    },
                                    onLongClick = { viewModel.toggleSelection(photo.id) }
                                )
                            } ?: ShimmerBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                        }
                    }
                }

                // شريط الخيارات الزجاجي الفاخر عند التحديد المتعدد
                AnimatedVisibility(
                    visible = selectedIds.isNotEmpty(),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(20.dp)
                ) {
                    FolderSelectionBar(
                        count = selectedIds.size,
                        onClose = { viewModel.clearSelection() },
                        onShare = {},
                        onDelete = {},
                        onMore = {}
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        SimpleFolderSortSheet(
            onDismiss = { showSortSheet = false },
            onApply = {
                viewModel.updateSort(it)
                showSortSheet = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderPhotoCell(
    photo: MediaPhoto, selected: Boolean, selecting: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (selected) 0.9f else 1f, label = "cell_scale")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selecting) {
            Box(modifier = Modifier.fillMaxSize().background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)))
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderSelectionBar(count: Int, onClose: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141220).copy(alpha = 0.9f),
        border = borderStrokeTokens()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.ArrowBack, "Close", tint = Color.White) }
                Text("$count items selected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Row {
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, null, tint = Color.White) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF5252)) }
                IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, null, tint = Color.White) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleFolderSortSheet(onDismiss: () -> Unit, onApply: (SortConfig) -> Unit) {
    var sortBy by remember { mutableStateOf(SortBy.DATE_TAKEN) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF141220)) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Sort Album Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            listOf(
                SortBy.DATE_TAKEN to "Latest Photo First",
                SortBy.NAME to "Alphabetical Name A-Z",
                SortBy.SIZE to "Largest File Size"
            ).forEach { (candidate, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { sortBy = candidate }).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = sortBy == candidate, onClick = { sortBy = candidate }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                    Text(label, color = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = sortOrder == SortOrder.DESCENDING, onClick = { sortOrder = SortOrder.DESCENDING }, label = { Text("Descending ↓") })
                FilterChip(selected = sortOrder == SortOrder.ASCENDING, onClick = { sortOrder = SortOrder.ASCENDING }, label = { Text("Ascending ↑") })
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onApply(SortConfig(sortBy = sortBy, sortOrder = sortOrder)) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Apply Sorting", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun borderStrokeTokens() = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
private fun BoxedBorder() = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
