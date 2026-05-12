package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = PhotoIntelligence::class)
@Entity(tableName = "photo_intelligence_fts")
data class PhotoIntelligenceFts(
    val rawText: String,
    val labels: String
)
