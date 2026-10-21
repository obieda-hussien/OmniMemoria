package com.omnimemoria.data.local.db

import androidx.room.ColumnInfo
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
    val embeddingId: Long?,
    @ColumnInfo(defaultValue = "0") val completedMask: Int = 0,
    @ColumnInfo(defaultValue = "0") val sourceModified: Long = 0,
    @ColumnInfo(defaultValue = "0") val sourceSize: Long = 0,
    @ColumnInfo(defaultValue = "0") val analysisVersion: Int = 0,
    @ColumnInfo(defaultValue = "''") val indexError: String = "",
    @ColumnInfo(defaultValue = "''") val ocrBoxes: String = ""
)
