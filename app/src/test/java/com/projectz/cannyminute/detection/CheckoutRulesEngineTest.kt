package com.projectz.cannyminute.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutRulesEngineTest {

    private val engine = CheckoutRulesEngine()

    @Test
    fun evaluate_triggersOnStrongCheckoutSignals() {
        val snapshot = UiSnapshot(
            packageName = "com.example.shop",
            visibleTexts = setOf("Place Order", "Payment", "Order Summary"),
            viewIds = setOf("com.example.shop:id/checkout_button")
        )

        val result = engine.evaluate(snapshot)

        assertTrue(result.shouldTrigger)
        assertTrue(result.confidence >= result.threshold)
        assertTrue(result.matchedSignals.any { it.startsWith("text:") })
        assertTrue(result.matchedSignals.any { it.startsWith("id:") })
    }

    @Test
    fun evaluate_doesNotTriggerOnSingleWeakSignal() {
        val snapshot = UiSnapshot(
            packageName = "com.example.news",
            visibleTexts = setOf("Checkout this article"),
            viewIds = emptySet()
        )

        val result = engine.evaluate(snapshot)

        assertFalse(result.shouldTrigger)
        assertTrue(result.confidence < result.threshold)
    }

    @Test
    fun evaluate_usesHigherThresholdForBrowsers() {
        val snapshot = UiSnapshot(
            packageName = "com.android.chrome",
            visibleTexts = setOf("place order", "payment", "order summary"),
            viewIds = emptySet()
        )

        val result = engine.evaluate(snapshot)

        assertEquals(0.75f, result.threshold, 0.0001f)
        assertFalse(result.shouldTrigger)
    }

    @Test
    fun evaluate_clampsConfidenceToOne() {
        val snapshot = UiSnapshot(
            packageName = "com.example.shop",
            visibleTexts = setOf(
                "checkout",
                "place order",
                "buy now",
                "pay now",
                "proceed to payment",
                "complete purchase",
                "order summary",
                "shipping",
                "payment",
                "cart total",
                "subtotal",
                "tax",
                "review order"
            ),
            viewIds = setOf("checkout", "pay", "buy", "place_order", "review_order")
        )

        val result = engine.evaluate(snapshot)

        assertEquals(1.0f, result.confidence, 0.0001f)
        assertTrue(result.shouldTrigger)
    }
}

