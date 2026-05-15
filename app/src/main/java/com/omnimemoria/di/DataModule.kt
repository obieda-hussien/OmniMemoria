package com.omnimemoria.di

import android.content.Context
import com.omnimemoria.data.preferences.AppPreferences
import com.omnimemoria.domain.flags.FeatureFlagManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences {
        return AppPreferences(context)
    }

    @Provides
    @Singleton
    fun provideFeatureFlagManager(appPreferences: AppPreferences): FeatureFlagManager {
        return FeatureFlagManager(appPreferences)
    }
}
