package com.projectz.cannyminute.data.log

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_events")
data class DetectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMillis: Long,
    val packageName: String,
    val confidence: Float,
    val action: String,
    val matchedSignalsCsv: String,
    val diagnosticText: String?
)

