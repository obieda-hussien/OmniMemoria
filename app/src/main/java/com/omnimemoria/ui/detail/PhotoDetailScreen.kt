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
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoDetailScreen(
    photoId:     Long,
    bucketId:    String? = null,
    onBack:      () -> Unit,
    onOpenVideo: (mediaId: Long) -> Unit,
    viewModel:   PhotoDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val haptic      = LocalHapticFeedback.current
    val photoList   by viewModel.photoList.collectAsState()
    val initialPage by viewModel.initialPage.collectAsState()
    val isFavorite  by viewModel.isFavorite.collectAsState()

    LaunchedEffect(photoId, bucketId) {
        viewModel.loadAllPhotos(photoId, bucketId)
    }

    if (photoList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090810)),
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

    key(initialPage, photoList.size) {
        PhotoPager(
            photoList   = photoList,
            startPage   = initialPage,
            isFavorite  = isFavorite,
            onBack      = onBack,
            onOpenVideo = onOpenVideo,
            onFavorite  = { id ->
                viewModel.toggleFavorite(id)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
    }
}

// ── Pager ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoPager(
    photoList:  List<MediaPhoto>,
    startPage:  Int,
    isFavorite: Boolean,
    onBack:     () -> Unit,
    onOpenVideo: (mediaId: Long) -> Unit,
    onFavorite: (Long) -> Unit
) {
    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val context                 = LocalContext.current

    val safeStart  = startPage.coerceIn(0, photoList.lastIndex.coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeStart) { photoList.size }

    LaunchedEffect(photoList.size) {
        val last = photoList.lastIndex
        if (last >= 0 && pagerState.currentPage > last && !pagerState.isScrollInProgress)
            pagerState.scrollToPage(last)
    }

    val currentPhoto = photoList.getOrNull(pagerState.currentPage)
    var showChrome   by remember { mutableStateOf(true) }
    var showMetadata by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090810))
    ) {
        // ── Pager ────────────────────────────────────────────────────────────
        HorizontalPager(
            state               = pagerState,
            modifier            = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val photo = photoList.getOrNull(page) ?: return@HorizontalPager

            val sharedMod: Modifier = if (
                sharedTransitionScope != null &&
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

            key(photo.id) {
                val imageRequest = remember(photo.id, photo.uri) {
                    ImageRequest.Builder(context).data(photo.uri).build()
                }
                val mediaMod = Modifier
                    .fillMaxSize()
                    .then(sharedMod)

                if (photo.mimeType.startsWith("video/", ignoreCase = true)) {
                    // Video thumbnail + play overlay
                    Box(modifier = mediaMod.clickable { showChrome = !showChrome }) {
                        AsyncImage(
                            model              = imageRequest,
                            contentDescription = photo.name,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.fillMaxSize()
                        )
                        // Play button
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable { onOpenVideo(photo.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.PlayCircleFilled,
                                contentDescription = "Play video",
                                tint               = Color.White,
                                modifier           = Modifier.size(48.dp)
                            )
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
        }

        // ── Gradient overlays ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome,
            enter    = fadeIn(tween(180)),
            exit     = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                )
                // Bottom scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )
            }
        }

        // ── Top bar ──────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome,
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            DetailTopBar(
                photo       = currentPhoto,
                onBack      = onBack,
                onInfo      = { showMetadata = !showMetadata },
                showingInfo = showMetadata
            )
        }

        // ── Page counter ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome && photoList.size > 1,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 92.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = "${pagerState.currentPage + 1} / ${photoList.size}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Metadata card ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showMetadata,
            enter    = slideInVertically { it / 2 } + fadeIn(tween(240)),
            exit     = slideOutVertically { it / 2 } + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
        ) {
            PhotoMetadataCard(photo = currentPhoto)
        }

        // ── Bottom actions ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showChrome,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            DetailBottomBar(
                isFavorite = isFavorite,
                onFavorite = { onFavorite(currentPhoto?.id ?: return@DetailBottomBar) },
                onShare    = { },
                onDelete   = { },
                onEdit     = { }
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
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
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

            // File name
            photo?.name?.let { name ->
                Text(
                    text       = name.substringBeforeLast('.'),
                    color      = Color.White.copy(alpha = 0.9f),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
            }

            // Info button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (showingInfo) Color(0xFF8B7FF5).copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .clickable(onClick = onInfo),
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
        if (it.width > 0 && it.height > 0) "${it.width} × ${it.height}"
        else "—"
    } ?: "—"
    val locationText = if (photo?.latitude != null && photo.longitude != null)
        "%.4f°, %.4f°".format(photo.latitude, photo.longitude)
    else null
    val format = photo?.mimeType
        ?.takeIf { it.isNotBlank() }
        ?.uppercase()
        ?.replace("IMAGE/", "")
        ?.replace("VIDEO/", "")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1830).copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8B7FF5).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Info,
                    null,
                    tint     = Color(0xFF8B7FF5),
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Photo Details",
                color         = Color.White.copy(alpha = 0.55f),
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
        }

        HorizontalDivider(
            color     = Color.White.copy(alpha = 0.08f),
            modifier  = Modifier.padding(bottom = 14.dp)
        )

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
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = Color(0xFF8B7FF5).copy(alpha = 0.7f),
            modifier           = Modifier
                .size(18.dp)
                .padding(top = 1.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text  = label,
                color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text       = value,
                color      = Color.White.copy(alpha = 0.88f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "heart_scale"
    )

    // Glassmorphism bar
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
            modifier              = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            DetailAction(
                icon    = Icons.Outlined.Share,
                label   = "Share",
                tint    = Color.White.copy(alpha = 0.85f),
                onClick = onShare
            )
            DetailAction(
                icon    = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label   = if (isFavorite) "Saved" else "Save",
                tint    = if (isFavorite) Color(0xFFFF4B6E) else Color.White.copy(alpha = 0.85f),
                scale   = heartScale,
                onClick = onFavorite
            )
            DetailAction(
                icon    = Icons.Outlined.Edit,
                label   = "Edit",
                tint    = Color.White.copy(alpha = 0.85f),
                onClick = onEdit
            )
            DetailAction(
                icon    = Icons.Outlined.DeleteOutline,
                label   = "Delete",
                tint    = Color(0xFFFF6B6B),
                onClick = onDelete
            )
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
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = tint,
            modifier           = Modifier.size(24.dp * scale)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = label,
            color = tint.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// ── Shared element helpers ─────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
internal val photosBoundsTransform = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness    = Spring.StiffnessMediumLow
    )
}
