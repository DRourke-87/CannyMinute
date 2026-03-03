package com.projectz.cannyminute.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectz.cannyminute.data.settings.AppSettings
import com.projectz.cannyminute.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings.DEFAULT
    )

    fun setProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateProtectionEnabled(enabled)
        }
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDiagnosticsEnabled(enabled)
        }
    }

    fun setCooldownDurationSeconds(rawInput: String) {
        val parsedSeconds = rawInput.toIntOrNull() ?: return
        viewModelScope.launch {
            settingsRepository.updateCooldownDurationSeconds(parsedSeconds)
        }
    }

    fun setAllowListFromCsv(rawInput: String) {
        val packages = rawInput
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        viewModelScope.launch {
            settingsRepository.updateAllowList(packages)
        }
    }

    fun clearBypass() {
        viewModelScope.launch {
            settingsRepository.clearTemporaryBypass()
        }
    }
}

