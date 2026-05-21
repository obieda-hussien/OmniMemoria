package com.omnimemoria.data.repository

import com.omnimemoria.data.local.db.FavoritePhoto
import com.omnimemoria.data.local.db.FavoritesDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoritesDao: FavoritesDao
) {
    suspend fun toggleFavorite(photoId: Long) {
        val currentlyFavorite = favoritesDao.isFavorite(photoId).first()
        if (currentlyFavorite) {
            favoritesDao.deleteFavorite(photoId)
        } else {
            favoritesDao.insertFavorite(photoId = photoId, addedAt = System.currentTimeMillis())
        }
    }

    fun isFavorite(photoId: Long): Flow<Boolean> = favoritesDao.isFavorite(photoId)

    fun getAllFavoriteIds(): Flow<Set<Long>> = favoritesDao.getAllFavoriteIds()

    fun getAllFavoritesByAddedAtDesc(): Flow<List<FavoritePhoto>> = favoritesDao.getAllFavoritesByAddedAtDesc()
}
