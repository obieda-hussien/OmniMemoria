package com.omnimemoria.di

import android.content.Context
import androidx.room.Room
import com.omnimemoria.data.local.db.AppDatabase
import com.omnimemoria.data.local.db.PhotoIntelligenceDao
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
        return Room.databaseBuilder(context, AppDatabase::class.java, "omnimemoria.db").build()
    }

    @Provides
    fun providePhotoIntelligenceDao(database: AppDatabase): PhotoIntelligenceDao {
        return database.photoIntelligenceDao()
    }
}
