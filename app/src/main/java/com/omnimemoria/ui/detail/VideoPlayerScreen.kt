package com.omnimemoria.ui.detail

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.widget.Toast
import android.widget.VideoView
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.omnimemoria.ui.components.MediaChromeCorner
import com.omnimemoria.ui.components.OmniMediaTopBar
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════════

private const val SKIP_MS                  = 10_000
private const val LONG_PRESS_SPEED         = 2f
private const val VIDEO_TAG                = "VideoPlayerScreen"
private const val AUTO_HIDE_DELAY_MS       = 3_500L
private const val POSITION_POLL_MS         = 200L
private const val FEEDBACK_DURATION_MS     = 850L
private const val GESTURE_HINT_DURATION_MS = 1_200L

// ── Speed options ──────────────────────────────────────────────────────────────

private data class SpeedOption(val speed: Float, val label: String)

private val SPEED_OPTIONS = listOf(
    SpeedOption(0.2f, "Very Slow"),
    SpeedOption(0.5f, "Slow"),
    SpeedOption(1.0f, "Normal"),
    SpeedOption(1.5f, "Fast"),
    SpeedOption(2.0f, "Very Fast"),
)

private fun Float.toSpeedLabel(): String =
    if (this == this.toLong().toFloat()) "${this.toInt()}x" else "${this}x"

// ── Playback URI fallback chain ────────────────────────────────────────────────

private enum class PlaybackUriMode { ORIGINAL, VIDEO_COLLECTION, FILES_COLLECTION }

private fun PlaybackUriMode.next(): PlaybackUriMode? = when (this) {
    PlaybackUriMode.ORIGINAL         -> PlaybackUriMode.VIDEO_COLLECTION
    PlaybackUriMode.VIDEO_COLLECTION -> PlaybackUriMode.FILES_COLLECTION
    PlaybackUriMode.FILES_COLLECTION -> null
}

// ── Gesture indicator ──────────────────────────────────────────────────────────

private data class GestureIndicator(
    val icon:   ImageVector,
    val label:  String,
    val value:  Float,
    val onLeft: Boolean
)

// ── ChromeCast state ───────────────────────────────────────────────────────────

private enum class CastState { UNAVAILABLE, SEARCHING, AVAILABLE, CONNECTED }

// ── Seek feedback ──────────────────────────────────────────────────────────────

private data class SeekFeedback(val seconds: Int, val forward: Boolean)

// ── Time formatter ─────────────────────────────────────────────────────────────

