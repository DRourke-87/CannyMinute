package com.projectz.cannyminute.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityServiceState {

    fun isCheckoutServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) return false

        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedService = ComponentName(
            context,
            CheckoutAccessibilityService::class.java
        ).flattenToString()

        val splitter = TextUtils.SimpleStringSplitter(':').apply {
            setString(enabledServicesSetting)
        }
        while (splitter.hasNext()) {
            val candidate = splitter.next()
            if (candidate.equals(expectedService, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
