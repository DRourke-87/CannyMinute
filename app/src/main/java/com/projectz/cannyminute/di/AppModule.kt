package com.projectz.cannyminute.di

import android.content.Context
import androidx.room.Room
import com.projectz.cannyminute.data.log.AppDatabase
import com.projectz.cannyminute.data.log.DetectionEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "canny_minute.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDetectionEventDao(database: AppDatabase): DetectionEventDao {
        return database.detectionEventDao()
    }
}

