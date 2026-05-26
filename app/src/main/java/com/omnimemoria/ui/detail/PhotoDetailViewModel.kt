package com.omnimemoria.ui.detail

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.FavoritePhoto
import com.omnimemoria.data.local.db.FavoritesDao
import com.omnimemoria.data.repository.MediaStoreRepository
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val favoritesDao: FavoritesDao,
    private val sortPresetRepository: SortPresetRepository,
    @ApplicationContext private val context: Context          // ← مضاف
) : ViewModel() {

    private val _photoList  = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photoList: StateFlow<List<MediaPhoto>> = _photoList.asStateFlow()

    private val _initialPage = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ── تحميل كل الصور ────────────────────────────────────────────────────────
    fun loadAllPhotos(photoId: Long, bucketId: String?, externalUriStr: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (externalUriStr != null) {
                val single = getPhotoFromUri(externalUriStr)
                if (single != null) {
                    _photoList.value  = listOf(single)
                    _initialPage.value = 0
                }
                return@launch
            }

            mediaStoreRepository.getPhotoById(photoId)?.let { seed ->
                _photoList.value  = listOf(seed)
                _initialPage.value = 0
            }

            val currentSortConfig = sortPresetRepository.getCurrentSort().first()
            val all = if (bucketId.isNullOrBlank()) {
                mediaStoreRepository.getAllNonVaultPhotos(currentSortConfig)
            } else {
                mediaStoreRepository.getAllNonVaultPhotosByFolder(bucketId, currentSortConfig)
            }

            val targetIndex = all.indexOfFirst { it.id == photoId }
            if (targetIndex < 0) {
                val single = mediaStoreRepository.getPhotoById(photoId)
                if (single != null) {
                    _photoList.value  = listOf(single)
                    _initialPage.value = 0
                }
                return@launch
            }

            _photoList.value   = all
            _initialPage.value = targetIndex
            _isFavorite.value  = favoritesDao.getAll().any { it.id == photoId }
        }
    }

    // ── Toggle Favorite ────────────────────────────────────────────────────────
    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val currently = _isFavorite.value
            if (!currently) {
                favoritesDao.upsert(FavoritePhoto(id = photoId, addedAt = System.currentTimeMillis()))
            }
            _isFavorite.value = !currently
        }
    }

    suspend fun getPhoto(photoId: Long): MediaPhoto? = withContext(Dispatchers.IO) {
        mediaStoreRepository.getPhotoById(photoId)
    }

    /**
     * يقرأ الـ metadata الحقيقية من الـ ContentResolver بدل ما يرجع object فارغ.
     *
     * الترتيب:
     *  1. ContentResolver.query → SIZE, MIME_TYPE, DATE_TAKEN, DATE_MODIFIED,
     *                               DISPLAY_NAME, WIDTH, HEIGHT
     *  2. ContentResolver.getType → fallback لـ MIME_TYPE
     *  3. BitmapFactory.Options(inJustDecodeBounds) → fallback للأبعاد لو الصورة
     *
     * ExternalStorage URIs (content://...) و File URIs (file://...) كلاهم مدعوم.
     */
    suspend fun getPhotoFromUri(uriStr: String): MediaPhoto? = withContext(Dispatchers.IO) {
        try {
            val uri = android.net.Uri.parse(uriStr)
            val cr  = context.contentResolver

            // ── 1. Query ContentResolver ──────────────────────────────────────
            var displayName  = "External Media"
            var size         = 0L
            var mimeType     = ""
            var dateTaken    = 0L
            var dateModified = 0L
            var dateAdded    = 0L
            var width        = 0
            var height       = 0

            val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )

            runCatching {
                cr.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        fun col(name: String) = cursor.getColumnIndex(name)

                        col(MediaStore.MediaColumns.DISPLAY_NAME).takeIf { it >= 0 }
                            ?.let { displayName = cursor.getString(it) ?: displayName }

                        col(MediaStore.MediaColumns.SIZE).takeIf { it >= 0 }
                            ?.let { size = cursor.getLong(it) }

                        col(MediaStore.MediaColumns.MIME_TYPE).takeIf { it >= 0 }
                            ?.let { mimeType = cursor.getString(it).orEmpty() }

                        col(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it >= 0 }
                            ?.let { dateTaken = cursor.getLong(it) }

                        col(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 }
                            ?.let { dateModified = cursor.getLong(it) }

                        col(MediaStore.MediaColumns.DATE_ADDED).takeIf { it >= 0 }
                            ?.let { dateAdded = cursor.getLong(it) }

                        col(MediaStore.MediaColumns.WIDTH).takeIf { it >= 0 }
                            ?.let { width = cursor.getInt(it) }

                        col(MediaStore.MediaColumns.HEIGHT).takeIf { it >= 0 }
                            ?.let { height = cursor.getInt(it) }
                    }
                }
            }

            // ── 2. MIME Type fallback ─────────────────────────────────────────
            if (mimeType.isBlank()) {
                mimeType = cr.getType(uri).orEmpty()
            }

            // ── 3. حاول تقدر Size من openFileDescriptor لو لسا 0 ─────────────
            if (size == 0L) {
                runCatching {
                    cr.openFileDescriptor(uri, "r")?.use { pfd ->
                        size = pfd.statSize.coerceAtLeast(0L)
                    }
                }
            }

            // ── 4. أبعاد الصورة عن طريق BitmapFactory (بدون فك ضغط كامل) ────
            if ((width == 0 || height == 0) && !mimeType.startsWith("video/", ignoreCase = true)) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    if (opts.outWidth > 0)  width  = opts.outWidth
                    if (opts.outHeight > 0) height = opts.outHeight
                }
            }

            // ── 5. اصطاد تاريخ الملف من OpenFileDescriptor لو مفيش DATE_TAKEN ─
            if (dateTaken == 0L && dateModified == 0L) {
                runCatching {
                    cr.openFileDescriptor(uri, "r")?.use { pfd ->
                        // lastModified مش متاح على ParcelFileDescriptor مباشرة —
                        // نحسب من اسم الملف لو كان فيه timestamp (Snapchat, WhatsApp…)
                        val match = Regex("""(\d{13})""").find(displayName)
                        if (match != null) dateTaken = match.value.toLongOrNull() ?: 0L
                    }
                }
            }

            MediaPhoto(
                id           = -1L,
                uri          = uri,
                name         = displayName,
                size         = size,
                mimeType     = mimeType,
                dateTaken    = dateTaken,
                dateModified = dateModified,
                dateAdded    = dateAdded,
                width        = width,
                height       = height,
                latitude     = null,
                longitude    = null
            )
        } catch (e: Exception) {
            null
        }
    }
}
