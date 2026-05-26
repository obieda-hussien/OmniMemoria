package com.omnimemoria.ui.detail

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
// الثوابت
// ══════════════════════════════════════════════════════════════════════════════

private const val SKIP_INTERVAL_MS       = 10_000
private const val LONG_PRESS_BOOST_SPEED = 2f
private const val VIDEO_TAG              = "VideoPlayerScreen"
private const val CONTROLS_AUTO_HIDE_MS  = 3_500L

// ── خيارات السرعة ─────────────────────────────────────────────────────────────

private data class SpeedOption(val speed: Float, val labelAr: String)

private val SPEED_OPTIONS = listOf(
    SpeedOption(0.2f, "بطيئة جداً"),
    SpeedOption(0.5f, "بطيئة"),
    SpeedOption(1.0f, "الأساسية"),
    SpeedOption(1.5f, "سريعة"),
    SpeedOption(2.0f, "سريعة جداً"),
)

// ── نسب العرض ─────────────────────────────────────────────────────────────────

private enum class AspectRatioMode(val labelAr: String) {
    FIT ("ملاءمة"),
    FILL("ملء"),
    CROP("قص");

    fun next(): AspectRatioMode = entries[(ordinal + 1) % entries.size]
}

// ── وضع URI للتشغيل ───────────────────────────────────────────────────────────

private enum class PlaybackUriMode { ORIGINAL, VIDEO_COLLECTION, FILES_COLLECTION }

// ── ملاحظة الإيماءة ───────────────────────────────────────────────────────────

private data class GestureHint(
    val icon:  ImageVector,
    val label: String,
    val value: Float,  // 0..1 for the progress bar
    val side:  GestureSide
)
private enum class GestureSide { LEFT, RIGHT }

// ── تنسيق الوقت ───────────────────────────────────────────────────────────────

private fun Float.toSpeedLabel(): String =
    if (this == this.toLong().toFloat()) "${this.toInt()}x" else "${this}x"

private fun formatVideoTime(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s / 60) % 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

