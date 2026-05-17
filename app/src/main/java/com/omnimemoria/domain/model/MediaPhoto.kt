package com.omnimemoria.domain.model

import android.net.Uri

data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long = 0L,
    val dateAdded: Long = 0L,
    val mimeType: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val latitude: Double?,
    val longitude: Double?
)
