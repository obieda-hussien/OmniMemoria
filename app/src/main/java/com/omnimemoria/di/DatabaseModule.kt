package com.omnimemoria.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omnimemoria.data.local.db.AppDatabase
import com.omnimemoria.data.local.db.CorruptedMediaDao
import com.omnimemoria.data.local.db.MediaIntegrityCheckedDao
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
import com.omnimemoria.data.local.db.SortPresetDao
import com.omnimemoria.data.local.db.FavoritesDao
import com.omnimemoria.data.local.db.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS corrupted_media (id INTEGER NOT NULL PRIMARY KEY, detectedAt INTEGER NOT NULL, reason TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS media_integrity_checked (id INTEGER NOT NULL PRIMARY KEY, checkedAt INTEGER NOT NULL)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sort_presets ADD COLUMN filterConfig TEXT NOT NULL DEFAULT ''")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "omnimemoria.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun providePhotoIntelligenceDao(database: AppDatabase): PhotoIntelligenceDao {
        return database.photoIntelligenceDao()
    }

    @Provides
    fun provideSortPresetDao(database: AppDatabase): SortPresetDao {
        return database.sortPresetDao()
    }

    @Provides
    fun provideFavoritesDao(database: AppDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Provides
    fun provideTrashDao(database: AppDatabase): TrashDao {
        return database.trashDao()
    }

    @Provides
    fun provideCorruptedMediaDao(database: AppDatabase): CorruptedMediaDao {
        return database.corruptedMediaDao()
    }

    @Provides
    fun provideMediaIntegrityCheckedDao(database: AppDatabase): MediaIntegrityCheckedDao {
        return database.mediaIntegrityCheckedDao()
    }
}
