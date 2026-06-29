package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_integrity_checked")
data class MediaIntegrityChecked(
    @PrimaryKey val id: Long,
    val checkedAt: Long
)
