package jp.co.yaz.kanuchi.presentation.dailyinput

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.DeleteWorkRecordUseCase
import jp.co.yaz.kanuchi.domain.work.GetWorkDayEntryUseCase
import jp.co.yaz.kanuchi.domain.work.InvalidWorkRecordException
import jp.co.yaz.kanuchi.domain.work.SaveWorkRecordUseCase
import jp.co.yaz.kanuchi.domain.work.WorkRecordInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 日次入力 (1日分の入力) 画面の ViewModel。ログイン中のユーザー自身の [date] の記録を入力・保存・削除する。
 * 保存・削除に成功したら [DailyInputDayUiState.isFinished] を true にする (画面は一覧へ戻る)。
 */
class DailyInputDayViewModel(
    date: LocalDate,
    private val getWorkDayEntryUseCase: GetWorkDayEntryUseCase,
    private val saveWorkRecordUseCase: SaveWorkRecordUseCase,
    private val deleteWorkRecordUseCase: DeleteWorkRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DailyInputDayUiState(date = date))
    val uiState: StateFlow<DailyInputDayUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetryClicked() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            getWorkDayEntryUseCase(_uiState.value.date)
                .onSuccess { entry ->
                    _uiState.update {
                        it.copy(isLoading = false, entry = entry, input = WorkRecordInput.from(entry.day.record))
                    }
                }.onFailure { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    fun onClockInChanged(clockIn: String) = updateInput { it.copy(clockIn = clockIn) }

    fun onClockOutChanged(clockOut: String) = updateInput { it.copy(clockOut = clockOut) }

    fun onBreakHoursChanged(breakHours: String) = updateInput { it.copy(breakHours = breakHours) }

    fun onFlagChanged(flag: DayFlag?) = updateInput { it.copy(flag = flag) }

    fun onNoteChanged(note: String) = updateInput { it.copy(note = note) }

    fun onAllocationChanged(
        projectId: String,
        hours: String,
    ) = updateInput { it.copy(allocations = it.allocations + (projectId to hours)) }

    /** 「定時を入力」: 出勤・退勤・休憩に勤務時間設定 (定時) の値を入れる。 */
    fun onFillShiftClicked() {
        val shift = _uiState.value.entry?.shiftSettings ?: return
        updateInput {
            it.copy(
                clockIn = shift.startTime.toString(),
                clockOut = shift.endTime.toString(),
                breakHours = shift.breakHours.toString(),
            )
        }
    }

    private fun updateInput(transform: (WorkRecordInput) -> WorkRecordInput) {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                input = transform(it.input),
                violations = emptySet(),
                allocationViolations = emptyMap(),
                saveFailed = false,
                deleteFailed = false,
            )
        }
    }

    fun onSaveClicked() {
        val currentState = _uiState.value
        val entry = currentState.entry ?: return
        if (currentState.isBusy) return
        _uiState.update {
            it.copy(isSaving = true, violations = emptySet(), allocationViolations = emptyMap(), saveFailed = false, deleteFailed = false)
        }

        viewModelScope.launch {
            saveWorkRecordUseCase(entry, currentState.input)
                .onSuccess { _uiState.update { it.copy(isSaving = false, isFinished = true) } }
                .onFailure { error ->
                    _uiState.update {
                        if (error is InvalidWorkRecordException) {
                            it.copy(isSaving = false, violations = error.violations, allocationViolations = error.allocationViolations)
                        } else {
                            it.copy(isSaving = false, saveFailed = true)
                        }
                    }
                }
        }
    }

    fun onDeleteClicked() {
        if (_uiState.value.isBusy || !_uiState.value.hasSavedRecord) return
        _uiState.update { it.copy(showsDeleteConfirmation = true) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(showsDeleteConfirmation = false) }
    }

    fun onDeleteConfirmed() {
        val currentState = _uiState.value
        if (currentState.isBusy || !currentState.hasSavedRecord) return
        _uiState.update { it.copy(showsDeleteConfirmation = false, isDeleting = true, saveFailed = false, deleteFailed = false) }

        viewModelScope.launch {
            deleteWorkRecordUseCase(currentState.date)
                .onSuccess { _uiState.update { it.copy(isDeleting = false, isFinished = true) } }
                .onFailure { _uiState.update { it.copy(isDeleting = false, deleteFailed = true) } }
        }
    }
}
