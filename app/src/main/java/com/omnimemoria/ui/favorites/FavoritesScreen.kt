package com.omnimemoria.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.gallery.PhotoCell

// ── Rose color shared with the gallery badge ──────────────────────────────────
private val FavoriteRose = Color(0xFFFF4B6E)
private val FavoritesSurfaceColor = Color(0xFF1E1C30)
private const val TopScrimMidStop = 0.85f
private const val TopScrimMidAlpha = 0.9f

// ── Top padding so the content sits below the top bar ────────────────────────
private val ContentTopPadding = 110.dp

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun FavoritesTopBar(
    count: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(FavoritesSurfaceColor)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                tint     = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Favorites",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (count > 0) {
                Text(
                    "$count item${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoritesScreen(
    onPhotoClick: (Long) -> Unit,
    onBack:       () -> Unit = {},
    viewModel:    FavoritesViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)
    val haptic                  = LocalHapticFeedback.current
    val uiState                 by viewModel.uiState.collectAsState()
    val count                   by viewModel.favoritesCount.collectAsState()
    val sharedTransitionScope   = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            // ── Loading skeleton ───────────────────────────────────────────────
            uiState.isLoading -> {
                LazyVerticalGrid(
                    columns           = GridCells.Fixed(3),
                    contentPadding    = PaddingValues(
                        top    = ContentTopPadding,
                        bottom = 40.dp,
                        start  = 6.dp,
                        end    = 6.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement   = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FavoritesHeader(isLoading = true)
                    }
                    items(12) { SkeletonCell() }
                }
            }

            // ── Empty state ────────────────────────────────────────────────────
            uiState.photos.isEmpty() -> {
                Column(
                    modifier            = Modifier
                        .fillMaxSize()
                        .padding(top = ContentTopPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FavoritesHeader(isLoading = false)
                    Spacer(Modifier.height(48.dp))
                    EmptyFavoritesContent()
                }
            }

            // ── Populated grid ─────────────────────────────────────────────────
            else -> {
                LazyVerticalGrid(
                    columns           = GridCells.Fixed(3),
                    contentPadding    = PaddingValues(
                        top    = ContentTopPadding,
                        bottom = 40.dp,
                        start  = 6.dp,
                        end    = 6.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement   = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FavoritesHeader(isLoading = false)
                    }

                    items(
                        items = uiState.photos,
                        key   = { photo -> "fav_${photo.id}" }
                    ) { photo ->
                        PhotoCell(
                            uri                     = photo.uri.toString(),
                            photoId                 = photo.id,
                            isVideo                 = photo.mimeType.startsWith("video/", ignoreCase = true),
                            isSelected              = false,
                            isSelecting             = false,
                            isFavorite              = true,   // all cells here ARE favorites
                            sharedTransitionScope   = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick                 = { onPhotoClick(photo.id) },
                            onLongClick             = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.removeFavorite(photo.id)
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ContentTopPadding)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        TopScrimMidStop to MaterialTheme.colorScheme.background.copy(alpha = TopScrimMidAlpha),
                        1f to Color.Transparent
                    )
                )
        )

        FavoritesTopBar(
            count = count,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun FavoritesInfoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FavoritesSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = FavoriteRose.copy(alpha = 0.9f),
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Long-press a photo to remove it from favorites.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Header: section title + count ─────────────────────────────────────────────

@Composable
private fun FavoritesHeader(isLoading: Boolean) {
    Column {
        FavoritesInfoBanner()
        if (!isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "All favorites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

// ── Empty-state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFavoritesContent(modifier: Modifier = Modifier) {
    val floatY by rememberInfiniteTransition(label = "float")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = -8f,
            animationSpec = infiniteRepeatable(
                tween(2200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "float_y"
        )

    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .offset(y = floatY.dp)
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(FavoritesSurfaceColor)
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            HeartSvgIcon(size = 42.dp, tint = FavoriteRose.copy(alpha = 0.65f))
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text       = "No favorites yet",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text      = "Tap ♥ on any photo or long-press a photo to add it here",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Large outlined heart drawn purely with Compose primitives (no SVG asset needed). */
@Composable
private fun HeartSvgIcon(size: androidx.compose.ui.unit.Dp, tint: Color) {
    Icon(
        imageVector        = Icons.Outlined.FavoriteBorder,
        contentDescription = null,
        tint               = tint,
        modifier           = Modifier.size(size)
    )
}

// ── Skeleton placeholder ───────────────────────────────────────────────────────

@Composable
private fun SkeletonCell() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.35f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_fav"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}
