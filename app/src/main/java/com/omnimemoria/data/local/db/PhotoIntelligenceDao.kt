package com.omnimemoria.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoIntelligenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PhotoIntelligence)

    @Query("SELECT * FROM photo_intelligence WHERE id = :id")
    suspend fun getById(id: Long): PhotoIntelligence?

    @Query("SELECT * FROM photo_intelligence")
    suspend fun getAll(): List<PhotoIntelligence>

    @Query("SELECT * FROM photo_intelligence WHERE isVaultItem = 0")
    fun getGalleryPhotos(): PagingSource<Int, PhotoIntelligence>

    @Query("SELECT * FROM photo_intelligence WHERE isVaultItem = 1")
    fun getVaultPhotos(): PagingSource<Int, PhotoIntelligence>

    @Query("SELECT id FROM photo_intelligence WHERE isVaultItem = 1")
    suspend fun getVaultPhotoIds(): List<Long>
}
