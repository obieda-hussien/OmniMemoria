package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SortPresetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SortPreset)

    @Query("SELECT * FROM sort_presets")
    suspend fun getAll(): List<SortPreset>
}
