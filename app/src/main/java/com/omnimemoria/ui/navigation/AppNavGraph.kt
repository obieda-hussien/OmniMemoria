package com.omnimemoria.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnimemoria.ui.detail.PhotoDetailScreen
import com.omnimemoria.ui.gallery.FolderScreen
import com.omnimemoria.ui.gallery.GalleryScreen

object AppRoutes {
    const val Gallery = "gallery"
    const val Detail = "detail/{photoId}"
    const val Folder = "folder/{bucketId}"
    const val VaultRoot = "vault"

    fun detail(photoId: Long): String = "detail/$photoId"
    fun folder(bucketId: String): String = "folder/$bucketId"
}

@Composable
fun AppNavGraph(isVaultUnlocked: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Gallery
    ) {
        composable(AppRoutes.Gallery) {
            GalleryScreen(
                onPhotoClick = { photoId -> navController.navigate(AppRoutes.detail(photoId)) },
                onFolderClick = { bucketId -> navController.navigate(AppRoutes.folder(bucketId)) }
            )
        }

        composable(
            route = AppRoutes.Detail,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            PhotoDetailScreen(
                photoId = photoId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.Folder,
            arguments = listOf(navArgument("bucketId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getString("bucketId").orEmpty()
            FolderScreen(bucketId = bucketId)
        }

        if (isVaultUnlocked) {
            vaultNavGraph()
        }
    }
}
