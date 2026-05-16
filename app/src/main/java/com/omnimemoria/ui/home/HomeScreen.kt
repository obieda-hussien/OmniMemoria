package com.omnimemoria.ui.home

import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnimemoria.data.repository.MediaStats
import com.omnimemoria.ui.gallery.GalleryScreen
import com.omnimemoria.ui.gallery.GalleryViewModel
import java.util.Calendar

// ── Tab definitions ──────────────────────────────────────────────────────────────

enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    GALLERY("home/gallery", "Gallery",  Icons.Outlined.PhotoLibrary),
    ALBUMS ("home/albums",  "Albums",   Icons.Outlined.GridView),
    SEARCH ("home/search",  "Search",   Icons.Outlined.Search),
    VAULT  ("home/vault",   "Vault",    Icons.Outlined.Lock);

    companion object {
        fun fromRoute(route: String?): HomeTab =
            entries.firstOrNull { it.route == route } ?: GALLERY
    }
}

// ── Root screen ──────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(onPhotoClick: (Long) -> Unit, onSettingsClick: () -> Unit) {

    // ── جلب الإحصائيات الحقيقية من GalleryViewModel ─────────────────────────
    val galleryViewModel: GalleryViewModel = hiltViewModel()
    val mediaStats   by galleryViewModel.mediaStats.collectAsState()
    val context       = LocalContext.current

    // تحويل Bytes → "GB" أو "MB" أوتوماتيكياً بالـ Android Formatter
    val formattedSize = remember(mediaStats.totalSizeBytes) {
        Formatter.formatShortFileSize(context, mediaStats.totalSizeBytes)
    }

    // ── Navigation ───────────────────────────────────────────────────────────
    val homeNavController  = rememberNavController()
    val navBackStackEntry  by homeNavController.currentBackStackEntryAsState()
    val currentDestination  = navBackStackEntry?.destination
    val currentTab          = HomeTab.fromRoute(currentDestination?.route)

    var showSmartSheet by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. المحتوى الأساسي — يمتد بكامل الشاشة تحت الـ overlay
        NavHost(
            navController    = homeNavController,
            startDestination = HomeTab.GALLERY.route,
            modifier         = Modifier.fillMaxSize()
        ) {
            composable(HomeTab.GALLERY.route) { GalleryScreen(onPhotoClick = onPhotoClick) }
            composable(HomeTab.ALBUMS.route)  { AlbumsPlaceholderScreen() }
            composable(HomeTab.SEARCH.route)  { SearchPlaceholderScreen() }
            composable(HomeTab.VAULT.route)   { VaultPlaceholderScreen() }
        }

        // 2. شريط علوي عائم مع الإحصائيات الحقيقية
        OmniTopBar(
            photoCount    = mediaStats.photoCount,
            formattedSize = formattedSize,
            albumCount    = mediaStats.albumCount,
            isLoading     = mediaStats.photoCount == 0 && mediaStats.totalSizeBytes == 0L,
            onSettingsClick = onSettingsClick,
            modifier      = Modifier.align(Alignment.TopCenter)
        )

        // 3. زر عائم + Bottom Nav
        Column(
            modifier           = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(
                visible = currentTab == HomeTab.GALLERY,
                enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit    = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.padding(end = 24.dp, bottom = 16.dp)
            ) {
                SmartFab(onClick = { showSmartSheet = true })
            }

            OmniBottomNav(
                currentDestination = currentDestination,
                onTabSelected      = { tab ->
                    homeNavController.navigate(tab.route) {
                        popUpTo(homeNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            )
        }
    }

    if (showSmartSheet) {
        SmartActionsSheet(onDismiss = { showSmartSheet = false })
    }
}

// ── OmniTopBar — إحصائيات حقيقية + Shimmer loading ──────────────────────────────

@Composable
private fun OmniTopBar(
    photoCount:      Int,
    formattedSize:   String,
    albumCount:      Int,
    isLoading:       Boolean,
    onSettingsClick: () -> Unit,
    modifier:        Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // ── Brand label ──────────────────────────────────────────────────
            Text(
                text          = "OMNIMEMORIA",
                style         = MaterialTheme.typography.labelSmall,
                color         = MaterialTheme.colorScheme.primary,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            // ── Greeting row ─────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector  = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint         = MaterialTheme.colorScheme.onBackground,
                    modifier     = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text       = dynamicGreeting(),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Stats chips — حقيقية أو shimmer لحين التحميل ─────────────
            if (isLoading) {
                StatsShimmerRow()
            } else {
                AnimatedVisibility(
                    visible = true,
                    enter   = fadeIn(tween(400)) + expandVertically()
                ) {
                    StatsChipsRow(
                        photoCount    = photoCount,
                        formattedSize = formattedSize,
                        albumCount    = albumCount
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ── Settings button ──────────────────────────────────────────────────
        IconButton(
            onClick  = onSettingsClick,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .size(44.dp)
        ) {
            Icon(
                imageVector  = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint         = MaterialTheme.colorScheme.primary,
                modifier     = Modifier.size(22.dp)
            )
        }
    }
}

// ── Stats chips — ثلاث حبات صغيرة بأيقونات ──────────────────────────────────────

@Composable
private fun StatsChipsRow(photoCount: Int, formattedSize: String, albumCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatChip(
            icon  = Icons.Outlined.Image,
            label = "$photoCount"
        )
        StatChip(
            icon  = Icons.Outlined.SdStorage,
            label = formattedSize
        )
        StatChip(
            icon  = Icons.Outlined.GridView,
            label = "$albumCount Albums"
        )
    }
}

@Composable
private fun StatChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector  = icon,
            contentDescription = null,
            tint         = MaterialTheme.colorScheme.primary,
            modifier     = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Shimmer بدل الأرقام لحين انتهاء الـ query ────────────────────────────────────

@Composable
private fun StatsShimmerRow() {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(
            initialValue   = 0.25f,
            targetValue    = 0.55f,
            animationSpec  = infiniteRepeatable(
                animation  = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_alpha"
        )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val widths = listOf(64.dp, 72.dp, 88.dp)
            Box(
                modifier = Modifier
                    .width(widths[index])
                    .height(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
                    )
            )
        }
    }
}

// ── SmartFab ──────────────────────────────────────────────────────────────────────

@Composable
private fun SmartFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick          = onClick,
        containerColor   = MaterialTheme.colorScheme.primary,
        contentColor     = MaterialTheme.colorScheme.onPrimary,
        shape            = RoundedCornerShape(18.dp),
        elevation        = FloatingActionButtonDefaults.elevation(
            defaultElevation  = 6.dp,
            pressedElevation  = 2.dp
        )
    ) {
        Icon(
            imageVector  = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier     = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Smart", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// ── OmniBottomNav — floating pill ────────────────────────────────────────────────

@Composable
private fun OmniBottomNav(
    currentDestination: NavDestination?,
    onTabSelected:      (HomeTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom     = 20.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF141220).copy(alpha = 0.88f))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier       = Modifier.height(68.dp)
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = currentDestination?.hierarchy
                    ?.any { it.route == tab.route } == true

                NavigationBarItem(
                    selected  = selected,
                    onClick   = { onTabSelected(tab) },
                    icon      = {
                        Icon(
                            imageVector  = tab.icon,
                            contentDescription = tab.label,
                            modifier     = Modifier.size(if (selected) 24.dp else 22.dp)
                        )
                    },
                    label     = {
                        AnimatedVisibility(visible = selected) {
                            Text(
                                text       = tab.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 10.sp
                            )
                        }
                    },
                    alwaysShowLabel = false,
                    colors          = NavigationBarItemDefaults.colors(
                        selectedIconColor   = MaterialTheme.colorScheme.primary,
                        indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

// ── SmartActionsSheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartActionsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom     = 32.dp)
        ) {
            // ── Sheet header ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector  = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint         = MaterialTheme.colorScheme.primary,
                        modifier     = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text       = "Smart Actions",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "AI-powered tools for your memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Action items ─────────────────────────────────────────────────
            SmartActionItems.forEach { item ->
                SmartActionRow(item = item, onDismiss = onDismiss)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SmartActionRow(item: SmartActionItem, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable { onDismiss() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(item.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector  = item.icon,
                contentDescription = null,
                tint         = item.color,
                modifier     = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = item.title,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = item.subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector  = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier     = Modifier.size(18.dp)
        )
    }
}

// ── Action item data ─────────────────────────────────────────────────────────────

private data class SmartActionItem(
    val icon:     ImageVector,
    val title:    String,
    val subtitle: String,
    val color:    Color
)

private val SmartActionItems = listOf(
    SmartActionItem(
        icon     = Icons.Outlined.Compress,
        title    = "Smart Compress",
        subtitle = "Free up space intelligently",
        color    = Color(0xFF8B7FF5)
    ),
    SmartActionItem(
        icon     = Icons.Outlined.ContentCopy,
        title    = "Photo DNA",
        subtitle = "Find & remove duplicates",
        color    = Color(0xFFFFB300)
    ),
    SmartActionItem(
        icon     = Icons.Outlined.Refresh,
        title    = "Re-Index",
        subtitle = "Rebuild photo intelligence index",
        color    = Color(0xFFFF5252)
    ),
    SmartActionItem(
        icon     = Icons.Outlined.BarChart,
        title    = "Memoria Stats",
        subtitle = "Visualize your memory patterns",
        color    = Color(0xFF7C4DFF)
    )
)

// ── Placeholder screens ───────────────────────────────────────────────────────────

@Composable
fun AlbumsPlaceholderScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Albums Screen", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SearchPlaceholderScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Search Screen", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun VaultPlaceholderScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Vault Screen", style = MaterialTheme.typography.titleLarge)
    }
}

// ── Greeting ──────────────────────────────────────────────────────────────────────

private fun dynamicGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 6  -> "Good Night 🌙"
        hour < 12 -> "Good Morning ☀️"
        hour < 17 -> "Good Afternoon 🌤"
        hour < 21 -> "Good Evening 🌆"
        else      -> "Good Night 🌙"
    }
}
