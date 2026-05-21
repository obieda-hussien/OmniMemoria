package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface FavoritesDao {
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :photoId)")
    fun isFavorite(photoId: Long): Flow<Boolean>

    @Query("SELECT id FROM favorites")
    fun getAllFavoriteIdsList(): Flow<List<Long>>

    fun getAllFavoriteIds(): Flow<Set<Long>> = getAllFavoriteIdsList().map { it.toSet() }

    @Query("INSERT OR REPLACE INTO favorites(id, addedAt) VALUES(:photoId, :addedAt)")
    suspend fun insertFavorite(photoId: Long, addedAt: Long)

    @Query("DELETE FROM favorites WHERE id = :photoId")
    suspend fun deleteFavorite(photoId: Long)

    @Query("SELECT COUNT(*) FROM favorites")
    fun getFavoritedPhotosCount(): Flow<Int>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoritesByAddedAtDesc(): Flow<List<FavoritePhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoritePhoto)

    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoritePhoto>
}
