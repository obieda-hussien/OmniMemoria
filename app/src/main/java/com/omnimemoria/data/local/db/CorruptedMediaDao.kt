package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CorruptedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CorruptedMedia)

    @Query("SELECT id FROM corrupted_media")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM corrupted_media WHERE id = :id")
    suspend fun delete(id: Long)
}
