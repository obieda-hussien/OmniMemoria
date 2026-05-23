package com.omnimemoria.ui.gallery

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
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.components.ShimmerBox
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable

private val SelectionBarBottomPadding = 148.dp

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
        snapshotFlow { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 100 }
            .collect { compact -> viewModel.setCompactTopBar(compact) }
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
            // تَمّت إزالة سطر الـ VibeAlbumsRow من هنا بنجاح بناءً على طلبك
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionSubHeader(
                    title     = when(currentFilter) {
                        MediaFilter.ALL -> "All Media"
                        MediaFilter.PHOTOS_ONLY -> "Photos Only"
                        MediaFilter.VIDEOS_ONLY -> "Videos Only"
                    },
                    count     = mediaStats.totalCount,
                    isLoading = groupedPhotos.loadState.refresh is LoadState.Loading,
                    onSort    = { showSortFilterSheet = true },
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
            SelectionActionBar(
                count    = selectedIds.size,
                onClose  = { viewModel.clearSelection() },
                onShare  = { /* TODO */ },
                onDelete = { /* TODO */ },
                onMore   = { /* TODO */ }
            )
        }
    }

    if (showSortFilterSheet) {
        GallerySortFilterSheet(
            currentFilter = currentFilter,
            currentSort   = sortConfig,
            onDismiss     = { showSortFilterSheet = false },
            onApply       = { selectedSort, selectedFilter ->
                viewModel.updateSortAndFilter(selectedSort, selectedFilter)
                showSortFilterSheet = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .combinedClickable(onClick = onSort)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Outlined.Tune, "Filter/Sort", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Filter & Sort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DateHeaderRow(label: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoCell(
    uri: String, photoId: Long, isVideo: Boolean, isSelected: Boolean, isSelecting: Boolean,
    sharedTransitionScope: SharedTransitionScope?, animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 0.88f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "photo_scale")
    val sharedModifier: Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && !isSelecting) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(rememberSharedContentState(key = photoSharedKey(photoId)), animatedVisibilityScope = animatedVisibilityScope, boundsTransform = com.omnimemoria.ui.detail.photosBoundsTransform)
        }
    } else Modifier

    Box(modifier = Modifier.aspectRatio(1f).scale(scale).clip(RoundedCornerShape(if (isSelected) 14.dp else 10.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        CachedThumbnail(uri = uri, modifier = Modifier.fillMaxSize().then(sharedModifier))
        if (isVideo) {
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        AnimatedVisibility(visible = isSelecting, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.15f))) {
                if (isSelected) {
                    Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
                } else {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun CachedThumbnail(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(model = ImageRequest.Builder(context).data(uri).size(Size(512, 512)).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
private fun SkeletonPhotoCell(size: Dp) {
    ShimmerBox(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
}

@Composable
private fun SelectionActionBar(count: Int, onClose: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onMore: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF1E1C30).copy(alpha = 0.95f), Color(0xFF2D26A0).copy(alpha = 0.9f))))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null, tint = Color.White) }
                Text(text = "$count selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row {
                IconButton(onClick = onShare)  { Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onMore)   { Icon(Icons.Outlined.MoreVert, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

// ورقة الفلترة والترتيب المدمجة الحديثة ذات التصميم الفاخر
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GallerySortFilterSheet(
    currentFilter: MediaFilter,
    currentSort: SortConfig,
    onDismiss: () -> Unit,
    onApply: (SortConfig, MediaFilter) -> Unit
) {
    var sortBy by remember { mutableStateOf(currentSort.sortBy) }
    var sortOrder by remember { mutableStateOf(currentSort.sortOrder) }
    var filterBy by remember { mutableStateOf(currentFilter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141220),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Filter & Sort Media", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // قسم الفلترة
            Text("Show Content Type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MediaFilter.entries.forEach { target ->
                    val isSelected = filterBy == target
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterBy = target },
                        label = { Text(when(target){
                            MediaFilter.ALL -> "All Media"
                            MediaFilter.PHOTOS_ONLY -> "Photos Only"
                            MediaFilter.VIDEOS_ONLY -> "Videos Only"
                        }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // قسم الترتيب
            Text("Sort By", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            listOf(
                SortBy.DATE_TAKEN to "Date Taken",
                SortBy.NAME to "File Name",
                SortBy.SIZE to "Storage Size"
            ).forEach { (candidate, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if(sortBy == candidate) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                        .combinedClickable { sortBy = candidate }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortBy == candidate, 
                        onClick = { sortBy = candidate },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White)
                }
            }

            // اتجاه الترتيب
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sortOrder == SortOrder.DESCENDING,
                    onClick = { sortOrder = SortOrder.DESCENDING },
                    label = { Text("Newest / Largest First ↓") }
                )
                FilterChip(
                    selected = sortOrder == SortOrder.ASCENDING,
                    onClick = { sortOrder = SortOrder.ASCENDING },
                    label = { Text("Oldest / Smallest First ↑") }
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onApply(SortConfig(sortBy = sortBy, sortOrder = sortOrder), filterBy) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply Configurations", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
