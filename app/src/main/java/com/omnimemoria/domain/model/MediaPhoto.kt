package com.omnimemoria.domain.model

import android.net.Uri

data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val latitude: Double?,
    val longitude: Double?
)
