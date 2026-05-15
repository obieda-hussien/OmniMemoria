package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SortPresetDao {
    @Query("SELECT * FROM sort_presets")
    fun getAll(): Flow<List<SortPreset>>

    @Query("SELECT * FROM sort_presets WHERE isDefault = 1 LIMIT 1")
    fun getDefault(): Flow<SortPreset?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: SortPreset)

    @Query("DELETE FROM sort_presets WHERE id = :id")
    suspend fun delete(id: Int)

    @Transaction
    suspend fun setDefault(id: Int) {
        clearDefault()
        markDefault(id)
    }

    @Query("UPDATE sort_presets SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE sort_presets SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: Int)

    @Query("SELECT COUNT(*) FROM sort_presets")
    suspend fun count(): Int
}
