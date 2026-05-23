package com.omnimemoria.ui.home

import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import coil3.compose.AsyncImage
import com.omnimemoria.data.repository.MediaStats
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.albums.AlbumsScreen
import com.omnimemoria.ui.gallery.GalleryScreen
import com.omnimemoria.ui.gallery.GalleryViewModel
import com.omnimemoria.ui.search.SearchScreen
import com.omnimemoria.ui.vault.VaultTabScreen
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

// ── Root Screen ──────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onPhotoClick: (Long) -> Unit,
    onFolderClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val galleryViewModel: GalleryViewModel = hiltViewModel()
    val mediaStats    by galleryViewModel.mediaStats.collectAsState()
    val dynamicAccent by galleryViewModel.dynamicAccent.collectAsState()
    val compactTopBar by galleryViewModel.compactTopBar.collectAsState()
    val context        = LocalContext.current

    val totalFormattedSize = remember(mediaStats.totalSizeBytes) {
        Formatter.formatShortFileSize(context, mediaStats.totalSizeBytes)
    }
    val photosFormattedSize = remember(mediaStats.photoSizeBytes) {
        Formatter.formatShortFileSize(context, mediaStats.photoSizeBytes)
    }
    val videosFormattedSize = remember(mediaStats.videoSizeBytes) {
        Formatter.formatShortFileSize(context, mediaStats.videoSizeBytes)
    }

    val homeNavController  = rememberNavController()
    val navBackStackEntry  by homeNavController.currentBackStackEntryAsState()
    val currentDestination  = navBackStackEntry?.destination
    val currentTab          = HomeTab.fromRoute(currentDestination?.route)

    var showSmartSheet    by rememberSaveable { mutableStateOf(false) }
    var showOnThisDay     by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Main content ─────────────────────────────────────────────────────
        NavHost(
            navController    = homeNavController,
            startDestination = HomeTab.GALLERY.route,
            modifier         = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            composable(
                route = HomeTab.GALLERY.route,
                enterTransition = { slideInHorizontally { -it / 4 } + fadeIn(tween(200)) },
                exitTransition = { slideOutHorizontally { it / 4 } + fadeOut(tween(200)) }
            ) { GalleryScreen(onPhotoClick = onPhotoClick) }
            composable(
                route = HomeTab.ALBUMS.route,
                enterTransition = { slideInHorizontally { it / 4 } + fadeIn(tween(200)) },
                exitTransition = { slideOutHorizontally { -it / 4 } + fadeOut(tween(200)) }
            ) { AlbumsScreen(onFolderClick = onFolderClick) }
            composable(
                route = HomeTab.SEARCH.route,
                enterTransition = { slideInHorizontally { it / 4 } + fadeIn(tween(200)) },
                exitTransition = { slideOutHorizontally { -it / 4 } + fadeOut(tween(200)) }
            ) {
                SearchScreen(
                    onPhotoClick = { onPhotoClick(it) },
                    onOpenSettings = onSettingsClick
                )
            }
            composable(HomeTab.VAULT.route)   {
                VaultTabScreen(onGoToSettings = onSettingsClick)
            }
        }

        // ── Floating Top Bar (Dynamic accent tints the gradient) ─────────────
        OmniTopBar(
            photoCount          = mediaStats.photoCount,
            videoCount          = mediaStats.videoCount,
            photosFormattedSize = photosFormattedSize,
            videosFormattedSize = videosFormattedSize,
            totalFormattedSize  = totalFormattedSize,
            albumCount          = mediaStats.albumCount,
            isLoading           = mediaStats.totalCount == 0 && mediaStats.totalSizeBytes == 0L,
            dynamicAccent       = dynamicAccent,
            compactMode         = compactTopBar || currentTab != HomeTab.GALLERY,
            onSettingsClick     = onSettingsClick,
            modifier            = Modifier.align(Alignment.TopCenter)
        )

        // ── Bottom area: On This Day card (dismissible) + FAB + BottomNav ────
        Column(
            modifier           = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // On This Day — بيظهر فوق الـ FAB
            AnimatedVisibility(
                visible = showOnThisDay && currentTab == HomeTab.GALLERY,
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                OnThisDayBanner(
                    memories  = galleryViewModel.onThisDayPhotos.collectAsState().value,
                    onDismiss = { showOnThisDay = false },
                    onPhotoClick = onPhotoClick
                )
            }

            // Smart FAB
            AnimatedVisibility(
                visible  = currentTab == HomeTab.GALLERY,
                enter    = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit     = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.padding(end = 24.dp, bottom = 12.dp)
            ) {
                SmartFab(
                    accent  = dynamicAccent,
                    onClick = { showSmartSheet = true }
                )
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

// ── OmniTopBar — Dynamic accent gradient ─────────────────────────────────────────

@Composable
private fun OmniTopBar(
    photoCount:          Int,
    videoCount:          Int,
    photosFormattedSize: String,
    videosFormattedSize: String,
    totalFormattedSize:  String,
    albumCount:          Int,
    isLoading:           Boolean,
    dynamicAccent:       Color?,
    compactMode:         Boolean,
    onSettingsClick:     () -> Unit,
    modifier:            Modifier = Modifier
) {
    // اللون المتحرك بـ animation من الـ default للـ dynamic
    val accentAlpha by animateFloatAsState(
        targetValue   = if (dynamicAccent != null) 0.18f else 0f,
        animationSpec = tween(800),
        label         = "accent_alpha"
    )

    val gradientColors = buildList {
        add(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
        if (dynamicAccent != null && accentAlpha > 0f) {
            add(dynamicAccent.copy(alpha = accentAlpha))
        }
        add(Color.Transparent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = gradientColors))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Brand
            Text(
                text          = "OMNIMEMORIA",
                style         = MaterialTheme.typography.labelSmall,
                color         = dynamicAccent?.let {
                    if (it.luminance() > 0.5f) it.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.primary
                } ?: MaterialTheme.colorScheme.primary,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            // Greeting
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onBackground,
                    modifier           = Modifier.size(20.dp)
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
            // Stats chips
            if (isLoading) StatsShimmerRow()
            else {
                AnimatedVisibility(
                    visible = !isLoading && !compactMode,
                    enter   = fadeIn(tween(400)) + expandVertically()
                ) {
                    StatsChipsRow(
                        photoCount = photoCount,
                        videoCount = videoCount,
                        photosFormattedSize = photosFormattedSize,
                        videosFormattedSize = videosFormattedSize,
                        totalFormattedSize = totalFormattedSize,
                        albumCount = albumCount,
                        accent = dynamicAccent
                    )
                }
                AnimatedVisibility(visible = compactMode) {
                    Text(
                        "Your memories, organized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Settings
        IconButton(
            onClick  = onSettingsClick,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    dynamicAccent?.copy(alpha = 0.2f)
                        ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
                .size(44.dp)
        ) {
            Icon(
                imageVector        = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}

// ── Stats Chips ───────────────────────────────────────────────────────────────────

@Composable
private fun StatsChipsRow(
    photoCount:          Int,
    videoCount:          Int,
    photosFormattedSize: String,
    videosFormattedSize: String,
    totalFormattedSize:  String,
    albumCount:          Int,
    accent:              Color?
) {
    val animatedPhotos by animateIntAsState(targetValue = photoCount, animationSpec = tween(600), label = "count_photos")
    val animatedVideos by animateIntAsState(targetValue = videoCount, animationSpec = tween(600), label = "count_videos")
    val animatedAlbums by animateIntAsState(targetValue = albumCount, animationSpec = tween(600), label = "count_albums")

    val chipBg = accent?.copy(alpha = 0.15f)
        ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val iconTint = accent ?: MaterialTheme.colorScheme.primary

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        StatChip(Icons.Outlined.Image, "$animatedPhotos Photos", chipBg, iconTint)
        StatChip(Icons.Outlined.Videocam, "$animatedVideos Videos", chipBg, iconTint)
        StatChip(Icons.Outlined.Image, "Photos $photosFormattedSize", chipBg, iconTint)
        StatChip(Icons.Outlined.VideoFile, "Videos $videosFormattedSize", chipBg, iconTint)
        StatChip(Icons.Outlined.SdStorage, "Total $totalFormattedSize", chipBg, iconTint)
        StatChip(Icons.Outlined.GridView, "$animatedAlbums Albums", chipBg, iconTint)
    }
}

@Composable
private fun StatChip(
    icon:     ImageVector,
    label:    String,
    bg:       Color,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Stats Shimmer ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsShimmerRow() {
    val alpha by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(
            initialValue   = 0.2f,
            targetValue    = 0.5f,
            animationSpec  = infiniteRepeatable(
                animation  = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_a"
        )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(64.dp, 72.dp, 90.dp).forEach { w ->
            Box(
                modifier = Modifier
                    .width(w).height(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
        }
    }
}

// ── On This Day Banner ────────────────────────────────────────────────────────────

@Composable
private fun OnThisDayBanner(
    memories:    List<MediaPhoto>,
    onDismiss:   () -> Unit,
    onPhotoClick: (Long) -> Unit
) {
    if (memories.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2D26A0), Color(0xFF5548D9))
                )
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint               = Color.White.copy(alpha = 0.9f),
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text       = "On This Day",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            text  = "${memories.size} memor${if (memories.size > 1) "ies" else "y"} from the past",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint               = Color.White.copy(alpha = 0.7f),
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo thumbnails row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories) { photo ->
                    OnThisDayThumb(
                        photo        = photo,
                        onPhotoClick = onPhotoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun OnThisDayThumb(photo: MediaPhoto, onPhotoClick: (Long) -> Unit) {
    val year = remember(photo.dateTaken) {
        if (photo.dateTaken > 0) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = photo.dateTaken
            cal.get(Calendar.YEAR).toString()
        } else "?"
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPhotoClick(photo.id) }
    ) {
        AsyncImage(
            model              = photo.uri,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        // Year badge at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text      = year,
                style     = MaterialTheme.typography.labelSmall,
                color     = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Smart FAB ─────────────────────────────────────────────────────────────────────

@Composable
private fun SmartFab(accent: Color?, onClick: () -> Unit) {
    val fabColor = accent ?: MaterialTheme.colorScheme.primary
    // اللون المعكوس — لو الـ accent فاتح يبقى النص داكن والعكس
    val contentColor = if (fabColor.luminance() > 0.5f) Color(0xFF1A1A2E) else Color.White

    ExtendedFloatingActionButton(
        onClick        = onClick,
        containerColor = fabColor,
        contentColor   = contentColor,
        shape          = RoundedCornerShape(18.dp),
        elevation      = FloatingActionButtonDefaults.elevation(6.dp, 2.dp)
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Smart", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// ── Bottom Nav ────────────────────────────────────────────────────────────────────

@Composable
private fun OmniBottomNav(
    currentDestination: NavDestination?,
    onTabSelected:      (HomeTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom     = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF141220).copy(alpha = 0.9f))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier       = Modifier.height(68.dp)
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick  = { onTabSelected(tab) },
                    icon     = {
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.15f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "tab_scale_${tab.route}"
                        )
                        Icon(
                            imageVector = when (tab) {
                                HomeTab.GALLERY -> if (selected) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary
                                HomeTab.ALBUMS -> if (selected) Icons.Filled.GridView else Icons.Outlined.GridView
                                HomeTab.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                HomeTab.VAULT -> if (selected) Icons.Filled.Lock else Icons.Outlined.Lock
                            },
                            contentDescription = tab.label,
                            modifier = Modifier.size(if (selected) 24.dp else 22.dp).scale(scale),
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    label = {
                        AnimatedVisibility(visible = selected) {
                            Text(tab.label, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                        }
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = MaterialTheme.colorScheme.primary,
                        indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

// ── Smart Actions Sheet ───────────────────────────────────────────────────────────

private data class SmartActionItem(
    val icon: ImageVector, val title: String, val subtitle: String, val color: Color
)

private val SmartActionItems = listOf(
    SmartActionItem(Icons.Outlined.Compress,    "Smart Compress",  "Free up space intelligently",    Color(0xFF8B7FF5)),
    SmartActionItem(Icons.Outlined.ContentCopy, "Photo DNA",       "Find & remove duplicates",       Color(0xFFFFB300)),
    SmartActionItem(Icons.Outlined.Refresh,     "Re-Index",        "Rebuild photo intelligence",     Color(0xFFFF5252)),
    SmartActionItem(Icons.Outlined.BarChart,    "Memoria Stats",   "Visualize your memory patterns", Color(0xFF7C4DFF))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartActionsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Smart Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("AI-powered tools for your memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SmartActionItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { onDismiss() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(item.color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, null, tint = item.color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(item.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Greeting ──────────────────────────────────────────────────────────────────────

private fun dynamicGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..5   -> "Good Night 🌙"
        in 6..11  -> "Good Morning ☀️"
        in 12..16 -> "Good Afternoon 🌤"
        in 17..20 -> "Good Evening 🌆"
        else      -> "Good Night 🌙"
    }
}
