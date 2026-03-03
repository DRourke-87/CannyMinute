package com.projectz.cannyminute.ui.cooldown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectz.cannyminute.accessibility.ContinueSuppressionRegistry
import com.projectz.cannyminute.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CooldownUiState(
    val sourcePackageName: String = "",
    val confidence: Float = 0f,
    val remainingSeconds: Int = 60,
    val needsChecked: Boolean = false,
    val affordableChecked: Boolean = false,
    val cheaperChecked: Boolean = false
) {
    val canContinue: Boolean
        get() = remainingSeconds == 0 && needsChecked && affordableChecked && cheaperChecked
}

@HiltViewModel
class CooldownViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val configuredDuration = (savedStateHandle.get<Int>(CooldownActivity.EXTRA_DURATION_SECONDS) ?: 10)
        .coerceIn(5, 600)

    private val _uiState = MutableStateFlow(
        CooldownUiState(
            sourcePackageName = savedStateHandle.get<String>(CooldownActivity.EXTRA_PACKAGE_NAME).orEmpty(),
            confidence = savedStateHandle.get<Float>(CooldownActivity.EXTRA_CONFIDENCE) ?: 0f,
            remainingSeconds = configuredDuration
        )
    )
    val uiState: StateFlow<CooldownUiState> = _uiState.asStateFlow()

    init {
        startCountdown(configuredDuration)
    }

    fun updateNeedsChecked(checked: Boolean) {
        _uiState.update { state -> state.copy(needsChecked = checked) }
    }

    fun updateAffordableChecked(checked: Boolean) {
        _uiState.update { state -> state.copy(affordableChecked = checked) }
    }

    fun updateCheaperChecked(checked: Boolean) {
        _uiState.update { state -> state.copy(cheaperChecked = checked) }
    }

    fun activateTenMinuteBypass() {
        viewModelScope.launch {
            settingsRepository.startTemporaryBypass(minutes = 10)
        }
    }

    fun suppressSourcePackageAfterContinue() {
        val sourcePackageName = uiState.value.sourcePackageName
        ContinueSuppressionRegistry.suppress(
            packageName = sourcePackageName,
            durationMillis = CONTINUE_SUPPRESSION_MS
        )
    }

    private fun startCountdown(startFrom: Int) {
        viewModelScope.launch {
            var remaining = startFrom
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update { state -> state.copy(remainingSeconds = remaining) }
            }
        }
    }

    private companion object {
        const val CONTINUE_SUPPRESSION_MS = 2 * 60_000L
    }
}

