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
import androidx.compose.runtime.remember
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
import com.omnimemoria.ui.favorites.FavoritesScreen
import com.omnimemoria.ui.home.HomeScreen
import com.omnimemoria.ui.settings.SettingsScreen
import com.omnimemoria.ui.trash.TrashScreen

object AppRoutes {
    const val Home      = "home"
    const val Detail    = "detail/{photoId}?bucketId={bucketId}&externalUri={externalUri}"
    const val Folder    = "folder/{bucketId}"
    const val Video     = "video/{mediaId}?externalUri={externalUri}"
    const val Settings  = "settings"
    const val Favorites = "favorites"
    const val Trash     = "trash"

    fun detail(photoId: Long, bucketId: String? = null, externalUri: String? = null): String {
        val base =
            if (bucketId.isNullOrBlank()) "detail/$photoId"
            else "detail/$photoId?bucketId=${android.net.Uri.encode(bucketId)}"

        return if (externalUri != null) {
            val delim = if (base.contains("?")) "&" else "?"
            "$base${delim}externalUri=${android.net.Uri.encode(externalUri)}"
        } else {
            base
        }
    }

    fun folder(bucketId: String): String = "folder/${android.net.Uri.encode(bucketId)}"

    fun video(mediaId: Long, externalUri: String? = null): String {
        return if (externalUri != null) "video/$mediaId?externalUri=${android.net.Uri.encode(externalUri)}"
        else "video/$mediaId"
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(externalUri: String? = null, intentType: String? = null) {
    val navController = rememberNavController()

    val startDestination = remember(externalUri, intentType) {
        if (externalUri != null) {
            val isVideo = intentType?.startsWith("video/") == true
            if (isVideo) {
                AppRoutes.video(0L, externalUri)
            } else {
                AppRoutes.detail(0L, null, externalUri)
            }
        } else {
            AppRoutes.Home
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                // ── Home (Gallery + Albums + Search + Vault tabs) ─────────────
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
                            },
                            onFavoritesClick = {
                                navController.navigate(AppRoutes.Favorites)
                            },
                            onTrashClick = {
                                navController.navigate(AppRoutes.Trash)
                            }
                        )
                    }
                }

                // ── Full-Screen Photo Detail ───────────────────────────────────
                composable(
                    route = AppRoutes.Detail,
                    arguments = listOf(
                        navArgument("photoId") { type = NavType.LongType },
                        navArgument("bucketId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("externalUri") {
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
                    val extUri = backStackEntry.arguments?.getString("externalUri")
                    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        PhotoDetailScreen(
                            photoId = photoId,
                            bucketId = bucketId,
                            externalUriStr = extUri,
                            onBack = {
                                if (extUri != null) {
                                    activity?.finish()
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            onOpenVideo = { mediaId, videoUri ->
                                navController.navigate(AppRoutes.video(mediaId, videoUri))
                            }
                        )
                    }
                }

                // ── Folder ────────────────────────────────────────────────────
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

                // ── Video ─────────────────────────────────────────────────────
                composable(
                    route = AppRoutes.Video,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.LongType },
                        navArgument("externalUri") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
                    val extUri = backStackEntry.arguments?.getString("externalUri")
                    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        VideoPlayerScreen(
                            mediaId = mediaId,
                            externalUriStr = extUri,
                            onBack = { if (extUri != null) activity?.finish() else navController.popBackStack() }
                        )
                    }
                }

                // ── Favorites ─────────────────────────────────────────────────
                composable(
                    route = AppRoutes.Favorites,
                    enterTransition = { scaleIn(tween(220), initialScale = 0.95f) + fadeIn(tween(220)) },
                    exitTransition = { scaleOut(tween(180), targetScale = 0.96f) + fadeOut(tween(180)) },
                    popExitTransition = { scaleOut(tween(180), targetScale = 0.95f) + fadeOut(tween(180)) }
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        FavoritesScreen(
                            onPhotoClick = { photoId ->
                                navController.navigate(AppRoutes.detail(photoId))
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // ── Trash ─────────────────────────────────────────────────────
                composable(
                    route = AppRoutes.Trash,
                    enterTransition = { scaleIn(tween(220), initialScale = 0.95f) + fadeIn(tween(220)) },
                    exitTransition = { scaleOut(tween(180), targetScale = 0.96f) + fadeOut(tween(180)) },
                    popExitTransition = { scaleOut(tween(180), targetScale = 0.95f) + fadeOut(tween(180)) }
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        TrashScreen(
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
