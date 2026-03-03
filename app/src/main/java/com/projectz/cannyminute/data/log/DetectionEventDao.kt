package com.projectz.cannyminute.data.log

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DetectionEventDao {
    @Insert
    suspend fun insert(event: DetectionEventEntity)

    @Query("SELECT * FROM detection_events ORDER BY timestampEpochMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<DetectionEventEntity>

    @Query("DELETE FROM detection_events")
    suspend fun clearAll()
}

