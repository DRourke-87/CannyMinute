package com.projectz.cannyminute.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "canny_minute_settings")

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            protectionEnabled = preferences[PROTECTION_ENABLED] ?: false,
            cooldownDurationSeconds = (preferences[COOLDOWN_DURATION_SECONDS] ?: 10).coerceIn(5, 600),
            allowListPackages = preferences[ALLOW_LIST_PACKAGES] ?: emptySet(),
            temporaryBypassUntilEpochMillis = preferences[TEMPORARY_BYPASS_UNTIL_EPOCH_MS] ?: 0L,
            diagnosticsEnabled = preferences[DIAGNOSTICS_ENABLED] ?: false
        )
    }

    suspend fun updateProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun updateCooldownDurationSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[COOLDOWN_DURATION_SECONDS] = seconds.coerceIn(5, 600)
        }
    }

    suspend fun updateAllowList(packages: Set<String>) {
        val sanitized = packages
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        context.dataStore.edit { preferences ->
            preferences[ALLOW_LIST_PACKAGES] = sanitized
        }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DIAGNOSTICS_ENABLED] = enabled
        }
    }

    suspend fun setTemporaryBypassUntil(epochMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[TEMPORARY_BYPASS_UNTIL_EPOCH_MS] = epochMillis
        }
    }

    suspend fun startTemporaryBypass(minutes: Long) {
        val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        setTemporaryBypassUntil(until)
    }

    suspend fun clearTemporaryBypass() {
        setTemporaryBypassUntil(0L)
    }

    private companion object {
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val COOLDOWN_DURATION_SECONDS = intPreferencesKey("cooldown_duration_seconds")
        val ALLOW_LIST_PACKAGES = stringSetPreferencesKey("allow_list_packages")
        val TEMPORARY_BYPASS_UNTIL_EPOCH_MS = longPreferencesKey("temporary_bypass_until_epoch_ms")
        val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
    }
}

