package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sort_presets")
data class SortPreset(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val sortBy: String,
    val sortOrder: String,
    val groupBy: String?,
    val isDefault: Boolean
)
