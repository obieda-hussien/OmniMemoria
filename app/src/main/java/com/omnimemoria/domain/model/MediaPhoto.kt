package com.omnimemoria.domain.model

import android.net.Uri

data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    /** Milliseconds since epoch from EXIF/camera. May be 0 for screenshots or shared media. */
    val dateTaken: Long,
    /** Seconds since epoch — MediaStore convention. Use [effectiveDateMs] for display. */
    val dateModified: Long = 0L,
    /** Seconds since epoch — MediaStore convention. Use [effectiveDateMs] for display. */
    val dateAdded: Long = 0L,
    val width: Int,
    val height: Int,
    val latitude: Double?,
    val longitude: Double?
) {
    /**
     * Best-effort display timestamp in milliseconds.
     * Tries dateTaken first (most accurate), then dateModified, then dateAdded.
     * Snapchat / received images often have dateTaken = 0 but dateModified is valid.
     */
    val effectiveDateMs: Long
        get() = when {
            dateTaken    > 0 -> dateTaken
            dateModified > 0 -> dateModified * 1_000L
            dateAdded    > 0 -> dateAdded    * 1_000L
            else             -> 0L
        }
}
