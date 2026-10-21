package com.omnimemoria.data.intelligence

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class IndexImage(val id: Long, val uri: Uri, val modified: Long, val size: Long)

@Singleton
class IndexImageSource @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun page(after: Long, ids: List<Long>? = null): List<IndexImage> = withContext(Dispatchers.IO) {
        val terms = mutableListOf("media_type = 1", "_id > ?", "_size > 0")
        if (Build.VERSION.SDK_INT >= 30) terms += "is_trashed = 0"
        if (ids != null) {
            val valid = ids.filter { it > 0 }.distinct()
            if (valid.isEmpty()) return@withContext emptyList()
            terms += "_id IN (${valid.joinToString()})"
        }
        val selection = terms.joinToString(" AND ")
        val projection = arrayOf("_id", "date_modified", "_size")
        val collection = MediaStore.Files.getContentUri("external")
        val cursor = if (Build.VERSION.SDK_INT >= 26) context.contentResolver.query(collection, projection,
            Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(after.toString()))
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "_id ASC")
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 100)
            }, null)
        else context.contentResolver.query(collection, projection, selection, arrayOf(after.toString()), "_id ASC LIMIT 100")
        (cursor ?: throw IOException("Cannot read the photo library")).use {
            val result = ArrayList<IndexImage>(100)
            while (result.size < 100 && it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = it.getLong(0)
                if (id > after) result += IndexImage(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), it.getLong(1), it.getLong(2))
            }
            result
        }
    }
}
