package com.omnimemoria.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.detail.PhotoDetailScreen
import com.omnimemoria.ui.home.HomeScreen
import com.omnimemoria.ui.settings.SettingsScreen

object AppRoutes {
    const val Home     = "home"
    const val Detail   = "detail/{photoId}"
    const val Settings = "settings"

    fun detail(photoId: Long): String = "detail/$photoId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    // SharedTransitionLayout creates the SharedTransitionScope that all
    // shared-element participants (grid thumbnails ↔ detail viewer) reference.
    SharedTransitionLayout {
        // Provide SharedTransitionScope globally via CompositionLocal so that
        // GalleryScreen / PhotoDetailScreen can access it without threading
        // the scope through every intermediate composable.
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController    = navController,
                startDestination = AppRoutes.Home
            ) {
                // ── Home (Gallery + Albums + Search + Vault tabs) ─────────────
                // `this` inside composable { } is AnimatedContentScope,
                // which implements AnimatedVisibilityScope.
                composable(AppRoutes.Home) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        HomeScreen(
                            onPhotoClick = { photoId ->
                                navController.navigate(AppRoutes.detail(photoId))
                            },
                            onSettingsClick = {
                                navController.navigate(AppRoutes.Settings)
                            }
                        )
                    }
                }

                // ── Full-Screen Photo Detail ───────────────────────────────────
                composable(
                    route     = AppRoutes.Detail,
                    arguments = listOf(navArgument("photoId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        PhotoDetailScreen(
                            photoId = photoId,
                            onBack  = { navController.popBackStack() }
                        )
                    }
                }

                // ── Settings ──────────────────────────────────────────────────
                composable(AppRoutes.Settings) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        SettingsScreen()
                    }
                }
            }
        }
    }
}
