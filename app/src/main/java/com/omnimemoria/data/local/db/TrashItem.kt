package com.omnimemoria.data.local.db

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
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
) {
    val expiresAt: Long
        get() = deletedAt + 30L * 24 * 60 * 60 * 1_000

    val daysUntilExpiry: Int
        get() = ((expiresAt - System.currentTimeMillis()) / 86_400_000L)
            .toInt()
            .coerceAtLeast(0)

    /** URI مُعاد بناؤه من mediaStoreId + mediaType — بيُستخدم في الـ UI مباشرة */
    val contentUri: Uri
        get() {
            val isVideo = mediaType.startsWith("video/", ignoreCase = true)
            val base = if (isVideo)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            return ContentUris.withAppendedId(base, mediaStoreId)
        }
}