private fun formatTime(ms: Int): String {
    val s   = (ms / 1000).coerceAtLeast(0)
    val h   = s / 3600
    val m   = (s / 60) % 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

// ══════════════════════════════════════════════════════════════════════════════
// Main Screen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun VideoPlayerScreen(
    mediaId:        Long,
    externalUriStr: String? = null,
    onBack:         () -> Unit,
    viewModel:      PhotoDetailViewModel = hiltViewModel()
) {
    val context       = LocalContext.current
    val activity      = context as? Activity
    val audioManager  = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume     = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val supportsPiP   = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val canSetSpeed   = true   // minSdk=24, playbackParams доступен с API 23

    // ── PiP mode detection via Lifecycle ──────────────────────────────────────
    // ON_PAUSE + isInPictureInPictureMode → entered PiP
    // ON_RESUME                           → exited PiP  (or came back from bg)
    var isInPiP by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> isInPiP = activity?.isInPictureInPictureMode ?: false
                Lifecycle.Event.ON_RESUME -> isInPiP = false
                else                      -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Playback state ─────────────────────────────────────────────────────────
    var mediaItem       by remember(mediaId, externalUriStr) { mutableStateOf<com.omnimemoria.domain.model.MediaPhoto?>(null) }
    var loadedVideoId   by remember { mutableStateOf<Long?>(null) }
    var videoViewRef    by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef  by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared      by remember { mutableStateOf(false) }
    var isPlaying       by remember { mutableStateOf(false) }
    var durationMs      by remember { mutableIntStateOf(0) }
    var positionMs      by remember { mutableIntStateOf(0) }
    var seekPositionMs  by remember { mutableIntStateOf(0) }
    var isSeeking       by remember { mutableStateOf(false) }
    var playbackUriMode by remember(mediaId, externalUriStr) { mutableStateOf(PlaybackUriMode.ORIGINAL) }

    // ── UI state ───────────────────────────────────────────────────────────────
    var showControls      by remember { mutableStateOf(true) }
    var playbackSpeed     by remember { mutableStateOf(1f) }
    var isBoostingSpeed   by remember { mutableStateOf(false) }
    var isRepeatMode      by remember { mutableStateOf(false) }
    var isLocked          by remember { mutableStateOf(false) }
    var showSpeedPanel    by remember { mutableStateOf(false) }
    var showInfoCard      by remember { mutableStateOf(false) }

    // ── Gesture / feedback overlays ────────────────────────────────────────────
    var gestureIndicator  by remember { mutableStateOf<GestureIndicator?>(null) }
    var seekFeedback      by remember { mutableStateOf<SeekFeedback?>(null) }
    var speedBoostVisible by remember { mutableStateOf(false) }

    // ── Volume / brightness ────────────────────────────────────────────────────
    var currentVolume     by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }

    // ── ChromeCast state ───────────────────────────────────────────────────────
    var castState      by remember { mutableStateOf(CastState.UNAVAILABLE) }
    var castDeviceName by remember { mutableStateOf<String?>(null) }

    // ── Helpers ────────────────────────────────────────────────────────────────

    fun applySpeed(speed: Float) {
        val player = mediaPlayerRef ?: return
        if (!isPrepared) return
        runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }.onFailure {
            Log.w(VIDEO_TAG, "Failed to set speed $speed: ${it.message}")
        }
    }

    fun seekBy(deltaMs: Int) {
        val vv = videoViewRef ?: return
        if (!isPrepared) return
        val newPos = (vv.currentPosition + deltaMs).coerceIn(0, vv.duration.coerceAtLeast(0))
        vv.seekTo(newPos)
        seekPositionMs = newPos
        positionMs     = newPos
        seekFeedback   = SeekFeedback(abs(deltaMs) / 1000, deltaMs >= 0)
    }

    /**
     * لو الـ URI خارجي (id == -1) نستخدمه مباشرة — مفيش محتاج fallback chain.
     * لو ID حقيقي نمشي في الـ fallback chain زي الأول.
     */
    fun resolveUri(item: com.omnimemoria.domain.model.MediaPhoto): Uri {
        if (item.id == -1L) return item.uri          // External URI — use as-is
        return when (playbackUriMode) {
            PlaybackUriMode.ORIGINAL         -> item.uri
            PlaybackUriMode.VIDEO_COLLECTION ->
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id)
            PlaybackUriMode.FILES_COLLECTION ->
                ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id)
        }
    }

    fun enterPiP() {
        if (!supportsPiP) {
            Toast.makeText(context, "Picture-in-Picture requires Android 8+", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val item   = mediaItem ?: return
            val aspectW = item.width.takeIf  { it > 0 } ?: 16
            val aspectH = item.height.takeIf { it > 0 } ?: 9
            val safeW   = aspectW.coerceIn(1, 239)
            val safeH   = aspectH.coerceIn(1, 239)
            val rational = when {
                safeW.toFloat() / safeH > 2.39f -> Rational(239, 100)
                safeH.toFloat() / safeW > 2.39f -> Rational(100, 239)
                else                             -> Rational(safeW, safeH)
            }
            val params = PictureInPictureParams.Builder().setAspectRatio(rational).build()
            // إخفاء الـ controls قبل الدخول عشان الانتقال يبان نضيف
            showControls   = false
            showSpeedPanel = false
            showInfoCard   = false
            activity?.enterPictureInPictureMode(params)
        }
    }

    fun startCastDiscovery() {
        castState      = CastState.SEARCHING
        castDeviceName = null
    }

    fun disconnectCast() {
        castState      = CastState.AVAILABLE
        castDeviceName = null
    }

    // ── Effects ────────────────────────────────────────────────────────────────

    /**
     * FIX: يشمل externalUriStr في الـ key — لو كان موجود نحمّل منه،
     * مش من mediaId اللي بيكون 0 وبيرجع null.
     */
    LaunchedEffect(mediaId, externalUriStr) {
        playbackUriMode = PlaybackUriMode.ORIGINAL
        loadedVideoId   = null
        mediaItem = if (externalUriStr != null) {
            viewModel.getPhotoFromUri(externalUriStr)
        } else {
            viewModel.getPhoto(mediaId)
        }
    }

    // لما نكون في PiP نخفي كل الـ overlays
    LaunchedEffect(isInPiP) {
        if (isInPiP) {
            showControls   = false
            showSpeedPanel = false
            showInfoCard   = false
            isLocked       = false
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isLocked, isInPiP) {
        if (!showControls || !isPlaying || isLocked || isInPiP) return@LaunchedEffect
        delay(AUTO_HIDE_DELAY_MS)
        showControls   = false
        showSpeedPanel = false
        showInfoCard   = false
    }

    // Position tracking
    LaunchedEffect(isPrepared, isPlaying) {
        while (isPrepared && isPlaying) {
            val vv = videoViewRef ?: break
            if (!isSeeking) {
                val dur = vv.duration.coerceAtLeast(0)
                val pos = vv.currentPosition.coerceAtLeast(0)
                if (durationMs != dur) durationMs = dur
                if (positionMs != pos) { positionMs = pos; seekPositionMs = pos }
            }
            delay(POSITION_POLL_MS)
        }
    }

    LaunchedEffect(seekFeedback)    { if (seekFeedback      != null) { delay(FEEDBACK_DURATION_MS);     seekFeedback      = null } }
    LaunchedEffect(gestureIndicator){ if (gestureIndicator  != null) { delay(GESTURE_HINT_DURATION_MS); gestureIndicator  = null } }
    LaunchedEffect(isBoostingSpeed) { if (isBoostingSpeed) speedBoostVisible = true else { delay(300); speedBoostVisible = false } }

    LaunchedEffect(castState) {
        if (castState == CastState.SEARCHING) {
            delay(2_000)
            castState = CastState.AVAILABLE
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayerRef = null; videoViewRef?.stopPlayback() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Root layout
    // ══════════════════════════════════════════════════════════════════════════

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)    // ← أسود خالص في PiP وخارجه
    ) {
        val item = mediaItem

        // ── Loading state ─────────────────────────────────────────────────────
        if (item == null) {
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color       = Color(0xFF8B7FF5),
                    strokeWidth = 2.dp,
                    modifier    = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "...Loading video",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return@Box
        }

        // ── VideoView ──────────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                VideoView(ctx).apply {
                    videoViewRef = this

                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        mp.isLooping   = isRepeatMode
                        isPrepared     = true
                        durationMs     = mp.duration.coerceAtLeast(0)
                        positionMs     = 0; seekPositionMs = 0
                        isPlaying      = true
                        val targetSpeed = if (isBoostingSpeed) LONG_PRESS_SPEED else playbackSpeed
                        if (targetSpeed != 1f) {
                            runCatching { mp.playbackParams = mp.playbackParams.setSpeed(targetSpeed) }
                        }
                        mp.start()
                    }

                    setOnCompletionListener {
                        isBoostingSpeed = false
                        isPlaying       = false
                        positionMs      = durationMs
                        seekPositionMs  = durationMs
                        if (!isInPiP) showControls = true
                    }

                    setOnErrorListener { _, _, _ ->
                        mediaPlayerRef  = null
                        isBoostingSpeed = false
                        isPrepared      = false
                        isPlaying       = false
                        // لو الـ URI خارجي، مفيش فايدة من الـ fallback chain
                        if (item.id == -1L) {
                            Toast.makeText(ctx, "Unable to play this video", Toast.LENGTH_SHORT).show()
                            return@setOnErrorListener true
                        }
                        val nextMode = playbackUriMode.next()
                        if (nextMode != null) {
                            Log.w(VIDEO_TAG, "Playback error — retrying with $nextMode")
                            playbackUriMode = nextMode
                            loadedVideoId   = null
                        } else {
                            Toast.makeText(ctx, "Unable to play this video", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                }
            },
            update = { vv ->
                val currentLoadedId = if (item.id == -1L) item.uri.hashCode().toLong() else item.id
                if (loadedVideoId != currentLoadedId) {
                    mediaPlayerRef  = null
                    isBoostingSpeed = false
                    isPrepared      = false
                    isPlaying       = false
                    durationMs      = 0
                    positionMs      = 0
                    seekPositionMs  = 0
                    vv.setVideoURI(resolveUri(item))
                    vv.requestFocus()
                    loadedVideoId = currentLoadedId
                } else if (isPrepared) {
                    applySpeed(if (isBoostingSpeed) LONG_PRESS_SPEED else playbackSpeed)
                }
            }
        )

        // ══════════════════════════════════════════════════════════════════════
        // كل الـ UI التالي مخفي في PiP Mode — الـ VideoView فضل شغال بس
        // ══════════════════════════════════════════════════════════════════════

        if (!isInPiP) {

            // ── Gesture layer ──────────────────────────────────────────────────
            if (!isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isPrepared) {
                            detectTapGestures(
                                onTap = {
                                    showControls = !showControls
                                    if (!showControls) { showSpeedPanel = false; showInfoCard = false }
                                },
                                onDoubleTap = { offset ->
                                    if (!isPrepared || size.width <= 0) return@detectTapGestures
                                    seekBy(if (offset.x >= size.width / 2f) SKIP_MS else -SKIP_MS)
                                },
                                onLongPress = {
                                    if (!isPrepared || !isPlaying || isBoostingSpeed) return@detectTapGestures
                                    isBoostingSpeed = true
                                    applySpeed(LONG_PRESS_SPEED)
                                },
                                onPress = {
                                    tryAwaitRelease()
                                    if (isBoostingSpeed) {
                                        isBoostingSpeed = false
                                        applySpeed(playbackSpeed)
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            val sensitivity = 0.0045f
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount.y * sensitivity
                                if (change.position.x < size.width / 2f) {
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                    currentBrightness = newBrightness
                                    activity?.window?.attributes?.let { p ->
                                        p.screenBrightness = newBrightness
                                        activity.window.attributes = p
                                    }
                                    gestureIndicator = GestureIndicator(
                                        icon   = Icons.Filled.BrightnessHigh,
                                        label  = "Brightness ${(newBrightness * 100).roundToInt()}%",
                                        value  = newBrightness,
                                        onLeft = true
                                    )
                                } else {
                                    val step   = (delta * maxVolume * 2).roundToInt()
                                    val newVol = (currentVolume + step).coerceIn(0, maxVolume)
                                    if (newVol != currentVolume) {
                                        currentVolume = newVol
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    }
                                    gestureIndicator = GestureIndicator(
                                        icon   = Icons.Filled.VolumeUp,
                                        label  = "Volume ${if (maxVolume > 0) (currentVolume * 100 / maxVolume) else 0}%",
                                        value  = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f,
                                        onLeft = false
                                    )
                                }
                            }
                        }
                )
            }

            // ── Gesture indicator ──────────────────────────────────────────────
            val gi = gestureIndicator
            AnimatedVisibility(
                visible  = gi != null,
                enter    = fadeIn(tween(150)) + scaleIn(initialScale = 0.88f),
                exit     = fadeOut(tween(200)),
                modifier = Modifier
                    .align(if (gi?.onLeft == true) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 20.dp)
            ) {
                if (gi != null) GestureIndicatorBubble(indicator = gi)
            }

            // ── Seek feedback bubble ───────────────────────────────────────────
            val sf = seekFeedback
            AnimatedVisibility(
                visible  = sf != null,
                enter    = fadeIn() + scaleIn(initialScale = 0.85f),
                exit     = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (sf != null) SeekFeedbackBubble(feedback = sf)
            }

            // ── Speed boost banner ─────────────────────────────────────────────
            AnimatedVisibility(
                visible  = speedBoostVisible,
                enter    = fadeIn(tween(120)),
                exit     = fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 86.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8B7FF5).copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text("2x Speed Boost", color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold)
                }
            }

            // ── Centre controls ────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = showControls && !isLocked,
                enter    = fadeIn(tween(180)),
                exit     = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    VideoControlButton(icon = Icons.Filled.FastRewind,  size = 54.dp, onClick = { seekBy(-SKIP_MS) })
                    VideoControlButton(
                        icon    = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        size    = 72.dp,
                        onClick = {
                            val vv = videoViewRef ?: return@VideoControlButton
                            if (!isPrepared) return@VideoControlButton
                            if (vv.isPlaying) { vv.pause(); isPlaying = false }
                            else              { vv.start(); isPlaying = true  }
                        }
                    )
                    VideoControlButton(icon = Icons.Filled.FastForward, size = 54.dp, onClick = { seekBy(SKIP_MS) })
                }
            }

            // ── Lock button ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = showControls || isLocked,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLocked) Color(0xFF2D26A0).copy(alpha = 0.9f)
                            else          Color.Black.copy(alpha = 0.40f)
                        )
                        .border(
                            1.dp,
                            if (isLocked) Color(0xFF8B7FF5).copy(alpha = 0.65f)
                            else          Color.White.copy(alpha = 0.14f),
                            CircleShape
                        )
                        .clickable {
                            isLocked = !isLocked
                            if (!isLocked) showControls = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = if (isLocked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (isLocked) "Unlock screen" else "Lock screen",
                        tint               = if (isLocked) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.65f),
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            // ── Top bar ────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = showControls && !isLocked,
                enter    = slideInVertically { -it } + fadeIn(tween(180)),
                exit     = slideOutVertically { -it } + fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                OmniMediaTopBar(
                    leading = {
                        TopBarButton(icon = Icons.AutoMirrored.Filled.ArrowBack, desc = "Back", onClick = onBack)
                    },
                    center = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                item.name.substringBeforeLast('.'),
                                color      = Color.White.copy(alpha = 0.94f),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines   = 1,
                                textAlign  = TextAlign.Center
                            )
                            val subtitle = buildString {
                                if (durationMs > 0) append(formatTime(durationMs))
                                if (item.width > 0 && item.height > 0) append(" · ${item.width}×${item.height}")
                            }
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle,
                                    color = Color.White.copy(alpha = 0.40f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                        if (playbackSpeed != 1f) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF2D26A0))
                                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.42f), RoundedCornerShape(9.dp))
                                    .clickable { showSpeedPanel = !showSpeedPanel; showInfoCard = false }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    playbackSpeed.toSpeedLabel(),
                                    color      = Color.White,
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        if (castState == CastState.CONNECTED)
                            TopBarButton(icon = Icons.Filled.Cast, desc = "Casting", tint = Color(0xFF8B7FF5), onClick = { showSpeedPanel = !showSpeedPanel; showInfoCard = false })
                        if (supportsPiP)
                            TopBarButton(icon = Icons.Filled.PictureInPicture, desc = "Picture in picture", onClick = { enterPiP() })
                        TopBarButton(icon = Icons.Outlined.Share, desc = "Share", onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = item.mimeType.ifBlank { "video/*" }
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                        })
                        TopBarButton(icon = Icons.Outlined.Info,    desc = "Video info",  onClick = { showInfoCard = !showInfoCard; showSpeedPanel = false })
                        TopBarButton(icon = Icons.Filled.MoreVert,  desc = "Options",     onClick = { showSpeedPanel = !showSpeedPanel; showInfoCard = false })
                        }
                    }
                )
            }

            // ── Bottom seek bar ────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = showControls && !isLocked,
                enter    = slideInVertically { it } + fadeIn(tween(180)),
                exit     = slideOutVertically { it } + fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                VideoSeekBar(
                    positionMs      = if (isSeeking) seekPositionMs else positionMs,
                    durationMs      = durationMs,
                    onSeek          = { isSeeking = true; seekPositionMs = it.toInt() },
                    onSeekFinished  = {
                        val vv = videoViewRef ?: run { isSeeking = false; return@VideoSeekBar }
                        if (isPrepared && durationMs > 0)
                            vv.seekTo(seekPositionMs.coerceIn(0, durationMs))
                        positionMs = seekPositionMs
                        isSeeking  = false
                    }
                )
            }

            // ── Speed / options panel ──────────────────────────────────────────
            AnimatedVisibility(
                visible  = showSpeedPanel && !isLocked,
                enter    = fadeIn(tween(200)) + slideInHorizontally { it },
                exit     = fadeOut(tween(160)) + slideOutHorizontally { it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 80.dp, end = 14.dp)
            ) {
                SpeedPanel(
                    currentSpeed     = playbackSpeed,
                    isRepeatMode     = isRepeatMode,
                    castState        = castState,
                    castDeviceName   = castDeviceName,
                    canSetSpeed      = canSetSpeed,
                    onSpeedSelect    = { speed -> playbackSpeed = speed; applySpeed(speed); showSpeedPanel = false },
                    onRepeatToggle   = { isRepeatMode = !isRepeatMode; mediaPlayerRef?.isLooping = isRepeatMode },
                    onCastAction     = {
                        when (castState) {
                            CastState.UNAVAILABLE -> Toast.makeText(context, "No cast devices found nearby", Toast.LENGTH_SHORT).show()
                            CastState.SEARCHING   -> Unit
                            CastState.AVAILABLE   -> {
                                castState = CastState.CONNECTED; castDeviceName = "Living Room TV"
                                Toast.makeText(context, "Connected to Living Room TV", Toast.LENGTH_SHORT).show()
                            }
                            CastState.CONNECTED -> {
                                disconnectCast()
                                Toast.makeText(context, "Disconnected from cast", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onScanForDevices = { startCastDiscovery() }
                )
            }

            // ── Video info card ────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = showInfoCard && !isLocked,
                enter    = fadeIn(tween(200)) + slideInVertically { -it / 2 },
                exit     = fadeOut(tween(160)) + slideOutVertically { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 80.dp, start = 14.dp)
            ) {
                VideoInfoCard(item = item, durationMs = durationMs)
            }

            // ── Locked screen hint ─────────────────────────────────────────────
            AnimatedVisibility(
                visible  = isLocked,
                enter    = fadeIn(tween(200)),
                exit     = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141220).copy(alpha = 0.88f))
                        .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(
                        "Screen locked · Tap the lock to unlock",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

        } // end !isInPiP block

    } // end root Box
}

