package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrashItem)

    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashItem>
}
