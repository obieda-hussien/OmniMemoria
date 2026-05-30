package com.omnimemoria.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One entry in the "Recycle Bin" — tracks metadata only; the actual file stays in MediaStore. */
@Entity(tableName = "trash")
data class TrashItem(
    /** Matches the MediaStore `_ID` column for the trashed file. */
    @PrimaryKey
    val id: Long,

    /** Original display path / bucket name (for UI display only). */
    val originalPath: String,

    /** Same as [id] — explicit alias kept for clarity at call sites. */
    val mediaStoreId: Long,

    /** Epoch-millis when the item was moved to trash. */
    val deletedAt: Long,

    /** MIME type string, e.g. "image/jpeg" or "video/mp4". */
    val mediaType: String
) {
    // ── Computed / derived fields ─────────────────────────────────────────────

    /** Absolute timestamp (epoch-millis) when this item will be auto-deleted. */
    val expiresAt: Long
        get() = deletedAt + 30L * 24 * 60 * 60 * 1_000

    /**
     * Whole days remaining before permanent deletion.
     * Returns 0 if already expired (never negative).
     */
    val daysUntilExpiry: Int
        get() = ((expiresAt - System.currentTimeMillis()) / 86_400_000L)
            .toInt()
            .coerceAtLeast(0)
}
