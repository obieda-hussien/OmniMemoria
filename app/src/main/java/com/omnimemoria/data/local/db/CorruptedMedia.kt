package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corrupted_media")
data class CorruptedMedia(
    @PrimaryKey val id: Long,
    val detectedAt: Long,
    val reason: String
)
