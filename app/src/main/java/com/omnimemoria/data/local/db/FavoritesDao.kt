package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    // ── Write ────────────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoritePhoto)

    @Query("DELETE FROM favorites WHERE id = :photoId")
    suspend fun deleteFavorite(photoId: Long)

    // ── Read (one-shot) ──────────────────────────────────────────────────────────
    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoritePhoto>

    // ── Read (reactive Flows) ────────────────────────────────────────────────────
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :photoId)")
    fun isFavorite(photoId: Long): Flow<Boolean>

    @Query("SELECT id FROM favorites")
    fun getAllFavoriteIds(): Flow<Set<Long>>

    @Query("SELECT COUNT(*) FROM favorites")
    fun getFavoritesCount(): Flow<Int>

    /** Returns all favorites ordered by addedAt DESC (most recently starred first). */
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllSortedByDate(): Flow<List<FavoritePhoto>>
}
