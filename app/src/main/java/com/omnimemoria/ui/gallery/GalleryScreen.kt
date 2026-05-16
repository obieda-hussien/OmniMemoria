package com.omnimemoria.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omnimemoria.ui.theme.AmberVibe
import com.omnimemoria.ui.theme.RoseMemory

private data class VibeChip(val label: String, val color: Color)
private val placeholderVibes = listOf(
    VibeChip("Evening\nPhotos", AmberVibe),
    VibeChip("Quiet\nMoments", Color(0xFF2D26A0)),
    VibeChip("Favorites\n", RoseMemory)
)

@Composable
fun GalleryScreen(onPhotoClick: (Long) -> Unit, viewModel: GalleryViewModel = hiltViewModel()) {
    val photos = viewModel.photos.collectAsLazyPagingItems()
    var showFilterSheet by remember { mutableStateOf(false) }
    
    // حفظ حالة السكرول صراحة
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        // المسافات هنا مهمة جداً لتعويض الأشرطة العائمة
        contentPadding = PaddingValues(
            top = 110.dp,    // تعويض ارتفاع TopBar
            bottom = 120.dp, // تعويض ارتفاع BottomNav + FAB
            start = 8.dp,
            end = 8.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Vibe Albums Row
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                items(placeholderVibes) { vibe ->
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(vibe.color)
                            .clickable { /* Handle click */ }
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = vibe.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        // 2. Sort / Filter Header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Photos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showFilterSheet = true }
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Sort Options", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sort", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 3. Photo Grid
        items(photos.itemCount) { index ->
            photos[index]?.let { photo ->
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick(photo.id) }
                )
            }
        }
    }
}
