package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrashItem)

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trash")
    suspend fun clearAll()

    /** جديد — لجلب صف واحد بالـ ID عشان نبني الـ URI الصح */
    @Query("SELECT * FROM trash WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrashItem?

    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashItem>

    @Query("SELECT * FROM trash WHERE deletedAt < :cutoff")
    suspend fun getExpiredItems(cutoff: Long): List<TrashItem>

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun getAllFlow(): Flow<List<TrashItem>>

    @Query("SELECT COUNT(*) FROM trash")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM trash")
    fun getTotalItems(): Flow<Int>
}
