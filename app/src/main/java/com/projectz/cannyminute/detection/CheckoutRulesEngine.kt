package com.projectz.cannyminute.detection

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class CheckoutRulesEngine @Inject constructor() {
    private val strongCheckoutTextSignals = listOf(
        "checkout",
        "place order",
        "buy now",
        "pay now",
        "proceed to payment",
        "complete purchase"
    )

    private val supportingTextSignals = listOf(
        "order summary",
        "shipping",
        "payment",
        "cart total",
        "subtotal",
        "tax",
        "review order"
    )

    private val checkoutIdSignals = listOf(
        "checkout",
        "buy",
        "pay",
        "place_order",
        "review_order"
    )

    private val perAppThresholdOffset = mapOf(
        "com.android.chrome" to 0.05f,
        "org.mozilla.firefox" to 0.05f
    )

    fun evaluate(snapshot: UiSnapshot): RuleMatchResult {
        val normalizedTexts = snapshot.visibleTexts
            .map(::normalize)
            .filter { it.length >= MIN_TEXT_LENGTH }

        val normalizedIds = snapshot.viewIds
            .map(::normalize)

        var confidence = 0f
        val matchedSignals = linkedSetOf<String>()

        strongCheckoutTextSignals.forEach { signal ->
            if (normalizedTexts.any { it.contains(signal) }) {
                confidence += STRONG_TEXT_WEIGHT
                matchedSignals += "text:$signal"
            }
        }

        supportingTextSignals.forEach { signal ->
            if (normalizedTexts.any { it.contains(signal) }) {
                confidence += SUPPORTING_TEXT_WEIGHT
                matchedSignals += "support:$signal"
            }
        }

        checkoutIdSignals.forEach { signal ->
            if (normalizedIds.any { it.contains(signal) }) {
                confidence += ID_WEIGHT
                matchedSignals += "id:$signal"
            }
        }

        val hasTextMatch = matchedSignals.any { it.startsWith("text:") }
        val hasIdMatch = matchedSignals.any { it.startsWith("id:") }
        if (hasTextMatch && hasIdMatch) {
            confidence += MULTI_SIGNAL_BONUS
        }

        val packageOffset = perAppThresholdOffset[snapshot.packageName] ?: 0f
        val threshold = (BASE_THRESHOLD + packageOffset).coerceIn(0.50f, 0.90f)
        val clampedConfidence = min(confidence, 1f)

        return RuleMatchResult(
            shouldTrigger = clampedConfidence >= threshold,
            confidence = clampedConfidence,
            threshold = threshold,
            matchedSignals = matchedSignals.toList()
        )
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase(Locale.US)
    }

    private companion object {
        const val BASE_THRESHOLD = 0.70f
        const val STRONG_TEXT_WEIGHT = 0.45f
        const val SUPPORTING_TEXT_WEIGHT = 0.10f
        const val ID_WEIGHT = 0.30f
        const val MULTI_SIGNAL_BONUS = 0.15f
        const val MIN_TEXT_LENGTH = 2
    }
}

