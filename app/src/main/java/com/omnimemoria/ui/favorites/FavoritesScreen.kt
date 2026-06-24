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
import com.omnimemoria.ui.components.OmniDetailTopBar
import com.omnimemoria.ui.components.OmniEmptyState
import com.omnimemoria.ui.components.OmniInfoBanner
import com.omnimemoria.ui.gallery.PhotoCell

// ── Rose color shared with the gallery badge ──────────────────────────────────
private val FavoriteRose = Color(0xFFFF4B6E)
private const val TopScrimMidStop = 0.85f
private const val TopScrimMidAlpha = 0.9f

// ── Top padding so the content sits below the top bar ────────────────────────
private val ContentTopPadding = 110.dp


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
                    OmniEmptyState(
                        icon     = Icons.Outlined.FavoriteBorder,
                        title    = "No favorites yet",
                        subtitle = "Tap the heart on any photo, or long-press a photo, to add it here",
                        floating = true
                    )
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

        OmniDetailTopBar(
            title    = "Favorites",
            subtitle = if (count > 0) "$count item${if (count != 1) "s" else ""}" else null,
            onBack   = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

// ── Header: section title + count ─────────────────────────────────────────────

@Composable
private fun FavoritesHeader(isLoading: Boolean) {
    Column {
        OmniInfoBanner(
            icon = Icons.Filled.Favorite,
            text = "Long-press a photo to remove it from favorites.",
            tint = FavoriteRose,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        )
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
