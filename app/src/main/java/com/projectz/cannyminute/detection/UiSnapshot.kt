package com.projectz.cannyminute.detection

data class UiSnapshot(
    val packageName: String,
    val visibleTexts: Set<String>,
    val viewIds: Set<String>
)

data class RuleMatchResult(
    val shouldTrigger: Boolean,
    val confidence: Float,
    val threshold: Float,
    val matchedSignals: List<String>
)