// ══════════════════════════════════════════════════════════════════════════════
// الشاشة الرئيسية
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun VideoPlayerScreen(
    mediaId:   Long,
    onBack:    () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // ── حالة التشغيل ───────────────────────────────────────────────────────────
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
    var playbackUriMode       by remember(mediaId) { mutableStateOf(PlaybackUriMode.ORIGINAL) }
    val canControlSpeed        = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    // ── ميزات إضافية ────────────────────────────────────────────────────────────
    var showSpeedPanel        by remember { mutableStateOf(false) }
    var isRepeatMode          by remember { mutableStateOf(false) }
    var isLocked              by remember { mutableStateOf(false) }
    var aspectRatioMode       by remember { mutableStateOf(AspectRatioMode.FIT) }
    var showVideoInfo         by remember { mutableStateOf(false) }
    var gestureHint           by remember { mutableStateOf<GestureHint?>(null) }
    var seekLabel             by remember { mutableStateOf<String?>(null) }

    // ── مستوى الصوت والإضاءة ──────────────────────────────────────────────────
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness
                ?.takeIf { it >= 0f } ?: 0.5f
        )
    }

    // ── دوال مساعدة ────────────────────────────────────────────────────────────

    fun applySpeed(speed: Float) {
        val player = mediaPlayerRef ?: return
        if (!isPrepared || !canControlSpeed) return
        runCatching {
            player.playbackParams = player.playbackParams.setSpeed(speed)
        }
    }

    fun seekBy(deltaMs: Int) {
        val player = videoViewRef ?: return
        if (!isPrepared) return
        val newPos = (player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(newPos)
        seekPositionMs = newPos
        positionMs     = newPos
        val sec = abs(deltaMs) / 1000
        seekLabel = "${if (deltaMs >= 0) "⏩ +${sec}" else "⏪ -${sec}"}ث"
    }

    fun resolveUri(item: com.omnimemoria.domain.model.MediaPhoto, mode: PlaybackUriMode): Uri =
        when (mode) {
            PlaybackUriMode.ORIGINAL        -> item.uri
            PlaybackUriMode.VIDEO_COLLECTION ->
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id)
            PlaybackUriMode.FILES_COLLECTION ->
                ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id)
        }

    // ── التأثيرات ──────────────────────────────────────────────────────────────

    LaunchedEffect(mediaId) {
        playbackUriMode = PlaybackUriMode.ORIGINAL
        loadedVideoId   = null
        mediaItem       = viewModel.getPhoto(mediaId)
    }

    // إخفاء تلقائي للأدوات
    LaunchedEffect(showControls, isPlaying) {
        if (!showControls || !isPlaying || isLocked) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        showControls = false
    }

    // تتبع الموضع كل 200ms
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

    // إخفاء ملاحظة السيك
    LaunchedEffect(seekLabel) {
        if (seekLabel != null) { delay(800); seekLabel = null }
    }

    // إخفاء ملاحظة الإيماءة
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) { delay(1200); gestureHint = null }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayerRef = null; videoViewRef?.stopPlayback() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // الواجهة
    // ══════════════════════════════════════════════════════════════════════════

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090810))
    ) {
        val item = mediaItem

        // ── شاشة التحميل ─────────────────────────────────────────────────────
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
                    "جارٍ تحميل الفيديو…",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return@Box
        }

        // ── VideoView (ملء الشاشة) ────────────────────────────────────────────
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
                        if (canControlSpeed && playbackSpeed != 1f)
                            runCatching {
                                mp.playbackParams = mp.playbackParams.setSpeed(
                                    if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else playbackSpeed
                                )
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
                        mediaPlayerRef = null; isBoostingByLongPress = false
                        isPrepared = false; isPlaying = false
                        val nextMode = when (playbackUriMode) {
                            PlaybackUriMode.ORIGINAL         -> PlaybackUriMode.VIDEO_COLLECTION
                            PlaybackUriMode.VIDEO_COLLECTION -> PlaybackUriMode.FILES_COLLECTION
                            PlaybackUriMode.FILES_COLLECTION -> null
                        }
                        if (nextMode != null) { playbackUriMode = nextMode; loadedVideoId = null }
                        else Toast.makeText(ctx, "تعذّر تشغيل الفيديو", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
            },
            update = { vv ->
                if (loadedVideoId != item.id) {
                    mediaPlayerRef = null; isBoostingByLongPress = false
                    isPrepared = false; isPlaying = false
                    durationMs = 0; positionMs = 0; seekPositionMs = 0
                    vv.setVideoURI(resolveUri(item, playbackUriMode))
                    vv.requestFocus()
                    loadedVideoId = item.id
                } else if (isPrepared) {
                    applySpeed(if (isBoostingByLongPress) LONG_PRESS_BOOST_SPEED else playbackSpeed)
                }
            }
        )

        // ── طبقة الإيماءات (شفافة، تعمل فوق الفيديو) ─────────────────────────
        if (!isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isPrepared) {
                        detectTapGestures(
                            onTap = { showControls = !showControls; if (!showControls) showSpeedPanel = false },
                            onDoubleTap = { offset ->
                                if (!isPrepared || size.width <= 0) return@detectTapGestures
                                seekBy(if (offset.x >= size.width / 2f) SKIP_INTERVAL_MS else -SKIP_INTERVAL_MS)
                            },
                            onLongPress = {
                                if (!isPrepared || !isPlaying || !canControlSpeed || isBoostingByLongPress) return@detectTapGestures
                                isBoostingByLongPress = true; applySpeed(LONG_PRESS_BOOST_SPEED)
                            },
                            onPress = { tryAwaitRelease(); if (isBoostingByLongPress) { isBoostingByLongPress = false; applySpeed(playbackSpeed) } }
                        )
                    }
                    // ── إيماءات السحب (إضاءة / صوت) ────────────────────────────
                    .pointerInput(isPrepared) {
                        var dragStartY = 0f
                        detectDragGestures(
                            onDragStart = { offset -> dragStartY = offset.y },
                            onDrag      = { change, dragAmount ->
                                change.consume()
                                val sensitivity = 0.005f
                                val isLeftSide  = change.position.x < size.width / 2f
                                val delta       = -dragAmount.y * sensitivity
                                if (isLeftSide) {
                                    // الإضاءة
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                    currentBrightness = newBrightness
                                    activity?.window?.attributes?.let { params ->
                                        params.screenBrightness = newBrightness
                                        activity.window.attributes = params
                                    }
                                    gestureHint = GestureHint(
                                        icon  = Icons.Filled.BrightnessHigh,
                                        label = "الإضاءة ${(newBrightness * 100).roundToInt()}٪",
                                        value = newBrightness,
                                        side  = GestureSide.LEFT
                                    )
                                } else {
                                    // الصوت
                                    val volStep = (delta * maxVolume * 2).roundToInt()
                                    val newVol  = (currentVolume + volStep).coerceIn(0, maxVolume)
                                    if (newVol != currentVolume) {
                                        currentVolume = newVol
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    }
                                    gestureHint = GestureHint(
                                        icon  = Icons.Filled.VolumeUp,
                                        label = "الصوت ${if (maxVolume > 0) (currentVolume * 100 / maxVolume) else 0}٪",
                                        value = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f,
                                        side  = GestureSide.RIGHT
                                    )
                                }
                            }
                        )
                    }
            )
        }

        // ── تلميح الإيماءة (إضاءة / صوت) ────────────────────────────────────
        val hint = gestureHint
        AnimatedVisibility(
            visible  = hint != null,
            enter    = fadeIn(tween(150)) + scaleIn(initialScale = 0.88f),
            exit     = fadeOut(tween(200)),
            modifier = Modifier
                .align(if (hint?.side == GestureSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 20.dp)
        ) {
            GestureHintBubble(hint = hint)
        }

        // ── ملاحظة التقديم / الإرجاع ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = seekLabel != null,
            enter    = fadeIn() + scaleIn(initialScale = 0.85f),
            exit     = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text       = seekLabel.orEmpty(),
                    color      = Color.White,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── مؤشر تسريع اللمس المطوّل ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = isBoostingByLongPress,
            enter    = fadeIn(tween(120)),
            exit     = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 86.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF8B7FF5).copy(alpha = 0.9f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("⚡ سرعة مضاعفة ×2", color = Color.White,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
            }
        }

        // ── أزرار التشغيل المركزية ────────────────────────────────────────────
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
                VideoControlButton(icon = Icons.Filled.FastRewind,
                    onClick = { seekBy(-SKIP_INTERVAL_MS) })
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
                VideoControlButton(icon = Icons.Filled.FastForward,
                    onClick = { seekBy(SKIP_INTERVAL_MS) })
            }
        }

        // ── زر القفل (يظهر دائماً) ───────────────────────────────────────────
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
                        else          Color.Black.copy(alpha = 0.45f)
                    )
                    .border(
                        1.dp,
                        if (isLocked) Color(0xFF8B7FF5).copy(alpha = 0.6f)
                        else          Color.White.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .clickable { isLocked = !isLocked; if (!isLocked) showControls = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (isLocked) "إلغاء القفل" else "تأمين",
                    tint     = if (isLocked) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── الشريط العلوي ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls && !isLocked,
            enter    = slideInVertically { -it } + fadeIn(tween(180)),
            exit     = slideOutVertically { -it } + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            VideoTopBar(
                title            = item.name.substringBeforeLast('.'),
                subtitle         = buildString {
                    if (durationMs > 0) append(formatVideoTime(durationMs))
                    if (item.width > 0 && item.height > 0) append(" • ${item.width}×${item.height}")
                },
                currentSpeed     = playbackSpeed,
                aspectRatioMode  = aspectRatioMode,
                onBack           = onBack,
                onOpenSpeedPanel = { showSpeedPanel = !showSpeedPanel; showVideoInfo = false },
                onToggleInfo     = { showVideoInfo = !showVideoInfo; showSpeedPanel = false }
            )
        }

        // ── شريط التقدم السفلي ────────────────────────────────────────────────
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
            VideoBottomBar(
                positionMs      = if (isSeeking) seekPositionMs else positionMs,
                durationMs      = durationMs,
                isRepeatMode    = isRepeatMode,
                aspectRatioMode = aspectRatioMode,
                onSeek          = { isSeeking = true; seekPositionMs = it.toInt() },
                onSeekFinished  = {
                    val player = videoViewRef ?: run { isSeeking = false; return@VideoBottomBar }
                    if (isPrepared && durationMs > 0)
                        player.seekTo(seekPositionMs.coerceIn(0, durationMs))
                    positionMs = seekPositionMs; isSeeking = false
                },
                onRepeatToggle  = { isRepeatMode = !isRepeatMode; mediaPlayerRef?.isLooping = isRepeatMode },
                onAspectToggle  = { aspectRatioMode = aspectRatioMode.next() }
            )
        }

        // ── لوحة السرعة والخيارات ────────────────────────────────────────────
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
                currentSpeed    = playbackSpeed,
                isRepeatMode    = isRepeatMode,
                canControlSpeed = canControlSpeed,
                onSpeedSelect   = { speed -> playbackSpeed = speed; applySpeed(speed); showSpeedPanel = false },
                onRepeatToggle  = { isRepeatMode = !isRepeatMode; mediaPlayerRef?.isLooping = isRepeatMode }
            )
        }

        // ── بطاقة معلومات الفيديو ─────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showVideoInfo && !isLocked,
            enter    = fadeIn(tween(200)) + slideInVertically { -it / 2 },
            exit     = fadeOut(tween(160)) + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 80.dp, start = 14.dp)
        ) {
            VideoInfoCard(item = item, durationMs = durationMs)
        }

        // ── رسالة الشاشة المقفولة ─────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isLocked,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141220).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text  = "🔒 الشاشة مقفولة • اضغط القفل لإلغاء التأمين",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// تلميح الإيماءة (إضاءة / صوت)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GestureHintBubble(hint: GestureHint?) {
    if (hint == null) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .width(90.dp)
    ) {
        Icon(
            imageVector        = hint.icon,
            contentDescription = null,
            tint               = Color(0xFF8B7FF5),
            modifier           = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress            = { hint.value },
            color               = Color(0xFF8B7FF5),
            trackColor          = Color.White.copy(alpha = 0.15f),
            modifier            = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = hint.label,
            color     = Color.White.copy(alpha = 0.85f),
            style     = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// الشريط العلوي
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoTopBar(
    title:            String,
    subtitle:         String,
    currentSpeed:     Float,
    aspectRatioMode:  AspectRatioMode,
    onBack:           () -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onToggleInfo:     () -> Unit
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
        // رجوع
        TopBarIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, desc = "رجوع", onClick = onBack)

        // العنوان
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = title,
                color      = Color.White.copy(alpha = 0.95f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                textAlign  = TextAlign.Center
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = Color.White.copy(alpha = 0.42f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        // شارة السرعة (تظهر فقط لو ≠ 1x)
        if (currentSpeed != 1f) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF2D26A0))
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                    .clickable(onClick = onOpenSpeedPanel)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(currentSpeed.toSpeedLabel(), color = Color.White,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(2.dp))
        }

        // معلومات
        TopBarIconButton(icon = Icons.Outlined.Info, desc = "معلومات", onClick = onToggleInfo)

        // الخيارات
        TopBarIconButton(icon = Icons.Filled.MoreVert, desc = "المزيد", onClick = onOpenSpeedPanel)
    }
}

