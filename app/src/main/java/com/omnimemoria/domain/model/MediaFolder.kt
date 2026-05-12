package com.omnimemoria.domain.model

import android.net.Uri

data class MediaFolder(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val photoCount: Int,
    val latestPhotoDate: Long
)
