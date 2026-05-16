package com.omnimemoria.ui.detail

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoSizeSelectActual
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.domain.model.MediaPhoto
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
fun PhotoDetailScreen(
    photoId: Long,
    onBack: () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val photo by produceState<MediaPhoto?>(initialValue = null, key1 = photoId) {
        value = viewModel.getPhoto(photoId)
    }
    
    var isFavorite by remember(photoId) { mutableStateOf(false) }
    // حالة جديدة للتحكم في ظهور تفاصيل الصورة
    var showMetadata by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Zoomable Image (Full Screen)
        ZoomableAsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = photo?.uri,
            contentDescription = photo?.name
        )

        // 2. Floating Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // زر إظهار التفاصيل (يتغير لونه عند التفعيل)
            IconButton(
                onClick = { showMetadata = !showMetadata },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (showMetadata) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Outlined.Info, contentDescription = "Info", tint = Color.White)
            }
        }

        // 3. Metadata Overlay Card (Animated)
        AnimatedVisibility(
            visible = showMetadata,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp) // ترتفع فوق شريط الأزرار السفلي
                .padding(horizontal = 16.dp)
        ) {
            PhotoMetadataCard(photo = photo)
        }

        // 4. Floating Bottom Actions Bar (Glassmorphism style)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IconButton(onClick = { }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
            IconButton(onClick = { isFavorite = !isFavorite }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color.Red else Color.White
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
    }
}

// تصميم عصري ونظيف لبطاقة عرض التفاصيل بدلاً من النصوص العادية
@Composable
private fun PhotoMetadataCard(photo: MediaPhoto?) {
    val context = LocalContext.current
    val dateText = photo?.dateTaken?.takeIf { it > 0 }?.let {
        DateFormat.getDateTimeInstance().format(Date(it))
    } ?: "Unknown Date"
    val sizeText = photo?.size?.let { Formatter.formatFileSize(context, it) } ?: "Unknown Size"
    val resolutionText = photo?.let { "${it.width} × ${it.height}" } ?: "Unknown Resolution"
    val locationText = if (photo?.latitude != null && photo.longitude != null) {
        "${photo.latitude}, ${photo.longitude}"
    } else {
        "Location Unavailable"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetadataRow(icon = Icons.Outlined.DateRange, text = dateText)
        MetadataRow(icon = Icons.Outlined.SdStorage, text = sizeText)
        MetadataRow(icon = Icons.Outlined.PhotoSizeSelectActual, text = resolutionText)
        MetadataRow(icon = Icons.Outlined.LocationOn, text = locationText)
    }
}

@Composable
private fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}
