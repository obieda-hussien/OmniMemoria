package com.omnimemoria.ui.detail

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.navigation.NavigationSurfaceColor
import com.omnimemoria.ui.photoSharedKey
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoDetailScreen(
    photoId:        Long,
    bucketId:       String?  = null,
    externalUriStr: String?  = null,
    onBack:         () -> Unit,
    onOpenVideo:    (mediaId: Long, externalUri: String?) -> Unit,
    viewModel:      PhotoDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val haptic          = LocalHapticFeedback.current
    val photoList       by viewModel.photoList.collectAsState()
    val initialPage     by viewModel.initialPage.collectAsState()
    val isFavorite      by viewModel.isFavorite.collectAsState()
    val isFullListReady by viewModel.isFullListReady.collectAsState()

    LaunchedEffect(photoId, bucketId, externalUriStr) {
        viewModel.loadAllPhotos(photoId, bucketId, externalUriStr)
    }

    if (photoList.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().background(Color(0xFF090810)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color       = Color(0xFF8B7FF5),
                strokeWidth = 2.dp,
                modifier    = Modifier.size(32.dp)
            )
        }
        return
    }

    PhotoPager(
        photoList       = photoList,
        startPage       = initialPage,
        isFullListReady = isFullListReady,
        isFavorite      = isFavorite,
        onBack          = onBack,
        onOpenVideo     = onOpenVideo,
        onFavorite      = { id ->
            viewModel.toggleFavorite(id)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )
}

