package com.projectz.cannyminute.data.log

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DetectionEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun detectionEventDao(): DetectionEventDao
}

