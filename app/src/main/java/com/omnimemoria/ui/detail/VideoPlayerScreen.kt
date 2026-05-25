package com.omnimemoria.ui.detail

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val SKIP_INTERVAL_MS = 10_000
private const val LONG_PRESS_BOOST_SPEED = 2f
private const val CONTROLS_AUTO_HIDE_MS = 2_800L
private const val VIDEO_PLAYER_TAG = "VideoPlayerScreen"
private val SPEED_OPTIONS = listOf(1f, 1.25f, 1.5f, 2f)

private enum class PlaybackUriMode { ORIGINAL, VIDEO_COLLECTION, FILES_COLLECTION }

private data class SeekFeedback(
    val forward: Boolean,
    val label: String
)

private data class VideoTechnicalMetadata(
    val durationMs: Int?,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?,
    val frameRate: Float?,
    val mimeType: String?
)

@Composable
fun VideoPlayerScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val context = androidx.compose.ui.platform.LocalContext.current
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
    var showMetadataCard by remember { mutableStateOf(false) }
    var playbackIssueMessage by remember { mutableStateOf<String?>(null) }
    var videoMetadata by remember(mediaId) { mutableStateOf<VideoTechnicalMetadata?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var isRepeatMode by remember { mutableStateOf(false) }
    val canControlSpeed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun resetPlaybackAttemptState() {
        playbackUriMode = PlaybackUriMode.ORIGINAL
        playbackIssueMessage = null
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

    fun retryPlayback() {
        mediaPlayerRef = null
        isBoostingByLongPress = false
        isPrepared = false
        isPlaying = false
        durationMs = 0
        positionMs = 0
        seekPositionMs = 0
        playbackIssueMessage = null
        loadedVideoId = null
    }
    LaunchedEffect(mediaId) {
        resetPlaybackAttemptState()
        mediaItem = viewModel.getPhoto(mediaId)
    }

    LaunchedEffect(mediaItem?.id, playbackUriMode) {
        val item = mediaItem ?: run {
            videoMetadata = null
            return@LaunchedEffect
        }
        videoMetadata = extractVideoMetadata(context, resolvePlaybackUri(item, playbackUriMode))
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback == null) return@LaunchedEffect
        delay(700)
        seekFeedback = null
    }

    LaunchedEffect(showControls, isPlaying, isPrepared) {
        if (!showControls || !isPlaying || !isPrepared) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        if (isPlaying && !isSeeking && !showOptionsMenu) showControls = false
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
                                mp.isLooping = isRepeatMode
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
                                    playbackIssueMessage = "Trying a more compatible source…"
                                    Log.w(VIDEO_PLAYER_TAG, "Retrying video playback with URI mode: $nextMode")
                                    return@setOnErrorListener true
                                }
                                playbackIssueMessage = "Playback failed on this device. Retry or open externally."
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
                            playbackIssueMessage = null
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
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CenterVideoControlButton(
                            icon = Icons.Filled.FastRewind,
                            contentDescription = "Rewind",
                            onClick = { seekBy(-SKIP_INTERVAL_MS, showFeedback = true) }
                        )
                        CenterVideoControlButton(
                            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            onClick = {
                                val player = videoViewRef ?: return@CenterVideoControlButton
                                if (!isPrepared) return@CenterVideoControlButton
                                if (player.isPlaying) player.pause() else player.start()
                                isPlaying = player.isPlaying
                            }
                        )
                        CenterVideoControlButton(
                            icon = Icons.Filled.FastForward,
                            contentDescription = "Forward",
                            onClick = { seekBy(SKIP_INTERVAL_MS, showFeedback = true) }
                        )
                    }

                    AnimatedVisibility(
                        visible = playbackIssueMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = playbackIssueMessage.orEmpty(),
                                color = Color.White.copy(alpha = 0.94f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { retryPlayback() }) {
                                    Text("Retry")
                                }
                                TextButton(
                                    onClick = {
                                        val target = mediaItem ?: return@TextButton
                                        openInExternalPlayer(
                                            context = context,
                                            uri = resolvePlaybackUri(target, playbackUriMode)
                                        )
                                    }
                                ) {
                                    Text("Open externally")
                                }
                            }
                        }
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
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { showOptionsMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White.copy(alpha = 0.92f)
                    )
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Turn repeat mode ${if (isRepeatMode) "off" else "on"}")
                            },
                            onClick = {
                                isRepeatMode = !isRepeatMode
                                mediaPlayerRef?.isLooping = isRepeatMode
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showMetadataCard) "Hide details" else "Show details") },
                            onClick = {
                                showMetadataCard = !showMetadataCard
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Retry playback") },
                            onClick = {
                                retryPlayback()
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in another app") },
                            onClick = {
                                val target = item ?: return@DropdownMenuItem
                                openInExternalPlayer(
                                    context = context,
                                    uri = resolvePlaybackUri(target, playbackUriMode)
                                )
                                showOptionsMenu = false
                            }
                        )
                        SPEED_OPTIONS.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            DropdownMenuItem(
                                text = {
                                    Text(text = "Speed: ${speed}x")
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected"
                                        )
                                    }
                                } else null,
                                onClick = {
                                    playbackSpeed = speed
                                    applyPlaybackSpeed(if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else speed)
                                    showOptionsMenu = false
                                },
                                enabled = canControlSpeed
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && showMetadataCard,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 116.dp)
        ) {
            VideoMetadataCard(
                media = item,
                metadata = videoMetadata,
                playbackUriMode = playbackUriMode
            )
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
            }
        }
    }
}

@Composable
private fun CenterVideoControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(30.dp)
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

private fun extractVideoMetadata(context: Context, uri: Uri): VideoTechnicalMetadata? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            VideoTechnicalMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull(),
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            )
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun openInExternalPlayer(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open video with"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No compatible app found.", Toast.LENGTH_SHORT).show()
    } catch (exception: Exception) {
        Log.w(VIDEO_PLAYER_TAG, "Failed to open external player", exception)
        Toast.makeText(context, "Could not open external player.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun VideoMetadataCard(
    media: com.omnimemoria.domain.model.MediaPhoto?,
    metadata: VideoTechnicalMetadata?,
    playbackUriMode: PlaybackUriMode
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sizeText = media?.size?.let { Formatter.formatFileSize(context, it) } ?: "—"
    val resolutionText = buildString {
        val width = metadata?.width ?: media?.width
        val height = metadata?.height ?: media?.height
        if ((width ?: 0) > 0 && (height ?: 0) > 0) append("${width} × ${height}") else append("—")
    }
    val bitrateText = metadata?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—"
    val frameRateText = metadata?.frameRate?.takeIf { it > 0f }?.let { "${"%.2f".format(it)} fps" } ?: "—"
    val formatText = (metadata?.mimeType ?: media?.mimeType).orEmpty().ifBlank { "—" }
    val sourceText = when (playbackUriMode) {
        PlaybackUriMode.ORIGINAL -> "Original"
        PlaybackUriMode.VIDEO_COLLECTION -> "Video collection fallback"
        PlaybackUriMode.FILES_COLLECTION -> "Files collection fallback"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoMetaRow("Format", formatText)
        VideoMetaRow("Size", sizeText)
        VideoMetaRow("Resolution", resolutionText)
        VideoMetaRow("Bitrate", bitrateText)
        VideoMetaRow("Frame rate", frameRateText)
        VideoMetaRow("Source", sourceText)
    }
}

@Composable
private fun VideoMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.54f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
