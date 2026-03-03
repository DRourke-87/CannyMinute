package com.projectz.cannyminute.data.log

import javax.inject.Inject
import javax.inject.Singleton

data class DetectionDecisionLog(
    val timestampEpochMillis: Long,
    val packageName: String,
    val confidence: Float,
    val action: String,
    val matchedSignals: List<String>,
    val diagnosticText: String? = null
)

@Singleton
class DetectionEventLogger @Inject constructor(
    private val detectionEventDao: DetectionEventDao
) {
    suspend fun log(decisionLog: DetectionDecisionLog, diagnosticsEnabled: Boolean) {
        val safeDiagnosticText = if (diagnosticsEnabled) {
            decisionLog.diagnosticText?.take(MAX_DIAGNOSTIC_LENGTH)
        } else {
            null
        }

        val entity = DetectionEventEntity(
            timestampEpochMillis = decisionLog.timestampEpochMillis,
            packageName = decisionLog.packageName,
            confidence = decisionLog.confidence,
            action = decisionLog.action,
            matchedSignalsCsv = decisionLog.matchedSignals.joinToString(separator = ","),
            diagnosticText = safeDiagnosticText
        )
        detectionEventDao.insert(entity)
    }

    private companion object {
        const val MAX_DIAGNOSTIC_LENGTH = 256
    }
}

