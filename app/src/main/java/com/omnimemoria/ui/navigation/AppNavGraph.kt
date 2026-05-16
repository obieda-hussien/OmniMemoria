package com.omnimemoria.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnimemoria.ui.detail.PhotoDetailScreen
import com.omnimemoria.ui.home.HomeScreen
import com.omnimemoria.ui.settings.SettingsScreen

object AppRoutes {
    const val Home     = "home"
    const val Detail   = "detail/{photoId}"
    const val Settings = "settings"

    fun detail(photoId: Long): String = "detail/$photoId"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = AppRoutes.Home
    ) {
        // ── Main Home Shell (Contains TopBar, BottomNav and Tabs) ─────────────
        composable(AppRoutes.Home) {
            HomeScreen(
                onPhotoClick = { photoId ->
                    navController.navigate(AppRoutes.detail(photoId))
                },
                onSettingsClick = {
                    navController.navigate(AppRoutes.Settings)
                }
            )
        }

        // ── Full Screen Photo Detail (Immersive View) ─────────────────────────
        composable(
            route     = AppRoutes.Detail,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            PhotoDetailScreen(
                photoId = photoId,
                onBack  = { navController.popBackStack() }
            )
        }

        // ── Full Screen Settings Screen ───────────────────────────────────────
        composable(AppRoutes.Settings) {
            SettingsScreen()
        }
    }
}
