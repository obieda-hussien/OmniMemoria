package com.omnimemoria.ui.home

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnimemoria.ui.gallery.GalleryScreen
import com.omnimemoria.ui.theme.AmberVibe
import com.omnimemoria.ui.theme.RoseMemory
import java.util.Calendar

enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    GALLERY("home/gallery", "Gallery", Icons.Outlined.PhotoLibrary),
    ALBUMS("home/albums", "Albums", Icons.Outlined.GridView),
    SEARCH("home/search", "Search", Icons.Outlined.Search),
    VAULT("home/vault", "Vault", Icons.Outlined.Lock);

    companion object {
        fun fromRoute(route: String?): HomeTab = entries.firstOrNull { it.route == route } ?: GALLERY
    }
}

@Composable
fun HomeScreen(onPhotoClick: (Long) -> Unit, onSettingsClick: () -> Unit) {
    val homeNavController = rememberNavController()
    val navBackStackEntry by homeNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentTab = HomeTab.fromRoute(currentDestination?.route)

    var showSmartSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        extendBody = true, // ضروري جداً عشان البار السفلي يكون عائم فوق المحتوى
        extendBodyBehindAppBar = true, // ضروري عشان البار العلوي يكون عائم
        topBar = { OmniTopBar(onSettingsClick = onSettingsClick) },
        bottomBar = {
            OmniBottomNav(
                currentDestination = currentDestination,
                onTabSelected = { tab ->
                    homeNavController.navigate(tab.route) {
                        popUpTo(homeNavController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentTab == HomeTab.GALLERY,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                SmartFab(onClick = { showSmartSheet = true })
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        // مررنا 0.dp لأننا بنعوض المسافات داخل شاشة الـ Gallery نفسها ليكون التمرير خلف الأشرطة
        NavHost(
            navController = homeNavController,
            startDestination = HomeTab.GALLERY.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(HomeTab.GALLERY.route) { GalleryScreen(onPhotoClick = onPhotoClick) }
            composable(HomeTab.ALBUMS.route) { AlbumsPlaceholderScreen() }
            composable(HomeTab.SEARCH.route) { SearchPlaceholderScreen() }
            composable(HomeTab.VAULT.route) { VaultPlaceholderScreen() }
        }
    }

    if (showSmartSheet) {
        SmartActionsSheet(onDismiss = { showSmartSheet = false })
    }
}

// Custom Floating Top Bar
@Composable
private fun OmniTopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // النصوص على اليسار
        Column {
            Text(
                text = "OMNIMEMORIA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dynamicGreeting(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Photos 23.4 GB · 89 Albums",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // زر الإعدادات على اليمين
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SmartFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Smart", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// Custom Floating Glass Bottom Nav
@Composable
private fun OmniBottomNav(currentDestination: NavDestination?, onTabSelected: (HomeTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp) // المسافة من الأسفل لجعله عائم
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF141220).copy(alpha = 0.85f)) // لون شفاف Glassmorphism
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp)
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = {
                        if (selected) { // إظهار النص للعنصر المحدد فقط لمظهر أنظف
                            Text(tab.label, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// Smart Sheet 
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartActionsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Smart Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val actions = listOf(
                Triple("Smart Compress", "Free up space intelligently", MaterialTheme.colorScheme.primary),
                Triple("Photo DNA", "Find similar photos", Color(0xFFFFB300)),
                Triple("Re-Index", "Rebuild photo index", Color(0xFFFF5252)),
                Triple("Memoria Stats", "Your memory stats", Color(0xFF7C4DFF))
            )
            actions.forEach { (title, subtitle, color) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { onDismiss() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = color)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// Placeholders
@Composable fun AlbumsPlaceholderScreen() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Albums Screen") } }
@Composable fun SearchPlaceholderScreen() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Search Screen") } }
@Composable fun VaultPlaceholderScreen() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Vault Screen") } }

private fun dynamicGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good Morning"
        else -> "Good Evening"
    }
}
