package com.omnimemoria.ui.gallery
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.MediaType

import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.domain.model.SortConfig
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton يجسر حالة الـ Gallery → PhotoDetail بدون nav args معقدة.
 *
 * مسؤوليات:
 *  1. كاش الصورة اللي اتضغطت من الـ gallery — متاحة فوراً بدون IO
 *     → shared-element transition شغال من أول frame.
 *  2. الـ sort config + media filter الحاليين اللي الـ gallery كان بيستخدمهم
 *     → الـ swipe window في detail مطابق تماماً للـ grid.
 *
 * Consume-once pattern للكاش: consumePendingPhoto() بترجعه وتمسحه
 * عشان مفيش حالة قديمة تأثر على الـ navigation التاني.
 */
@Singleton
class GalleryStateHolder @Inject constructor() {

    // ── Immediate photo cache (zero-IO) ───────────────────────────────────────
    @Volatile private var _pendingPhoto: MediaPhoto? = null

    fun cachePendingPhoto(photo: MediaPhoto) {
        _pendingPhoto = photo
    }

    /** Reads and clears the cached photo atomically. */
    fun consumePendingPhoto(): MediaPhoto? {
        val photo = _pendingPhoto
        _pendingPhoto = null
        return photo
    }

    // ── Active sort + filter that produced the gallery list ───────────────────
    // Updated by GalleryViewModel / FolderDetailViewModel before navigating.
    val activeSortConfig = MutableStateFlow(SortConfig())
    val activeFilter     = MutableStateFlow(FilterConfig())
}
