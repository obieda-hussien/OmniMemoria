package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_intelligence")
data class PhotoIntelligence(
    @PrimaryKey
    val id: Long,
    val rawText: String,
    val labels: String,
    val hasPhoneNumber: Boolean,
    val hasEmail: Boolean,
    val hasFaces: Boolean,
    val dominantColorHex: String,
    val secondaryColorHex: String,
    val pHash: String,
    val isVaultItem: Boolean = false,
    val isIndexed: Boolean = false,
    val indexedAt: Long?,
    val embeddingId: Long?
)
