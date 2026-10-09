package com.omnimemoria.ui.detail

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.components.OmniMediaBottomBar
import com.omnimemoria.ui.components.OmniMediaTopBar
import com.omnimemoria.ui.photoSharedKey
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val isFavorite      by viewModel.isFavorite.collectAsState()
    val isFullListReady by viewModel.isFullListReady.collectAsState()

    // ── Delete / events wiring ─────────────────────────────────────────────────
    var pendingOnConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingOnConfirm?.invoke()
        pendingOnConfirm = null
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is PhotoDetailUiEvent.RequestMediaPermission -> {
                    pendingOnConfirm = event.onConfirmed
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    )
                }
                is PhotoDetailUiEvent.NavigateBack -> onBack()
            }
        }
    }

    LaunchedEffect(photoId, bucketId, externalUriStr) {
        viewModel.loadAllPhotos(photoId, bucketId, externalUriStr)
    }

    if (photoList.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier    = Modifier.size(32.dp)
            )
        }
        return
    }

    PhotoPager(
        photoList       = photoList,
        isFullListReady = isFullListReady,
        isFavorite      = isFavorite,
        onBack          = onBack,
        onOpenVideo     = onOpenVideo,
        onPageChanged   = { newPhotoId -> viewModel.onPhotoPageChanged(newPhotoId) },
        onFavorite      = { id ->
            val willBeFavorite = !isFavorite
            viewModel.toggleFavorite(id)
            if (willBeFavorite) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onDelete        = { id -> viewModel.deleteCurrentPhoto(id) }  // ← FIXED
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PhotoPager(
    photoList:       List<MediaPhoto>,
    isFullListReady: Boolean,
    isFavorite:      Boolean,
    onBack:          () -> Unit,
    onOpenVideo:     (mediaId: Long, externalUri: String?) -> Unit,
    onPageChanged:   (Long) -> Unit = {},
    onFavorite:      (Long) -> Unit,
    onDelete:        (Long) -> Unit                                   // ← جديد
) {
    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val context                 = LocalContext.current

    val seedPhoto   = remember { photoList.firstOrNull() }
    val isTrulyReady = isFullListReady && photoList.size > 1

    val targetIndex = remember(photoList, isTrulyReady, seedPhoto) {
        if (isTrulyReady && seedPhoto != null) {
            val idx = photoList.indexOfFirst { it.id == seedPhoto.id }
            if (idx >= 0) idx else 0
        } else 0
    }

    val pagerState = key(isTrulyReady) {
        rememberPagerState(
            initialPage = if (isTrulyReady) targetIndex else 0,
            pageCount   = { photoList.size }
        )
    }

    val currentPhoto by remember(photoList, pagerState) {
        derivedStateOf { photoList.getOrNull(pagerState.currentPage) }
    }

    LaunchedEffect(pagerState, pagerState.currentPage, photoList) {
        photoList.getOrNull(pagerState.currentPage)?.id?.let { onPageChanged(it) }
    }

    var showChrome   by remember { mutableStateOf(true) }
    var showMetadata by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        HorizontalPager(
            state                   = pagerState,
            modifier                = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key                     = { page -> photoList.getOrNull(page)?.id ?: "empty_$page" }
        ) { page ->
            val photo = photoList.getOrNull(page) ?: return@HorizontalPager
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
                            .background(com.omnimemoria.ui.navigation.MediaChromeSurfaceColor)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                onOpenVideo(photo.id, if (photo.id == -1L) photo.uri.toString() else null)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayCircleFilled, "Play video",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(48.dp))
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

        // Gradient overlays
        AnimatedVisibility(
            visible  = showChrome,
            enter    = fadeIn(tween(180)),
            exit     = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.88f), Color.Transparent))))
                Box(modifier = Modifier.fillMaxWidth().height(240.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.94f)))))
            }
        }

        // Top bar
        AnimatedVisibility(
            visible  = showChrome,
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            OmniMediaTopBar(
                leading = {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                },
                center = {
                    currentPhoto?.name?.let { name ->
                        Text(
                            text       = name.substringBeforeLast('.'),
                            color      = MaterialTheme.colorScheme.onSurface,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                },
                trailing = {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                            .background(
                                if (showMetadata) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ).clickable { showMetadata = !showMetadata },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = if (showMetadata) Icons.Filled.Info else Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint               = if (showMetadata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }

        // Page counter
        AnimatedVisibility(
            visible  = showChrome && photoList.size > 1,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 92.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(com.omnimemoria.ui.navigation.MediaChromeSurfaceColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${photoList.size}",
                    color      = MaterialTheme.colorScheme.onSurface,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Loading bar
        AnimatedVisibility(
            visible  = !isFullListReady,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(600)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            LinearProgressIndicator(
                color      = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                modifier   = Modifier.fillMaxWidth().height(2.dp)
            )
        }

        // Bottom actions
        AnimatedVisibility(
            visible  = showChrome,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            val heartScale by animateFloatAsState(
                targetValue   = if (isFavorite) 1.28f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label         = "heart_scale"
            )
            OmniMediaBottomBar {
                DetailAction(Icons.Outlined.Share, "Share", MaterialTheme.colorScheme.onSurfaceVariant, onClick = {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_STREAM, currentPhoto?.uri)
                        type   = currentPhoto?.mimeType ?: "image/*"
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Media"))
                })
                DetailAction(
                    icon    = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label   = if (isFavorite) "Favorited" else "Favorite",
                    tint    = if (isFavorite) Color(0xFFFF4B6E) else MaterialTheme.colorScheme.onSurfaceVariant,
                    scale   = heartScale,
                    onClick = { currentPhoto?.id?.let { onFavorite(it) } }
                )
                DetailAction(Icons.Outlined.DeleteOutline, "Delete", MaterialTheme.colorScheme.error,               onClick = { currentPhoto?.id?.let { onDelete(it) } })
            }
        }
    }
    if (showMetadata) {
        ModalBottomSheet(
            onDismissRequest = { showMetadata = false },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PhotoMetadataCard(photo = currentPhoto)
        }
    }

}

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
        "%.4f°, %.4f°".format(photo.latitude, photo.longitude) else null
    val format = photo?.mimeType?.takeIf { it.isNotBlank() }
        ?.uppercase()?.replace("IMAGE/", "")?.replace("VIDEO/", "")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(bottom = 14.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Photo Details", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 14.dp))
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(value, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}
