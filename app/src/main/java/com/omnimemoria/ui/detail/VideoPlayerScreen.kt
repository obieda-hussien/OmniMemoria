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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import kotlinx.coroutines.delay
import kotlin.math.abs

// ── Constants ──────────────────────────────────────────────────────────────────

private const val SKIP_INTERVAL_MS    = 10_000
private const val LONG_PRESS_BOOST_SPEED = 2f
private const val VIDEO_PLAYER_TAG    = "VideoPlayerScreen"

// ── Speed options ──────────────────────────────────────────────────────────────

private data class SpeedOption(val speed: Float, val labelAr: String)

private val SPEED_OPTIONS = listOf(
    SpeedOption(0.2f, "بطيئة جداً"),
    SpeedOption(0.5f, "بطيئة"),
    SpeedOption(1.0f, "الأساسية"),
    SpeedOption(1.5f, "سريعة"),
    SpeedOption(2.0f, "سريعة جداً"),
)

/** e.g. 1.0f → "1x", 0.2f → "0.2x", 2.0f → "2x" */
private fun Float.toSpeedLabel(): String =
    if (this == this.toLong().toFloat()) "${this.toInt()}x" else "${this}x"

// ── Playback helpers ───────────────────────────────────────────────────────────

private enum class PlaybackUriMode { ORIGINAL, VIDEO_COLLECTION, FILES_COLLECTION }

private data class SeekFeedback(val forward: Boolean, val label: String)