@Composable
private fun TopBarIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// الشريط السفلي + شريط التقدم
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoBottomBar(
    positionMs:     Int,
    durationMs:     Int,
    isRepeatMode:   Boolean,
    aspectRatioMode: AspectRatioMode,
    onSeek:         (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onRepeatToggle: () -> Unit,
    onAspectToggle: () -> Unit
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
                thumbColor         = Color(0xFF8B7FF5),
                activeTrackColor   = Color(0xFF8B7FF5),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatVideoTime(positionMs), color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium)
            Text(
                if (durationMs > 0) "-${formatVideoTime((durationMs - positionMs).coerceAtLeast(0))}" else "--:--",
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        // أزرار تحكم إضافية
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // تكرار
            BottomActionChip(
                icon    = if (isRepeatMode) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                label   = "تكرار",
                active  = isRepeatMode,
                onClick = onRepeatToggle
            )

            // نسبة العرض
            BottomActionChip(
                icon    = Icons.Filled.FitScreen,
                label   = aspectRatioMode.labelAr,
                active  = aspectRatioMode != AspectRatioMode.FIT,
                onClick = onAspectToggle
            )

            // كروم كاست (واجهة فقط)
            BottomActionChip(
                icon    = Icons.Outlined.Cast,
                label   = "كاست",
                active  = false,
                onClick = {}
            )

            // صورة داخل صورة (واجهة فقط)
            BottomActionChip(
                icon    = Icons.Filled.PictureInPicture,
                label   = "صورة في صورة",
                active  = false,
                onClick = {}
            )
        }
    }
}

