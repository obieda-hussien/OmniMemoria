package com.omnimemoria.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaIntegrityCheckedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChecked(item: MediaIntegrityChecked)

    @Query("SELECT id FROM media_integrity_checked")
    suspend fun getCheckedIds(): List<Long>

    @Query("DELETE FROM media_integrity_checked WHERE checkedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}
