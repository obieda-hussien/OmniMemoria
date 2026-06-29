package com.omnimemoria.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * OmniMemoria -- Shared Design Components
 * ==========================================
 * Single source of truth for every reusable UI primitive.
 * All screens MUST use these instead of rolling their own versions.
 *
 * Design Tokens
 * -------------
 * Card background   : Color(0xFF1E1C30)   <- SurfaceVariantDark
 * Card border       : White @ 6% opacity
 * Corner -- large   : 20.dp
 * Corner -- medium  : 16.dp
 * Corner -- chip    : 12.dp
 * Corner -- button  : 14.dp
 * Selection bar     : horizontal indigo gradient, 24.dp pill
 * Media chrome corner: 28.dp (see MediaChromeCorner)
 * Media chrome height: 64.dp (see MediaChromeHeight)
 */

// ── 1. Detail / Secondary Top Bar ─────────────────────────────────────────────
// Used by: FolderDetailScreen, SettingsScreen, any non-tab secondary screen.
// Floats over content -- no Scaffold needed.

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniDetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back pill -- matches FAB/stat-chip visual language
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .combinedClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = MaterialTheme.colorScheme.onBackground,
                modifier           = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onBackground,
                maxLines   = 1
            )
            if (subtitle != null) {
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            content           = actions
        )
    }
}

// ── 2. Section Header ──────────────────────────────────────────────────────────
// Used by: GalleryScreen, AlbumsScreen, SearchScreen, FolderDetailScreen.
// Consistent bold title + optional subtitle + optional action chip.

@Composable
fun OmniSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            OmniActionChip(label = actionLabel, icon = actionIcon, onClick = onAction)
        }
    }
}

// ── 3. Action Chip ────────────────────────────────────────────────────────────
// Used for: "Filter & Sort", "Sort", "See all", "Re-index", etc.
// Same style everywhere -- primary-tinted pill with subtle border.

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniActionChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary
        )
    }
}

// ── 4. Icon Action Button (top bar actions) ───────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniIconAction(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size(20.dp)
        )
    }
}

// ── 5. Empty State ────────────────────────────────────────────────────────────
// Used by: AlbumsScreen, SearchScreen, TrashScreen, FavoritesScreen, etc.
// Standard: 72dp icon box (22dp corners) + title + subtitle + optional CTA.
// floating=true adds an infinite float animation to the icon box (absorbs the
// per-screen float animation blocks previously duplicated in TrashEmptyState,
// EmptyFavoritesContent, and VaultDisabledState).

@Composable
fun OmniEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    val floatY = if (floating) {
        val transition = rememberInfiniteTransition(label = "empty_state_float")
        val value by transition.animateFloat(
            initialValue = 0f, targetValue = -8f,
            animationSpec = infiniteRepeatable(
                tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse
            ), label = "float_y"
        )
        value
    } else 0f

    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .offset(y = floatY.dp)
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = subtitle,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (actionIcon != null) {
                    Icon(actionIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text       = actionLabel,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── 6. Selection Action Bar ───────────────────────────────────────────────────
// Used by: GalleryScreen, FolderDetailScreen -- multi-select mode bottom bar.
// Indigo gradient pill, always floats above the bottom nav.

@Composable
fun OmniSelectionBar(
    count: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1E1C30).copy(alpha = 0.97f),
                        Color(0xFF2D26A0).copy(alpha = 0.93f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, null, tint = Color.White)
                }
                Text(
                    text       = "$count selected",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
            Row {
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                    }
                }
                if (onMore != null) {
                    IconButton(onClick = onMore) {
                        Icon(Icons.Outlined.MoreVert, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ── 6b. Info Banner ───────────────────────────────────────────────────────────
// Used by: FavoritesScreen, TrashScreen.
// Replaces per-screen FavoritesInfoBanner / TrashInfoBanner duplicates.

@Composable
fun OmniInfoBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 7. Omni Surface / Card ────────────────────────────────────────────────────
// Base card primitive. Use instead of Material3's Card for consistent styling.

@Composable
fun OmniSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(cornerRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

// ── 9. Media Chrome Constants and Bars ───────────────────────────────────────
// Floating opaque pill chrome that sits ON TOP of photo/video content.
// Standardizes corner radius and height that PhotoDetailScreen and
// VideoPlayerScreen previously diverged on (32dp/68dp vs 20dp/60dp).
// 28dp/64dp is the standardized middle ground -- change in one place.

val MediaChromeCorner = 28.dp
val MediaChromeHeight = 64.dp

@Composable
fun OmniMediaTopBar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit,
    center: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(MediaChromeCorner))
            .background(com.omnimemoria.ui.navigation.NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(MediaChromeCorner))
            .height(MediaChromeHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { leading() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) { center() }
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
fun OmniMediaBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(MediaChromeCorner))
            .background(com.omnimemoria.ui.navigation.NavigationSurfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(MediaChromeCorner))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(MediaChromeHeight).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// ── 8. Settings Group Card ────────────────────────────────────────────────────
// Wraps a group of settings items inside a titled section.

@Composable
fun OmniSettingsGroup(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        // Section label row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(start = 4.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
        }
        // Content card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141220))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
        ) {
            Column(content = content)
        }
    }
}
