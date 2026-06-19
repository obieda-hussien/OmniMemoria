package com.omnimemoria.data.repository

import com.omnimemoria.data.local.db.FavoritePhoto
import com.omnimemoria.data.local.db.FavoritesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for favorite photo IDs.
 *
 * All DAO calls that change the database are [suspend]; all read operations
 * return reactive [Flow]s so UI layers can observe changes automatically.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val favoritesDao: FavoritesDao
) {

    // ── Reactive reads ───────────────────────────────────────────────────────────

    /** Emits `true` whenever [photoId] is in the favorites table. */
    fun isFavorite(photoId: Long): Flow<Boolean> =
        favoritesDao.isFavorite(photoId)

    /** Emits the full set of favorited photo IDs on every change. */
    fun getAllFavoriteIds(): Flow<Set<Long>> =
        favoritesDao.getAllFavoriteIds().map { it.toSet() }

    /** Emits the current total count of favorites. */
    fun getFavoritesCount(): Flow<Int> =
        favoritesDao.getFavoritesCount()

    /** Emits all [FavoritePhoto] rows sorted by [FavoritePhoto.addedAt] DESC. */
    fun getAllSortedByDate(): Flow<List<FavoritePhoto>> =
        favoritesDao.getAllSortedByDate()

    // ── Write ────────────────────────────────────────────────────────────────────

    /**
     * Toggles the favorite state of [photoId]:
     * - If currently **not** a favorite → inserts a new row with the current timestamp.
     * - If currently **a** favorite     → deletes the row.
     *
     * Uses [isFavorite] as a one-shot read (`.first()`) before mutating, so the
     * check and the write happen sequentially on the caller's coroutine scope.
     */
    suspend fun toggleFavorite(photoId: Long) {
        val currently = favoritesDao.isFavorite(photoId).first()
        if (currently) {
            favoritesDao.deleteFavorite(photoId)
        } else {
            favoritesDao.upsert(
                FavoritePhoto(id = photoId, addedAt = System.currentTimeMillis())
            )
        }
    }

    /** Unconditionally adds [photoId] to favorites (no-op if already present). */
    suspend fun addFavorite(photoId: Long) {
        favoritesDao.upsert(
            FavoritePhoto(id = photoId, addedAt = System.currentTimeMillis())
        )
    }

    /** Unconditionally removes [photoId] from favorites (no-op if not present). */
    suspend fun removeFavorite(photoId: Long) {
        favoritesDao.deleteFavorite(photoId)
    }
}
