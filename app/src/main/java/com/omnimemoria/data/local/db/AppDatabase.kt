package com.omnimemoria.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PhotoIntelligence::class,
        PhotoIntelligenceFts::class,
        TrashItem::class,
        FavoritePhoto::class,
        SortPreset::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoIntelligenceDao(): PhotoIntelligenceDao
    abstract fun trashDao(): TrashDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun sortPresetDao(): SortPresetDao
}
