package com.omnimemoria.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnimemoria.ui.LocalNavAnimatedVisibilityScope
import com.omnimemoria.ui.LocalSharedTransitionScope
import com.omnimemoria.ui.albums.FolderDetailScreen
import com.omnimemoria.ui.detail.PhotoDetailScreen
import com.omnimemoria.ui.detail.VideoPlayerScreen
import com.omnimemoria.ui.home.HomeScreen
import com.omnimemoria.ui.settings.SettingsScreen

object AppRoutes {
    const val Home     = "home"
    const val Detail   = "detail/{photoId}?bucketId={bucketId}"
    const val Folder   = "folder/{bucketId}"
    const val Video    = "video/{mediaId}"
    const val Settings = "settings"

    fun detail(photoId: Long, bucketId: String? = null): String {
        return if (bucketId.isNullOrBlank()) "detail/$photoId"
        else "detail/$photoId?bucketId=${android.net.Uri.encode(bucketId)}"
    }
    fun folder(bucketId: String): String = "folder/${android.net.Uri.encode(bucketId)}"
    fun video(mediaId: Long): String = "video/$mediaId"
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
                startDestination = AppRoutes.Home,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
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
                            onFolderClick = { bucketId ->
                                navController.navigate(AppRoutes.folder(bucketId))
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
                    arguments = listOf(
                        navArgument("photoId") { type = NavType.LongType },
                        navArgument("bucketId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                    enterTransition = {
                        scaleIn(animationSpec = tween(220), initialScale = 0.95f) + fadeIn(tween(220))
                    },
                    exitTransition = {
                        scaleOut(animationSpec = tween(180), targetScale = 0.96f) + fadeOut(tween(180))
                    },
                    popEnterTransition = {
                        scaleIn(animationSpec = tween(220), initialScale = 0.96f) + fadeIn(tween(220))
                    },
                    popExitTransition = {
                        scaleOut(animationSpec = tween(180), targetScale = 0.95f) + fadeOut(tween(180))
                    }
                ) { backStackEntry ->
                    val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
                    val bucketId = backStackEntry.arguments?.getString("bucketId")
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        PhotoDetailScreen(
                            photoId = photoId,
                            bucketId = bucketId,
                            onBack  = { navController.popBackStack() },
                            onOpenVideo = { mediaId ->
                                navController.navigate(AppRoutes.video(mediaId))
                            }
                        )
                    }
                }

                composable(
                    route = AppRoutes.Folder,
                    arguments = listOf(navArgument("bucketId") { type = NavType.StringType })
                ) { backStackEntry ->
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        FolderDetailScreen(
                            onPhotoClick = { photoId ->
                                val bucketId = backStackEntry.arguments?.getString("bucketId") ?: ""
                                navController.navigate(AppRoutes.detail(photoId, bucketId))
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = AppRoutes.Video,
                    arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        VideoPlayerScreen(
                            mediaId = mediaId,
                            onBack = { navController.popBackStack() }
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
