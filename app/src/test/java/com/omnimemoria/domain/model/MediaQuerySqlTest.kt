package com.omnimemoria.domain.model

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MediaQuerySqlTest {
    private fun matching(filter: FilterConfig): List<Long> = SQLiteDatabase.create(null).use { db ->
        db.execSQL("CREATE TABLE files (_id INTEGER, _size INTEGER, width INTEGER, height INTEGER, mime_type TEXT, media_type INTEGER)")
        db.execSQL("INSERT INTO files VALUES (1,68630,NULL,NULL,'image/png',1),(2,68630,1080,1440,'image/png',1),(3,68630,0,0,'image/png',1),(4,0,NULL,NULL,'image/png',1),(5,68630,0,NULL,'image/png',1)")
        val (selection, args) = MediaQuerySql.selection(filter)
        db.rawQuery("SELECT _id FROM files WHERE $selection ORDER BY _id", args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
    }

    @Test fun validMediaWithUnknownDimensionsIsVisibleWhileKnownInvalidRowsStayHidden() {
        assertEquals(listOf(1L, 2L), matching(FilterConfig()))
    }

    @Test fun explicitResolutionFilterStillRequiresKnownMatchingDimensions() {
        assertEquals(listOf(2L), matching(FilterConfig(minResolutionMp = 0.5f)))
    }
}
