package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    // ── Write ────────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrashItem)

    /** Remove a single item by its Room primary key. */
    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)

    /** Wipe every row in the trash table. */
    @Query("DELETE FROM trash")
    suspend fun clearAll()

    // ── Read (one-shot) ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashItem>

    /**
     * Items whose [TrashItem.deletedAt] is earlier than [cutoff].
     * Caller should pass `System.currentTimeMillis() - 30_days_ms` as [cutoff].
     */
    @Query("SELECT * FROM trash WHERE deletedAt < :cutoff")
    suspend fun getExpiredItems(cutoff: Long): List<TrashItem>

    // ── Read (reactive Flows) ────────────────────────────────────────────────────

    /** All trash items, newest-first, emitting on every change. */
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun getAllFlow(): Flow<List<TrashItem>>

    /** Reactive count of items in the trash. */
    @Query("SELECT COUNT(*) FROM trash")
    fun getCount(): Flow<Int>

    /**
     * Reactive total *item count* (real file-size is resolved via MediaStore,
     * so this intentionally returns item count rather than a fake SUM).
     */
    @Query("SELECT COUNT(*) FROM trash")
    fun getTotalItems(): Flow<Int>
}
