package com.omnimemoria.ui.detail

import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import kotlinx.coroutines.delay

// 45 minutes is treated as long-form content to surface the cinema hint.
private const val LONG_VIDEO_THRESHOLD_MS = 45 * 60 * 1000
private const val LONG_VIDEO_HINT = "Cinema mode ready for long playback"
private const val LIGHT_VIDEO_HINT = "Optimized for smooth lightweight playback"
private const val SKIP_INTERVAL_MS = 10_000

@Composable
fun VideoPlayerScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var mediaItem by remember(mediaId) { mutableStateOf<com.omnimemoria.domain.model.MediaPhoto?>(null) }
    var loadedVideoId by remember { mutableStateOf<Long?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var seekPositionMs by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(mediaId) {
        mediaItem = viewModel.getPhoto(mediaId)
    }

    LaunchedEffect(videoViewRef, isPrepared, isPlaying) {
        if (!isPrepared || !isPlaying || videoViewRef == null) return@LaunchedEffect
        while (isPlaying && videoViewRef != null) {
            val player = videoViewRef ?: break
            if (!isSeeking) {
                val newDuration = player.duration.coerceAtLeast(0)
                val newPosition = player.currentPosition.coerceAtLeast(0)
                if (durationMs != newDuration) durationMs = newDuration
                if (positionMs != newPosition) {
                    positionMs = newPosition
                    seekPositionMs = newPosition
                }
            }
            delay(1000)
        }
    }

    DisposableEffect(videoViewRef) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090810))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        val item = mediaItem
        if (item == null) {
            CircularProgressIndicator(
                color = Color(0xFF8B7FF5),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.68f)
                    .align(Alignment.TopCenter),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this
                        setOnPreparedListener { mp ->
                            isPrepared = true
                            durationMs = mp.duration.coerceAtLeast(0)
                            positionMs = 0
                            seekPositionMs = 0
                            isPlaying = true
                            start()
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            positionMs = durationMs
                            seekPositionMs = durationMs
                            showControls = true
                        }
                        setOnErrorListener { _, _, _ ->
                            Toast.makeText(ctx, "Couldn't play this video.", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
                },
                update = { videoView ->
                    if (loadedVideoId != item.id) {
                        isPrepared = false
                        isPlaying = false
                        durationMs = 0
                        positionMs = 0
                        seekPositionMs = 0
                        videoView.setVideoURI(item.uri)
                        videoView.requestFocus()
                        loadedVideoId = item.id
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(NavigationSurfaceColor)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item?.name?.substringBeforeLast('.') ?: "Video",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (durationMs > 0) formatVideoTime(durationMs) else "Preparing...",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HD",
                        color = Color(0xFF8B7FF5),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(NavigationSurfaceColor)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Slider(
                    value = seekPositionMs.toFloat(),
                    onValueChange = { value ->
                        isSeeking = true
                        seekPositionMs = value.toInt()
                    },
                    onValueChangeFinished = {
                        val player = videoViewRef ?: run {
                            isSeeking = false
                            return@Slider
                        }
                        if (!isPrepared || durationMs <= 0) {
                            isSeeking = false
                            return@Slider
                        }
                        player.seekTo(seekPositionMs.coerceIn(0, durationMs))
                        positionMs = seekPositionMs
                        isSeeking = false
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1).toFloat()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatVideoTime(if (isSeeking) seekPositionMs else positionMs),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (durationMs > 0) {
                            val currentPosition = if (isSeeking) seekPositionMs else positionMs
                            "-${formatVideoTime((durationMs - currentPosition).coerceAtLeast(0))}"
                        } else "--:--",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoControlButton(
                        label = "Rewind",
                        icon = Icons.Filled.FastRewind,
                        onClick = {
                            val player = videoViewRef ?: return@VideoControlButton
                            if (!isPrepared) return@VideoControlButton
                            val newPos = (player.currentPosition - SKIP_INTERVAL_MS).coerceAtLeast(0)
                            player.seekTo(newPos)
                            seekPositionMs = newPos
                            positionMs = newPos
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    VideoControlButton(
                        label = if (isPlaying) "Pause" else "Play",
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onClick = {
                            val player = videoViewRef ?: return@VideoControlButton
                            if (!isPrepared) return@VideoControlButton
                            if (player.isPlaying) player.pause() else player.start()
                            isPlaying = player.isPlaying
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    VideoControlButton(
                        label = "Forward",
                        icon = Icons.Filled.FastForward,
                        onClick = {
                            val player = videoViewRef ?: return@VideoControlButton
                            if (!isPrepared) return@VideoControlButton
                            val max = player.duration.coerceAtLeast(0)
                            val newPos = (player.currentPosition + SKIP_INTERVAL_MS).coerceAtMost(max)
                            player.seekTo(newPos)
                            seekPositionMs = newPos
                            positionMs = newPos
                        }
                    )
                }
                Text(
                    text = if (durationMs >= LONG_VIDEO_THRESHOLD_MS) LONG_VIDEO_HINT else LIGHT_VIDEO_HINT,
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoControlButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatVideoTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
