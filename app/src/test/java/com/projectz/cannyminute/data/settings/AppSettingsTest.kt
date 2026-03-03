package com.projectz.cannyminute.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun isBypassActive_trueWhenNowBeforeBypassEnd() {
        val now = 1_000L
        val settings = AppSettings(temporaryBypassUntilEpochMillis = 2_000L)

        assertTrue(settings.isBypassActive(now))
    }

    @Test
    fun isBypassActive_falseWhenNowAtOrAfterBypassEnd() {
        val settings = AppSettings(temporaryBypassUntilEpochMillis = 2_000L)

        assertFalse(settings.isBypassActive(2_000L))
        assertFalse(settings.isBypassActive(2_001L))
    }

    @Test
    fun defaultDiagnosticsIsDisabled() {
        assertFalse(AppSettings.DEFAULT.diagnosticsEnabled)
    }

    @Test
    fun defaultProtectionIsDisabled() {
        assertFalse(AppSettings.DEFAULT.protectionEnabled)
    }
}

