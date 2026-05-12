package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash")
data class TrashItem(
    @PrimaryKey
    val id: Long,
    val originalPath: String,
    val mediaStoreId: Long,
    val deletedAt: Long,
    val mediaType: String
)
