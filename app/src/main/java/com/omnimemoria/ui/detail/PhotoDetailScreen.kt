package com.omnimemoria.ui.detail

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.MediaPhoto
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotoDetailScreen(
    photoId:   Long,
    onBack:    () -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val haptic      = LocalHapticFeedback.current
    val context     = LocalContext.current
    val photoWindow by viewModel.photoWindow.collectAsState()
    val isFavorite  by viewModel.isFavorite.collectAsState()

    LaunchedEffect(photoId) {
        viewModel.loadWindowAround(photoId)
    }

    // ── FIX: pagerState ─────────────────────────────────────────────────────────
    // Root cause of "wrong photo" bug:
    //
    //   1. loadWindowAround() is async — when pagerState is first created, photoWindow
    //      is still empty so initialPage = 0 always (first photo in window, not the
    //      one the user tapped).
    //
    //   2. Even if we calculate initialPage from the empty list correctly (0), once
    //      the window loads Compose doesn't re-create pagerState (it's remembered),
    //      so the pager stays on page 0.
    //
    // Fix:
    //   • Start pagerState at 0 (window is empty at first creation anyway).
    //   • Add a LaunchedEffect that fires once when photoWindow loads, calculates
    //     the real target page, and calls scrollToPage() without animation so the
    //     user sees the correct photo immediately.
    //   • Once scrolled to the right photo, subsequent window loads (should not
    //     happen) are guarded by the `hasScrolledToInitialPage` flag so we don't
    //     jump back unexpectedly during swipe.

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount   = { photoWindow.size.coerceAtLeast(1) }
    )

    var hasScrolledToInitialPage by remember { mutableStateOf(false) }

    LaunchedEffect(photoWindow) {
        if (photoWindow.isNotEmpty() && !hasScrolledToInitialPage) {
            val targetPage = photoWindow.indexOfFirst { it.id == photoId }.coerceAtLeast(0)
            // scrollToPage (no animation) so the user sees the right photo instantly,
            // not a visible snap from page 0.
            pagerState.scrollToPage(targetPage)
            hasScrolledToInitialPage = true
        }
    }

    // The photo currently visible in the pager
    val currentPhoto = photoWindow.getOrNull(pagerState.currentPage)

    var showChrome   by remember { mutableStateOf(true) }
    var showMetadata by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ══ HorizontalPager ═══════════════════════════════════════════════════
        if (photoWindow.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
            }
        } else {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = photoWindow.getOrNull(page)
                if (photo != null) {
                    ZoomableAsyncImage(
                        model              = photo.uri,
                        contentDescription = photo.name,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ══ Blurred Background Gradient ════════════════════════════════════════
        AnimatedVisibility(
            visible  = showChrome,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
            }
        }

        // ══ Top Bar ═══════════════════════════════════════════════════════════
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
                onInfo      = {
                    showMetadata = !showMetadata
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                showingInfo = showMetadata
            )
        }

        // ══ Page Indicator ════════════════════════════════════════════════════
        // FIX: The indicator now shows the correct page because pagerState has been
        // scrolled to the right photo via the LaunchedEffect above.
        AnimatedVisibility(
            visible  = showChrome && photoWindow.size > 1,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        ) {
            Text(
                text      = "${pagerState.currentPage + 1} / ${photoWindow.size}",
                color     = Color.White.copy(alpha = 0.7f),
                style     = MaterialTheme.typography.labelSmall,
                modifier  = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // ══ Metadata Card ══════════════════════════════════════════════════════
        AnimatedVisibility(
            visible  = showMetadata,
            enter    = slideInVertically { it / 2 } + fadeIn(tween(250)),
            exit     = slideOutVertically { it / 2 } + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp, start = 16.dp, end = 16.dp)
        ) {
            PhotoMetadataCard(photo = currentPhoto)
        }

        // ══ Bottom Action Bar ═════════════════════════════════════════════════
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
                onFavorite = {
                    viewModel.toggleFavorite(currentPhoto?.id ?: return@DetailBottomBar)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onShare    = { /* TODO */ },
                onDelete   = { /* TODO */ },
                onEdit     = { /* TODO */ }
            )
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    photo:       MediaPhoto?,
    onBack:      () -> Unit,
    onInfo:      () -> Unit,
    showingInfo: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector        = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint               = Color.White,
                modifier           = Modifier.size(22.dp)
            )
        }

        photo?.name?.let { name ->
            Text(
                text       = name.substringBeforeLast('.'),
                color      = Color.White.copy(alpha = 0.9f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                modifier   = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        IconButton(
            onClick  = onInfo,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (showingInfo) Color.White.copy(alpha = 0.25f)
                    else Color.Black.copy(alpha = 0.45f)
                )
        ) {
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = "Info",
                tint               = if (showingInfo) Color.White else Color.White.copy(alpha = 0.8f),
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}

// ── Metadata Card ─────────────────────────────────────────────────────────────────

@Composable
private fun PhotoMetadataCard(photo: MediaPhoto?) {
    val context = LocalContext.current

    val dateText = photo?.dateTaken?.takeIf { it > 0 }?.let {
        SimpleDateFormat("EEE, MMM d yyyy  •  h:mm a", Locale.getDefault()).format(Date(it))
    } ?: "Unknown date"

    val sizeText = photo?.size?.let {
        Formatter.formatFileSize(context, it)
    } ?: "—"

    val resText = photo?.let {
        if (it.width > 0 && it.height > 0) "${it.width} × ${it.height}" else "—"
    } ?: "—"

    val locationText = if (photo?.latitude != null && photo.longitude != null)
        "%.4f, %.4f".format(photo.latitude, photo.longitude)
    else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1A2E).copy(alpha = 0.92f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.5f),
                modifier           = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text          = "Photo Details",
                color         = Color.White.copy(alpha = 0.6f),
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        MetadataRow(Icons.Outlined.CalendarMonth, "Date",       dateText)
        MetadataRow(Icons.Outlined.SdStorage,     "Size",       sizeText)
        MetadataRow(Icons.Outlined.AspectRatio,   "Resolution", resText)
        if (locationText != null) {
            MetadataRow(Icons.Outlined.LocationOn, "Location", locationText)
        }
        photo?.mimeType?.takeIf { it.isNotBlank() }?.let { mime ->
            MetadataRow(Icons.Outlined.Image, "Format", mime.uppercase().replace("IMAGE/", ""))
        }
    }
}