// ══════════════════════════════════════════════════════════════════════════════
// Main Screen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun VideoPlayerScreen(
    mediaId:   Long,
    onBack:    () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    // ── State ───────────────────────────────────────────────────────────────────
    var mediaItem             by remember(mediaId) { mutableStateOf<com.omnimemoria.domain.model.MediaPhoto?>(null) }
    var loadedVideoId         by remember { mutableStateOf<Long?>(null) }
    var videoViewRef          by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef        by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared            by remember { mutableStateOf(false) }
    var isPlaying             by remember { mutableStateOf(false) }
    var durationMs            by remember { mutableIntStateOf(0) }
    var positionMs            by remember { mutableIntStateOf(0) }
    var seekPositionMs        by remember { mutableIntStateOf(0) }
    var isSeeking             by remember { mutableStateOf(false) }
    var showControls          by remember { mutableStateOf(true) }
    var playbackSpeed         by remember { mutableStateOf(1f) }
    var isBoostingByLongPress by remember { mutableStateOf(false) }
    var seekFeedback          by remember { mutableStateOf<SeekFeedback?>(null) }
    var playbackUriMode       by remember(mediaId) { mutableStateOf(PlaybackUriMode.ORIGINAL) }
    var showSpeedPanel        by remember { mutableStateOf(false) }
    var isRepeatMode          by remember { mutableStateOf(false) }
    val canControlSpeed        = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    // ── Helpers (capture state by ref inside composable) ────────────────────────

    fun applyPlaybackSpeed(speed: Float) {
        val player = mediaPlayerRef ?: return
        if (!isPrepared || !canControlSpeed) return
        try {
            player.playbackParams = player.playbackParams.setSpeed(speed)
        } catch (e: Exception) {
            Log.w(VIDEO_PLAYER_TAG, "Speed $speed failed: ${e.message}")
        }
    }

    fun seekBy(deltaMs: Int, showFeedback: Boolean = false) {
        val player = videoViewRef ?: return
        if (!isPrepared) return
        val newPos = (player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(newPos)
        seekPositionMs = newPos
        positionMs     = newPos
        if (showFeedback) {
            seekFeedback = SeekFeedback(
                forward = deltaMs >= 0,
                label   = "${if (deltaMs >= 0) "+" else "-"}${abs(deltaMs) / 1000}s"
            )
        }
    }

    fun resolveUri(
        item: com.omnimemoria.domain.model.MediaPhoto,
        mode: PlaybackUriMode
    ): Uri = when (mode) {
        PlaybackUriMode.ORIGINAL ->
            item.uri
        PlaybackUriMode.VIDEO_COLLECTION ->
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id)
        PlaybackUriMode.FILES_COLLECTION ->
            ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id)
    }

    // ── Effects ─────────────────────────────────────────────────────────────────

    // Load media item — reset state when mediaId changes
    LaunchedEffect(mediaId) {
        playbackUriMode = PlaybackUriMode.ORIGINAL
        loadedVideoId   = null
        mediaItem       = viewModel.getPhoto(mediaId)
    }

    // Auto-dismiss seek feedback
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(750)
            seekFeedback = null
        }
    }

    // Smooth seek-bar position tracking (200ms polling)
    LaunchedEffect(isPrepared, isPlaying) {
        while (isPrepared && isPlaying) {
            val player = videoViewRef ?: break
            if (!isSeeking) {
                val dur = player.duration.coerceAtLeast(0)
                val pos = player.currentPosition.coerceAtLeast(0)
                if (durationMs != dur) durationMs = dur
                if (positionMs != pos) { positionMs = pos; seekPositionMs = pos }
            }
            delay(200L)
        }
    }

    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef = null
            videoViewRef?.stopPlayback()
        }
    }

    // ── Root layout ─────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090810))
    ) {
        val item = mediaItem

        // ── Loading ──────────────────────────────────────────────────────────
        if (item == null) {
            CircularProgressIndicator(
                color       = Color(0xFF8B7FF5),
                strokeWidth = 2.dp,
                modifier    = Modifier.align(Alignment.Center).size(36.dp)
            )
            return@Box
        }

        // ── 1. Full-screen VideoView ──────────────────────────────────────────
        // VideoView naturally centres the video and adds letterbox/pillarbox bars.
        // Filling the entire screen gives us proper centre layout without
        // the old 68% height hack.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                VideoView(ctx).apply {
                    videoViewRef = this

                    // FIX: setOnPreparedListener is the ONLY place that calls start().
                    // Calling start() in update before prepared was unreliable on many
                    // devices. Here we let Android prepare async and start in the callback.
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        mp.isLooping   = isRepeatMode
                        isPrepared     = true
                        durationMs     = mp.duration.coerceAtLeast(0)
                        positionMs     = 0
                        seekPositionMs = 0
                        isPlaying      = true
                        // Apply non-default speed before first frame
                        if (canControlSpeed && playbackSpeed != 1f) {
                            try {
                                mp.playbackParams = mp.playbackParams
                                    .setSpeed(if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else playbackSpeed)
                            } catch (_: Exception) {}
                        }
                        mp.start()
                    }

                    setOnCompletionListener {
                        isBoostingByLongPress = false
                        isPlaying             = false
                        positionMs            = durationMs
                        seekPositionMs        = durationMs
                        showControls          = true
                    }

                    setOnErrorListener { _, _, _ ->
                        mediaPlayerRef        = null
                        isBoostingByLongPress = false
                        isPrepared            = false
                        isPlaying             = false
                        // URI fallback chain: ORIGINAL → VIDEO_COLLECTION → FILES_COLLECTION
                        val nextMode = when (playbackUriMode) {
                            PlaybackUriMode.ORIGINAL        -> PlaybackUriMode.VIDEO_COLLECTION
                            PlaybackUriMode.VIDEO_COLLECTION -> PlaybackUriMode.FILES_COLLECTION
                            PlaybackUriMode.FILES_COLLECTION -> null
                        }
                        if (nextMode != null) {
                            playbackUriMode = nextMode
                            loadedVideoId   = null
                            Log.w(VIDEO_PLAYER_TAG, "Retrying with URI mode: $nextMode")
                        } else {
                            Toast.makeText(ctx, "Couldn't play this video.", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                }
            },
            update = { videoView ->
                val targetUri = resolveUri(item, playbackUriMode)
                if (loadedVideoId != item.id) {
                    // Reset everything for the new clip
                    mediaPlayerRef        = null
                    isBoostingByLongPress = false
                    isPrepared            = false
                    isPlaying             = false
                    durationMs            = 0
                    positionMs            = 0
                    seekPositionMs        = 0
                    videoView.setVideoURI(targetUri)
                    videoView.requestFocus()
                    // Do NOT call videoView.start() here — onPreparedListener handles it.
                    // VideoView.start() before prepared is unreliable: on some OEM ROMs it
                    // silently fails, which caused the "must background + foreground to play" bug.
                    loadedVideoId = item.id
                } else if (isPrepared) {
                    // Only update speed (triggered by speed-panel selection)
                    applyPlaybackSpeed(if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else playbackSpeed)
                }
            }
        )

        // ── 2. Gesture overlay (transparent, full-screen) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            // Close speed panel when controls hide
                            if (!showControls) showSpeedPanel = false
                        },
                        onDoubleTap = { offset ->
                            if (!isPrepared || size.width <= 0) return@detectTapGestures
                            seekBy(
                                deltaMs     = if (offset.x >= size.width / 2f) SKIP_INTERVAL_MS else -SKIP_INTERVAL_MS,
                                showFeedback = true
                            )
                        },
                        onLongPress = {
                            if (!isPrepared || !isPlaying || !canControlSpeed || isBoostingByLongPress)
                                return@detectTapGestures
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

        // ── 3. Seek feedback bubbles ──────────────────────────────────────────
        val fb = seekFeedback
        AnimatedVisibility(
            visible  = fb != null,
            enter    = fadeIn() + scaleIn(initialScale = 0.82f),
            exit     = fadeOut() + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(if (fb?.forward == true) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text       = fb?.label.orEmpty(),
                    color      = Color.White,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── 4. Long-press speed-boost indicator ───────────────────────────────
        AnimatedVisibility(
            visible  = isBoostingByLongPress,
            enter    = fadeIn(tween(150)),
            exit     = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF8B7FF5).copy(alpha = 0.88f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text       = "2× Speed",
                    color      = Color.White,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // ── 5. Centre playback controls ───────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn(tween(180)),
            exit     = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                VideoControlButton(
                    icon    = Icons.Filled.FastRewind,
                    onClick = { seekBy(-SKIP_INTERVAL_MS, true) }
                )
                VideoControlButton(
                    icon    = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    size    = 72.dp,
                    onClick = {
                        val player = videoViewRef ?: return@VideoControlButton
                        if (!isPrepared) return@VideoControlButton
                        if (player.isPlaying) { player.pause(); isPlaying = false }
                        else                  { player.start(); isPlaying = true  }
                    }
                )
                VideoControlButton(
                    icon    = Icons.Filled.FastForward,
                    onClick = { seekBy(SKIP_INTERVAL_MS, true) }
                )
            }
        }

        // ── 6. Top bar (slides in from top) ───────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(180)),
            exit     = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            VideoTopBar(
                title            = item.name.substringBeforeLast('.'),
                subtitle         = if (durationMs > 0) formatVideoTime(durationMs) else "—",
                currentSpeed     = playbackSpeed,
                onBack           = onBack,
                onOpenSpeedPanel = { showSpeedPanel = !showSpeedPanel }
            )
        }

        // ── 7. Bottom seek bar (slides in from bottom) ────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(180)),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            VideoBottomBar(
                positionMs     = if (isSeeking) seekPositionMs else positionMs,
                durationMs     = durationMs,
                onSeek         = { value -> isSeeking = true; seekPositionMs = value.toInt() },
                onSeekFinished = {
                    val player = videoViewRef ?: run { isSeeking = false; return@VideoBottomBar }
                    if (isPrepared && durationMs > 0)
                        player.seekTo(seekPositionMs.coerceIn(0, durationMs))
                    positionMs = seekPositionMs
                    isSeeking  = false
                }
            )
        }

        // ── 8. Speed / Options panel (slides in from right) ───────────────────
        AnimatedVisibility(
            visible  = showSpeedPanel,
            enter    = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { it }),
            exit     = fadeOut(tween(160)) + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 80.dp, end = 14.dp)
        ) {
            SpeedPanel(
                currentSpeed     = playbackSpeed,
                isRepeatMode     = isRepeatMode,
                canControlSpeed  = canControlSpeed,
                onSpeedSelect    = { speed ->
                    playbackSpeed = speed
                    applyPlaybackSpeed(speed)
                    showSpeedPanel = false
                },
                onRepeatToggle   = {
                    isRepeatMode           = !isRepeatMode
                    mediaPlayerRef?.isLooping = isRepeatMode
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Top Bar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoTopBar(
    title:            String,
    subtitle:         String,
    currentSpeed:     Float,
    onBack:           () -> Unit,
    onOpenSpeedPanel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Back
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = Color.White,
                modifier           = Modifier.size(20.dp)
            )
        }

        // Title + duration
        Column(
            modifier              = Modifier.weight(1f),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text       = title,
                color      = Color.White.copy(alpha = 0.95f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                textAlign  = TextAlign.Center
            )
            Text(
                text  = subtitle,
                color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Current-speed badge (visible only when speed ≠ 1x)
        if (currentSpeed != 1f) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF2D26A0).copy(alpha = 0.65f))
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                    .clickable(onClick = onOpenSpeedPanel)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = currentSpeed.toSpeedLabel(),
                    color      = Color(0xFF8B7FF5),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(2.dp))
        }

        // Options / speed panel toggle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickable(onClick = onOpenSpeedPanel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Filled.MoreVert,
                contentDescription = "Options",
                tint               = Color.White.copy(alpha = 0.92f),
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Bottom Seek Bar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoBottomBar(
    positionMs:     Int,
    durationMs:     Int,
    onSeek:         (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Slider(
            value               = positionMs.toFloat(),
            onValueChange       = onSeek,
            onValueChangeFinished = onSeekFinished,
            valueRange          = 0f..durationMs.coerceAtLeast(1).toFloat(),
            colors              = SliderDefaults.colors(
                thumbColor        = Color(0xFF8B7FF5),
                activeTrackColor  = Color(0xFF8B7FF5),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = formatVideoTime(positionMs),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text  = if (durationMs > 0)
                    "-${formatVideoTime((durationMs - positionMs).coerceAtLeast(0))}"
                else "--:--",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Centre Control Button
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoControlButton(
    icon:    ImageVector,
    onClick: () -> Unit,
    size:    Dp = 56.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = Color.White.copy(alpha = 0.95f),
            modifier           = Modifier.size(size * 0.5f)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Speed / Options Panel  —  Lumina design system
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedPanel(
    currentSpeed:    Float,
    isRepeatMode:    Boolean,
    canControlSpeed: Boolean,
    onSpeedSelect:   (Float) -> Unit,
    onRepeatToggle:  () -> Unit
) {
    Column(
        modifier = Modifier
            .width(228.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
    ) {

        // ── Header row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Active-speed indicator (left side — LTR for the badge)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF2D26A0))
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(
                    text       = currentSpeed.toSpeedLabel(),
                    color      = Color.White,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Label (right side, Arabic RTL)
            Text(
                text       = "السرعة",
                color      = Color.White.copy(alpha = 0.92f),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(
            color     = Color.White.copy(alpha = 0.07f),
            thickness = 0.5.dp
        )

        // ── Speed options ───────────────────────────────────────────────────────
        SPEED_OPTIONS.forEach { option ->
            val isSelected = currentSpeed == option.speed
            val enabled    = canControlSpeed || option.speed == 1f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) Color(0xFF2D26A0).copy(alpha = 0.30f)
                        else            Color.Transparent
                    )
                    .clickable(enabled = enabled) { onSpeedSelect(option.speed) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Speed number (left)
                Text(
                    text       = option.speed.toSpeedLabel(),
                    color      = when {
                        isSelected -> Color(0xFF8B7FF5)
                        !enabled   -> Color.White.copy(alpha = 0.22f)
                        else       -> Color.White.copy(alpha = 0.60f)
                    },
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                )
                // Arabic label (right)
                Text(
                    text  = option.labelAr,
                    color = when {
                        isSelected -> Color(0xFF8B7FF5).copy(alpha = 0.85f)
                        !enabled   -> Color.White.copy(alpha = 0.18f)
                        else       -> Color.White.copy(alpha = 0.42f)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(
            color     = Color.White.copy(alpha = 0.07f),
            thickness = 0.5.dp
        )

        // ── Repeat ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRepeatToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (isRepeatMode) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                contentDescription = "Repeat",
                tint               = if (isRepeatMode) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.55f),
                modifier           = Modifier.size(20.dp)
            )
            Text(
                text       = "تكرار",
                color      = if (isRepeatMode) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.55f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isRepeatMode) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        HorizontalDivider(
            color     = Color.White.copy(alpha = 0.05f),
            thickness = 0.5.dp
        )

        // ── ChromeCast (UI only) ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* TODO: ChromeCast integration */ }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Outlined.Cast,
                contentDescription = "ChromeCast",
                tint               = Color.White.copy(alpha = 0.38f),
                modifier           = Modifier.size(20.dp)
            )
            Text(
                text  = "كروم كاست",
                color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ── Time formatter ─────────────────────────────────────────────────────────────

private fun formatVideoTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds      = totalSeconds % 60
    val minutes      = (totalSeconds / 60) % 60
    val hours        = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
           else           "%02d:%02d".format(minutes, seconds)
}
