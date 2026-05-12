package com.omnimemoria.ui.detail

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    val photo = viewModel.getPhoto(photoId)
    var isFavorite by remember(photoId) { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = { }) { Text("Share") }
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Text(if (isFavorite) "♥" else "♡")
                    }
                    IconButton(onClick = { }) { Text("Delete") }
                    IconButton(onClick = { }) { Text("More") }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            ZoomableAsyncImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                model = photo?.uri,
                contentDescription = photo?.name
            )
            PhotoMetadata(photo = photo)
        }
    }
}

@Composable
private fun PhotoMetadata(photo: MediaPhoto?) {
    val context = LocalContext.current
    val dateText = photo?.dateTaken?.takeIf { it > 0 }?.let {
        DateFormat.getDateTimeInstance().format(Date(it))
    } ?: "Unknown"
    val sizeText = photo?.size?.let { Formatter.formatFileSize(context, it) } ?: "Unknown"
    val resolutionText = photo?.let { "${it.width} × ${it.height}" } ?: "Unknown"
    val locationText = if (photo?.latitude != null && photo.longitude != null) {
        "${photo.latitude}, ${photo.longitude}"
    } else {
        "Unavailable"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Date: $dateText", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Size: $sizeText", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Resolution: $resolutionText", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Location: $locationText", style = MaterialTheme.typography.bodyMedium)
    }
}
