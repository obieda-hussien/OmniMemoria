package com.omnimemoria.ui.detail

import android.content.ContentUris
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import kotlinx.coroutines.delay
import kotlin.math.abs

// 45 minutes is treated as long-form content to surface the cinema hint.
private const val LONG_VIDEO_THRESHOLD_MS = 45 * 60 * 1000
private const val LONG_VIDEO_HINT = "Cinema mode ready for long playback"
private const val LIGHT_VIDEO_HINT = "Optimized for smooth lightweight playback"
private const val SKIP_INTERVAL_MS = 10_000
private const val LONG_PRESS_BOOST_SPEED = 2f
private const val VIDEO_PLAYER_TAG = "VideoPlayerScreen"
private val SPEED_OPTIONS = listOf(1f, 1.25f, 1.5f, 2f)

private enum class PlaybackUriMode { ORIGINAL, VIDEO_COLLECTION, FILES_COLLECTION }

private data class SeekFeedback(
    val forward: Boolean,
    val label: String
)

@Composable
fun VideoPlayerScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    var mediaItem by remember(mediaId) { mutableStateOf<com.omnimemoria.domain.model.MediaPhoto?>(null) }
    var loadedVideoId by remember { mutableStateOf<Long?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var seekPositionMs by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isBoostingByLongPress by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var playbackUriMode by remember(mediaId) { mutableStateOf(PlaybackUriMode.ORIGINAL) }
    val canControlSpeed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun resetPlaybackAttemptState() {
        playbackUriMode = PlaybackUriMode.ORIGINAL
        loadedVideoId = null
    }

    fun resolvePlaybackUri(item: com.omnimemoria.domain.model.MediaPhoto, mode: PlaybackUriMode): Uri {
        return when (mode) {
            PlaybackUriMode.ORIGINAL -> item.uri
            PlaybackUriMode.VIDEO_COLLECTION -> {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id)
            }
            PlaybackUriMode.FILES_COLLECTION -> {
                ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id)
            }
        }
    }

    fun applyPlaybackSpeed(speed: Float) {
        val player = mediaPlayerRef ?: return
        if (!isPrepared || !canControlSpeed) return
        try {
            val currentParams = player.playbackParams
            player.playbackParams = currentParams.setSpeed(speed)
        } catch (exception: Exception) {
            Log.w(VIDEO_PLAYER_TAG, "Failed to apply playback speed: $speed", exception)
        }
    }

    fun seekBy(deltaMs: Int, showFeedback: Boolean = false) {
        val player = videoViewRef ?: return
        if (!isPrepared) return
        val max = player.duration.coerceAtLeast(0)
        val newPos = (player.currentPosition + deltaMs).coerceIn(0, max)
        player.seekTo(newPos)
        seekPositionMs = newPos
        positionMs = newPos
        if (showFeedback) {
            val displaySeconds = abs(deltaMs) / 1000
            seekFeedback = SeekFeedback(
                forward = deltaMs >= 0,
                label = "${if (deltaMs >= 0) "+" else "-"}${displaySeconds}s"
            )
        }
    }
    LaunchedEffect(mediaId) {
        resetPlaybackAttemptState()
        mediaItem = viewModel.getPhoto(mediaId)
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback == null) return@LaunchedEffect
        delay(700)
        seekFeedback = null
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
            mediaPlayerRef = null
            videoViewRef?.stopPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090810))
    ) {
        val item = mediaItem
        if (item == null) {
            CircularProgressIndicator(
                color = Color(0xFF8B7FF5),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.68f)
                    .align(Alignment.TopCenter)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            videoViewRef = this
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                isPrepared = true
                                durationMs = mp.duration.coerceAtLeast(0)
                                positionMs = 0
                                seekPositionMs = 0
                                isPlaying = true
                                val targetSpeed = if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else playbackSpeed
                                applyPlaybackSpeed(targetSpeed)
                                start()
                            }
                            setOnCompletionListener {
                                isBoostingByLongPress = false
                                isPlaying = false
                                positionMs = durationMs
                                seekPositionMs = durationMs
                                showControls = true
                            }
                            setOnErrorListener { _, _, _ ->
                                mediaPlayerRef = null
                                isBoostingByLongPress = false
                                isPrepared = false
                                isPlaying = false
                                val nextMode = when (playbackUriMode) {
                                    PlaybackUriMode.ORIGINAL -> PlaybackUriMode.VIDEO_COLLECTION
                                    PlaybackUriMode.VIDEO_COLLECTION -> PlaybackUriMode.FILES_COLLECTION
                                    PlaybackUriMode.FILES_COLLECTION -> null
                                }
                                if (nextMode != null) {
                                    playbackUriMode = nextMode
                                    loadedVideoId = null
                                    Log.w(VIDEO_PLAYER_TAG, "Retrying video playback with URI mode: $nextMode")
                                    return@setOnErrorListener true
                                }
                                Toast.makeText(
                                    ctx,
                                    "Couldn't play this video. It may be missing or unsupported.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }
                    },
                    update = { videoView ->
                        val targetUri = resolvePlaybackUri(item, playbackUriMode)
                        if (loadedVideoId != item.id) {
                            mediaPlayerRef = null
                            isBoostingByLongPress = false
                            isPrepared = false
                            isPlaying = false
                            durationMs = 0
                            positionMs = 0
                            seekPositionMs = 0
                            videoView.setVideoURI(targetUri)
                            videoView.requestFocus()
                            videoView.start()
                            loadedVideoId = item.id
                        } else if (isPrepared) {
                            val targetSpeed = if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else playbackSpeed
                            applyPlaybackSpeed(targetSpeed)
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = { offset ->
                                    if (!isPrepared) return@detectTapGestures
                                    // Guard transient invalid size before first stable layout is available.
                                    if (size.width <= 0) return@detectTapGestures
                                    val splitX = size.width / 2f
                                    val seekForward = offset.x >= splitX
                                    seekBy(
                                        deltaMs = if (seekForward) SKIP_INTERVAL_MS else -SKIP_INTERVAL_MS,
                                        showFeedback = true
                                    )
                                },
                                onLongPress = {
                                    if (!isPrepared || !isPlaying || !canControlSpeed || isBoostingByLongPress) {
                                        return@detectTapGestures
                                    }
                                    isBoostingByLongPress = true
                                    applyPlaybackSpeed(LONG_PRESS_BOOST_SPEED)
                                },
                                onPress = {
                                    tryAwaitRelease()
                                    if (isBoostingByLongPress) {
                                        isBoostingByLongPress = false
                                        applyPlaybackSpeed(playbackSpeed)
                                    }
                                }
                            )
                        }
                )
                val feedback = seekFeedback
                AnimatedVisibility(
                    visible = feedback != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(if (feedback?.forward == true) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = feedback?.label.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
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
                            seekBy(-SKIP_INTERVAL_MS, showFeedback = true)
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
                            seekBy(SKIP_INTERVAL_MS, showFeedback = true)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SPEED_OPTIONS.forEach { speed ->
                        SpeedChip(
                            speed = speed,
                            selected = playbackSpeed == speed,
                            enabled = isPrepared && canControlSpeed,
                            onClick = {
                                playbackSpeed = speed
                                applyPlaybackSpeed(speed)
                            }
                        )
                    }
                }
                Text(
                    text = if (canControlSpeed) {
                        if (isBoostingByLongPress) "Boost active: 2x (release to restore normal speed)"
                        else "Long press video area for temporary 2x boost"
                    } else "Speed controls unavailable on this Android version",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = if (canControlSpeed) {
                                if (isBoostingByLongPress) {
                                    "Instruction: release to restore normal playback speed."
                                } else {
                                    "Instruction: long press the video area for temporary two times speed boost."
                                }
                            } else {
                                "Instruction: speed controls are unavailable on this Android version."
                            }
                        }
                        .padding(top = 8.dp)
                )
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
private fun SpeedChip(
    speed: Float,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        selected -> Color(0xFF8B7FF5).copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val textColor = when {
        !enabled -> Color.White.copy(alpha = 0.45f)
        selected -> Color(0xFFC8C0FF)
        else -> Color.White.copy(alpha = 0.82f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.25f else 0.1f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${speed}x",
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
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
