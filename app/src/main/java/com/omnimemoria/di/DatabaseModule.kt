package com.omnimemoria.di

import android.content.Context
import androidx.room.Room
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
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "omnimemoria.db")
            .fallbackToDestructiveMigration()
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
