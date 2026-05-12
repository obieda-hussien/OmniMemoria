package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoritePhoto(
    @PrimaryKey
    val id: Long,
    val addedAt: Long
)
