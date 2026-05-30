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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.gallery.PhotoCell

// ── Rose color shared with the gallery badge ──────────────────────────────────
private val FavoriteRose = Color(0xFFFF4B6E)

// ── Top padding so the content sits below the floating top bar ──────────────
private val ContentTopPadding = 80.dp

// ── Floating top bar ──────────────────────────────────────────────────────────

@Composable
private fun FavoritesTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(com.omnimemoria.ui.navigation.NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint     = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Favorite, null,
                    tint     = FavoriteRose,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text       = "Favorites",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
            Spacer(Modifier.size(44.dp)) // balance back button
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
                        bottom = 130.dp,
                        start  = 6.dp,
                        end    = 6.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement   = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FavoritesHeader(count = 0, isLoading = true)
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
                    FavoritesHeader(count = 0, isLoading = false)
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
                        bottom = 130.dp,
                        start  = 6.dp,
                        end    = 6.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement   = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FavoritesHeader(count = count, isLoading = false)
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

        // ── Floating top bar overlay ───────────────────────────────────────────
        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            FavoritesTopBar(onBack = onBack)
        }
    }
}

// ── Header: "Favorites ♥" title + animated count chip ─────────────────────────

@Composable
private fun FavoritesHeader(count: Int, isLoading: Boolean) {
    val animatedCount by animateIntAsState(
        targetValue   = count,
        animationSpec = tween(500),
        label         = "fav_count"
    )

    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Filled.Favorite,
                contentDescription = null,
                tint               = FavoriteRose,
                modifier           = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text       = "Favorites",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
        }

        // Count chip
        if (!isLoading) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(FavoriteRose.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text       = "$animatedCount",
                    style      = MaterialTheme.typography.labelLarge,
                    color      = FavoriteRose,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Empty-state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFavoritesContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(horizontal = 40.dp)
    ) {
        // SVG-style heart drawn with Compose
        HeartSvgIcon(size = 80.dp, tint = FavoriteRose.copy(alpha = 0.35f))

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
            text      = "Tap ♥ on any photo or long-press a tile to add it here",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
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