@Composable
private fun BottomActionChip(
    icon:    ImageVector,
    label:   String,
    active:  Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) Color(0xFF2D26A0).copy(alpha = 0.5f)
                else        Color.White.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Icon(icon, null,
            tint     = if (active) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(3.dp))
        Text(label,
            color  = if (active) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.42f),
            style  = MaterialTheme.typography.labelSmall)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// أزرار التحكم المركزية
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoControlButton(icon: ImageVector, onClick: () -> Unit, size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(size * 0.5f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// لوحة السرعة والخيارات — نظام Lumina
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
            .width(236.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
    ) {
        // ── رأس اللوحة ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // شارة السرعة الحالية
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
            // عنوان اللوحة
            Text("السرعة", color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)

        // ── خيارات السرعة ──────────────────────────────────────────────────────
        SPEED_OPTIONS.forEach { opt ->
            val isSelected = currentSpeed == opt.speed
            val enabled    = canControlSpeed || opt.speed == 1f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) Color(0xFF2D26A0).copy(alpha = 0.32f) else Color.Transparent)
                    .clickable(enabled = enabled) { onSpeedSelect(opt.speed) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // رقم السرعة (يسار RTL → يمين فعلياً)
                Text(
                    text       = opt.speed.toSpeedLabel(),
                    color      = when { isSelected -> Color(0xFF8B7FF5); !enabled -> Color.White.copy(0.2f); else -> Color.White.copy(0.58f) },
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                )
                // التسمية العربية
                Text(
                    text  = opt.labelAr,
                    color = when { isSelected -> Color(0xFF8B7FF5).copy(0.85f); !enabled -> Color.White.copy(0.18f); else -> Color.White.copy(0.42f) },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)

        // ── تكرار ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isRepeatMode) Color(0xFF2D26A0).copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onRepeatToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRepeatMode) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                contentDescription = "تكرار",
                tint     = if (isRepeatMode) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.52f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text       = "تكرار",
                color      = if (isRepeatMode) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.52f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isRepeatMode) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)

        // ── كروم كاست ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* TODO */ }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Cast, "كاست",
                tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            Text("كروم كاست", color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// بطاقة معلومات الفيديو
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoInfoCard(
    item:      com.omnimemoria.domain.model.MediaPhoto,
    durationMs: Int
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("معلومات الفيديو",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)

        if (item.width > 0 && item.height > 0) {
            InfoRow("الدقة", "${item.width}×${item.height}")
        }
        if (durationMs > 0) {
            InfoRow("المدة", formatVideoTime(durationMs))
        }
        if (item.size > 0) {
            val mb = item.size / (1024f * 1024f)
            InfoRow("الحجم", if (mb >= 1f) "%.1f ميجابايت".format(mb) else "${item.size / 1024} كيلوبايت")
        }
        if (item.mimeType.isNotBlank()) {
            InfoRow("الصيغة", item.mimeType.uppercase().replace("VIDEO/", ""))
        }
        InfoRow("الاسم", item.name.substringBeforeLast('.').take(22))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