// ══════════════════════════════════════════════════════════════════════════════
// Gesture indicator bubble
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GestureIndicatorBubble(indicator: GestureIndicator) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.93f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .width(92.dp)
    ) {
        Icon(indicator.icon, null, tint = Color(0xFF8B7FF5), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress   = { indicator.value },
            color      = Color(0xFF8B7FF5),
            trackColor = Color.White.copy(alpha = 0.14f),
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(indicator.label, color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Seek feedback bubble
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SeekFeedbackBubble(feedback: SeekFeedback) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text       = "${if (feedback.forward) "+${feedback.seconds}" else "-${feedback.seconds}"}s",
            color      = Color.White,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Top bar button (used by the inline OmniMediaTopBar trailing slot above)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopBarButton(
    icon:    ImageVector,
    desc:    String,
    tint:    Color = Color.White.copy(alpha = 0.88f),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Seek bar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoSeekBar(
    positionMs:     Int,
    durationMs:     Int,
    onSeek:         (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MediaChromeCorner))
            .background(NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(MediaChromeCorner))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Slider(
            value                 = positionMs.toFloat(),
            onValueChange         = onSeek,
            onValueChangeFinished = onSeekFinished,
            valueRange            = 0f..durationMs.coerceAtLeast(1).toFloat(),
            colors                = SliderDefaults.colors(
                thumbColor         = Color(0xFF8B7FF5),
                activeTrackColor   = Color(0xFF8B7FF5),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelMedium)
            Text(
                if (durationMs > 0) "-${formatTime((durationMs - positionMs).coerceAtLeast(0))}" else "--:--",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Centre control button
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoControlButton(icon: ImageVector, size: Dp = 56.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.94f), modifier = Modifier.size(size * 0.50f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Speed panel
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedPanel(
    currentSpeed:     Float,
    isRepeatMode:     Boolean,
    castState:        CastState,
    castDeviceName:   String?,
    canSetSpeed:      Boolean,
    onSpeedSelect:    (Float) -> Unit,
    onRepeatToggle:   () -> Unit,
    onCastAction:     () -> Unit,
    onScanForDevices: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF2D26A0))
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(currentSpeed.toSpeedLabel(), color = Color.White,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
            }
            Text("Speed", color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)
        SPEED_OPTIONS.forEach { opt ->
            val isSelected = currentSpeed == opt.speed
            val enabled    = canSetSpeed || opt.speed == 1f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) Color(0xFF2D26A0).copy(alpha = 0.32f) else Color.Transparent)
                    .clickable(enabled = enabled) { onSpeedSelect(opt.speed) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (isSelected) Icon(Icons.Filled.Check, null, tint = Color(0xFF8B7FF5), modifier = Modifier.size(16.dp))
                else Spacer(Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(opt.label,
                    color = when { isSelected -> Color(0xFF8B7FF5).copy(alpha = 0.9f); !enabled -> Color.White.copy(alpha = 0.20f); else -> Color.White.copy(alpha = 0.55f) },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(opt.speed.toSpeedLabel(),
                    color = when { isSelected -> Color(0xFF8B7FF5); !enabled -> Color.White.copy(alpha = 0.18f); else -> Color.White.copy(alpha = 0.60f) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(if (isRepeatMode) Color(0xFF2D26A0).copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onRepeatToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(if (isRepeatMode) Icons.Filled.Repeat else Icons.Outlined.Repeat, "Repeat",
                tint = if (isRepeatMode) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.50f), modifier = Modifier.size(20.dp))
            Text("Repeat", color = if (isRepeatMode) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.50f),
                style = MaterialTheme.typography.bodyMedium, fontWeight = if (isRepeatMode) FontWeight.SemiBold else FontWeight.Normal)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
        val castIconTint = when (castState) {
            CastState.CONNECTED   -> Color(0xFF8B7FF5); CastState.AVAILABLE -> Color.White.copy(alpha = 0.72f)
            CastState.SEARCHING   -> Color.White.copy(alpha = 0.45f); CastState.UNAVAILABLE -> Color.White.copy(alpha = 0.25f)
        }
        val castLabel = when (castState) {
            CastState.CONNECTED   -> castDeviceName ?: "Connected"; CastState.AVAILABLE -> "Cast to device"
            CastState.SEARCHING   -> "Searching..."; CastState.UNAVAILABLE -> "No devices found"
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(if (castState == CastState.CONNECTED) Color(0xFF2D26A0).copy(alpha = 0.28f) else Color.Transparent)
                .clickable(enabled = castState != CastState.SEARCHING) {
                    if (castState == CastState.UNAVAILABLE) onScanForDevices() else onCastAction()
                }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(if (castState == CastState.CONNECTED) Icons.Filled.Cast else Icons.Outlined.Cast,
                "ChromeCast", tint = castIconTint, modifier = Modifier.size(20.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(castLabel, color = castIconTint, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (castState == CastState.CONNECTED) FontWeight.SemiBold else FontWeight.Normal)
                if (castState == CastState.SEARCHING)
                    Text("Scanning for cast receivers...", color = Color.White.copy(alpha = 0.32f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Video info card
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoInfoCard(
    item:       com.omnimemoria.domain.model.MediaPhoto,
    durationMs: Int
) {
    Column(
        modifier = Modifier
            .width(224.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Video Info", color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)
        if (item.width > 0 && item.height > 0) InfoRow("Resolution", "${item.width} × ${item.height}")
        if (durationMs > 0)                     InfoRow("Duration",   formatTime(durationMs))
        if (item.size > 0) {
            val mb = item.size / (1024f * 1024f)
            InfoRow("Size", if (mb >= 1f) "%.1f MB".format(mb) else "${item.size / 1024} KB")
        }
        if (item.mimeType.isNotBlank()) InfoRow("Format", item.mimeType.uppercase().replace("VIDEO/", ""))
        if (item.name.isNotBlank())     InfoRow("File",   item.name.substringBeforeLast('.').take(24))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.36f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