// ── Pager ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoPager(
    photoList:       List<MediaPhoto>,
    startPage:       Int,
    isFullListReady: Boolean,
    isFavorite:      Boolean,
    onBack:          () -> Unit,
    onOpenVideo:     (mediaId: Long, externalUri: String?) -> Unit,
    onFavorite:      (Long) -> Unit
) {
    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val context                 = LocalContext.current

    val pagerState = rememberPagerState(initialPage = 0) { photoList.size }
    var hasJumped by remember { mutableStateOf(false) }

    LaunchedEffect(photoList.size) {
        if (!isFullListReady) hasJumped = false
    }

    // ── THE SYNCHRONIZATION BARRIER (The Ultimate Fix) ──
    LaunchedEffect(isFullListReady, startPage, photoList.size) {
        if (isFullListReady && photoList.isNotEmpty()) {
            val target = startPage.coerceIn(0, photoList.lastIndex)
            
            // 1. حاجز المزامنة: لن نقوم بالقفز أبداً إلا عندما يؤكد الـ Pager أن
            // حجمه الداخلي قد تتطابق مع الحجم الجديد للقائمة. هذا يمنع خطأ الـ Clamp
            // الذي كان يجبر الفهرس على العودة إلى الصفر.
            snapshotFlow { pagerState.pageCount }
                .filter { it == photoList.size }
                .first()

            // 2. الآن، وبعد أن استوعب الـ Pager حجمه، نقفز بأمان مطلق.
            if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
                pagerState.scrollToPage(target)
            }
            
            // 3. إنهاء حالة التبديل الوهمي
            hasJumped = true
        }
    }

    // ── The Magic Resolver ──
    val resolvePhoto: (Int) -> MediaPhoto? = remember(photoList, isFullListReady, hasJumped, startPage) {
        { page ->
            if (photoList.isEmpty()) {
                null
            } else if (!isFullListReady) {
                if (page == 0) photoList.firstOrNull() else null
            } else if (!hasJumped && startPage != 0) {
                when (page) {
                    0         -> photoList.getOrNull(startPage)
                    startPage -> photoList.getOrNull(0)
                    else      -> photoList.getOrNull(page)
                }
            } else {
                photoList.getOrNull(page)
            }
        }
    }

    val currentPhoto by remember(isFullListReady, hasJumped, startPage, photoList) {
        derivedStateOf { resolvePhoto(pagerState.currentPage) }
    }

    var showChrome   by remember { mutableStateOf(true) }
    var showMetadata by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090810))
    ) {
        HorizontalPager(
            state                   = pagerState,
            modifier                = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key                     = { page -> resolvePhoto(page)?.id ?: "empty_$page" }
        ) { page ->
            val photo = resolvePhoto(page) ?: return@HorizontalPager

            val sharedMod: Modifier = if (
                sharedTransitionScope   != null &&
                animatedVisibilityScope != null &&
                page == pagerState.currentPage
            ) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState      = rememberSharedContentState(key = photoSharedKey(photo.id)),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform         = photosBoundsTransform
                    )
                }
            } else Modifier

            val imageRequest = remember(photo.id, photo.uri) {
                ImageRequest.Builder(context).data(photo.uri).build()
            }
            val mediaMod = Modifier.fillMaxSize().then(sharedMod)

            if (photo.mimeType.startsWith("video/", ignoreCase = true)) {
                Box(modifier = mediaMod.clickable {
                    onOpenVideo(photo.id, if (photo.id == -1L) photo.uri.toString() else null)
                }) {
                    AsyncImage(
                        model              = imageRequest,
                        contentDescription = photo.name,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable {
                                onOpenVideo(photo.id, if (photo.id == -1L) photo.uri.toString() else null)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayCircleFilled, "Play video",
                            tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            } else {
                ZoomableAsyncImage(
                    model              = imageRequest,
                    contentDescription = photo.name,
                    modifier           = mediaMod,
                    onClick            = { showChrome = !showChrome }
                )
            }
        }

        // ── Gradient overlays ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome,
            enter    = fadeIn(tween(180)),
            exit     = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
                Box(modifier = Modifier.fillMaxWidth().height(240.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
            }
        }

        // ── Top bar ────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome,
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            DetailTopBar(
                photo       = currentPhoto,
                onBack      = onBack,
                onInfo      = { showMetadata = !showMetadata },
                showingInfo = showMetadata
            )
        }

        // ── Page counter ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome && photoList.size > 1,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 92.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = "${pagerState.currentPage + 1} / ${photoList.size}",
                    color      = Color.White.copy(alpha = 0.9f),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Subtle loading bar ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = !isFullListReady,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(600)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            LinearProgressIndicator(
                color      = Color(0xFF8B7FF5),
                trackColor = Color.Transparent,
                modifier   = Modifier.fillMaxWidth().height(2.dp)
            )
        }

        // ── Metadata card ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showMetadata,
            enter    = slideInVertically { it / 2 } + fadeIn(tween(240)),
            exit     = slideOutVertically { it / 2 } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
        ) {
            PhotoMetadataCard(photo = currentPhoto)
        }

        // ── Bottom actions ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            DetailBottomBar(
                isFavorite = isFavorite,
                onFavorite = { onFavorite(currentPhoto?.id ?: return@DetailBottomBar) },
                onShare    = {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_STREAM, currentPhoto?.uri)
                        type   = currentPhoto?.mimeType ?: "image/*"
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Media"))
                },
                onDelete = { },
                onEdit   = { }
            )
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    photo:       MediaPhoto?,
    onBack:      () -> Unit,
    onInfo:      () -> Unit,
    showingInfo: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }

            photo?.name?.let { name ->
                Text(
                    text       = name.substringBeforeLast('.'),
                    color      = Color.White.copy(alpha = 0.9f),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
            }

            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                    .background(
                        if (showingInfo) Color(0xFF8B7FF5).copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.08f)
                    ).clickable(onClick = onInfo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (showingInfo) Icons.Filled.Info else Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint               = if (showingInfo) Color(0xFF8B7FF5) else Color.White.copy(alpha = 0.9f),
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Metadata card ──────────────────────────────────────────────────────────────

@Composable
private fun PhotoMetadataCard(photo: MediaPhoto?) {
    val context = LocalContext.current

    val dateText = photo?.effectiveDateMs?.takeIf { it > 0 }?.let {
        SimpleDateFormat("EEE, d MMM yyyy  •  h:mm a", Locale.getDefault()).format(Date(it))
    } ?: "Unknown date"
    val sizeText = photo?.size?.let { Formatter.formatFileSize(context, it) } ?: "—"
    val resText  = photo?.let {
        if (it.width > 0 && it.height > 0) "${it.width} × ${it.height}" else "—"
    } ?: "—"
    val locationText = if (photo?.latitude != null && photo.longitude != null)
        "%.4f°, %.4f°".format(photo.latitude, photo.longitude)
    else null
    val format = photo?.mimeType?.takeIf { it.isNotBlank() }
        ?.uppercase()?.replace("IMAGE/", "")?.replace("VIDEO/", "")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 14.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8B7FF5).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Info, null, tint = Color(0xFF8B7FF5), modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Photo Details", color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 14.dp))
        MetaRow(Icons.Outlined.CalendarMonth, "Date",       dateText)
        Spacer(Modifier.height(12.dp))
        MetaRow(Icons.Outlined.SdStorage,     "File Size",  sizeText)
        Spacer(Modifier.height(12.dp))
        MetaRow(Icons.Outlined.AspectRatio,   "Resolution", resText)
        if (format != null) {
            Spacer(Modifier.height(12.dp))
            MetaRow(Icons.Outlined.Image, "Format", format)
        }
        if (locationText != null) {
            Spacer(Modifier.height(12.dp))
            MetaRow(Icons.Outlined.LocationOn, "Location", locationText)
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Color(0xFF8B7FF5).copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.labelSmall)
            Text(value, color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Bottom action bar ──────────────────────────────────────────────────────────

@Composable
private fun DetailBottomBar(
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onShare:    () -> Unit,
    onDelete:   () -> Unit,
    onEdit:     () -> Unit
) {
    val heartScale by animateFloatAsState(
        targetValue   = if (isFavorite) 1.28f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "heart_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            DetailAction(Icons.Outlined.Share,  "Share",  Color.White.copy(alpha = 0.85f), onClick = onShare)
            DetailAction(
                icon    = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label   = if (isFavorite) "Saved" else "Save",
                tint    = if (isFavorite) Color(0xFFFF4B6E) else Color.White.copy(alpha = 0.85f),
                scale   = heartScale,
                onClick = onFavorite
            )
            DetailAction(Icons.Outlined.Edit,          "Edit",   Color.White.copy(alpha = 0.85f), onClick = onEdit)
            DetailAction(Icons.Outlined.DeleteOutline,  "Delete", Color(0xFFFF6B6B),              onClick = onDelete)
        }
    }
}

@Composable
private fun DetailAction(
    icon:    ImageVector,
    label:   String,
    tint:    Color,
    scale:   Float  = 1f,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp * scale))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal val photosBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
}
