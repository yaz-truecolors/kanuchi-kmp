package jp.co.yaz.kanuchi.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.InvalidDisplayNameException
import jp.co.yaz.kanuchi.domain.profile.UpdateDisplayNameUseCase
import jp.co.yaz.kanuchi.domain.shift.GetShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.shift.InvalidShiftSettingsException
import jp.co.yaz.kanuchi.domain.shift.SaveShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 個人設定画面 (ログイン中のユーザー自身の表示名・勤務時間設定の変更) の ViewModel。
 */
class SettingsViewModel(
    private val getCurrentUserProfileUseCase: GetCurrentUserProfileUseCase,
    private val updateDisplayNameUseCase: UpdateDisplayNameUseCase,
    private val getShiftSettingsUseCase: GetShiftSettingsUseCase,
    private val saveShiftSettingsUseCase: SaveShiftSettingsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetryClicked() {
        load()
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        loadJob =
            viewModelScope.launch {
                val profileResult = async { getCurrentUserProfileUseCase() }
                val shiftSettingsResult = async { getShiftSettingsUseCase() }
                val profile = profileResult.await().getOrNull()
                val shiftSettings = shiftSettingsResult.await().getOrNull()
                _uiState.update {
                    if (profile == null || shiftSettings == null) {
                        it.copy(isLoading = false, loadFailed = true)
                    } else {
                        it.copy(
                            isLoading = false,
                            displayName = profile.displayName,
                            shiftSettings = ShiftSettingsInput.from(shiftSettings),
                        )
                    }
                }
            }
    }

    fun onDisplayNameChanged(displayName: String) {
        _uiState.update {
            it.copy(
                displayName = displayName,
                displayNameViolation = null,
                displayNameSaveFailed = false,
                displayNameSaved = false,
            )
        }
    }

    fun onSaveDisplayNameClicked() {
        val currentState = _uiState.value
        if (currentState.isSavingDisplayName) return
        _uiState.update {
            it.copy(isSavingDisplayName = true, displayNameViolation = null, displayNameSaveFailed = false, displayNameSaved = false)
        }

        viewModelScope.launch {
            updateDisplayNameUseCase(currentState.displayName)
                .onSuccess { profile ->
                    _uiState.update { it.copy(displayName = profile.displayName, isSavingDisplayName = false, displayNameSaved = true) }
                }.onFailure { error ->
                    _uiState.update {
                        if (error is InvalidDisplayNameException) {
                            it.copy(isSavingDisplayName = false, displayNameViolation = error.violation)
                        } else {
                            it.copy(isSavingDisplayName = false, displayNameSaveFailed = true)
                        }
                    }
                }
        }
    }

    fun onShiftSettingsChanged(shiftSettings: ShiftSettingsInput) {
        _uiState.update {
            it.copy(
                shiftSettings = shiftSettings,
                shiftSettingsViolations = emptySet(),
                shiftSettingsSaveFailed = false,
                shiftSettingsSaved = false,
            )
        }
    }

    fun onSaveShiftSettingsClicked() {
        val currentState = _uiState.value
        if (currentState.isSavingShiftSettings) return
        _uiState.update {
            it.copy(
                isSavingShiftSettings = true,
                shiftSettingsViolations = emptySet(),
                shiftSettingsSaveFailed = false,
                shiftSettingsSaved = false,
            )
        }

        viewModelScope.launch {
            saveShiftSettingsUseCase(currentState.shiftSettings)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            shiftSettings = ShiftSettingsInput.from(saved),
                            isSavingShiftSettings = false,
                            shiftSettingsSaved = true,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        if (error is InvalidShiftSettingsException) {
                            it.copy(isSavingShiftSettings = false, shiftSettingsViolations = error.violations)
                        } else {
                            it.copy(isSavingShiftSettings = false, shiftSettingsSaveFailed = true)
                        }
                    }
                }
        }
    }
}