@Composable
private fun MetadataRow(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = Color.White.copy(alpha = 0.55f),
            modifier           = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text  = label,
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text       = value,
                color      = Color.White.copy(alpha = 0.9f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Bottom Action Bar ─────────────────────────────────────────────────────────────

@Composable
private fun DetailBottomBar(
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onShare:    () -> Unit,
    onDelete:   () -> Unit,
    onEdit:     () -> Unit
) {
    val heartScale by animateFloatAsState(
        targetValue   = if (isFavorite) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "heart_scale"
    )

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        BottomActionBtn(icon = Icons.Outlined.Share,  label = "Share",  tint = Color.White, onClick = onShare)
        BottomActionBtn(
            icon    = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label   = if (isFavorite) "Saved" else "Favorite",
            tint    = if (isFavorite) Color(0xFFFF4B6E) else Color.White,
            scale   = heartScale,
            onClick = onFavorite
        )
        BottomActionBtn(icon = Icons.Outlined.Edit,   label = "Edit",   tint = Color.White,          onClick = onEdit)
        BottomActionBtn(icon = Icons.Outlined.Delete, label = "Delete", tint = Color(0xFFFF6B6B),    onClick = onDelete)
    }
}

@Composable
private fun BottomActionBtn(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    label:   String,
    tint:    Color,
    scale:   Float = 1f,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = tint,
            modifier           = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = label,
            color = tint.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
