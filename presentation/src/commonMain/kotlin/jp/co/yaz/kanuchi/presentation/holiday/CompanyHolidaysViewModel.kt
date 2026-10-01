package jp.co.yaz.kanuchi.presentation.holiday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.calendar.AddCompanyHolidaysUseCase
import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayInput
import jp.co.yaz.kanuchi.domain.calendar.DeleteCompanyHolidayUseCase
import jp.co.yaz.kanuchi.domain.calendar.DuplicateCompanyHolidayException
import jp.co.yaz.kanuchi.domain.calendar.GetCompanyHolidaysOfYearUseCase
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.calendar.InvalidCompanyHolidayInputException
import jp.co.yaz.kanuchi.domain.calendar.JapaneseHolidays
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 休業日管理画面 (admin のみ) の ViewModel。年ごとの会社の休業日の一覧・追加・削除と、
 * 参考としてその年の日本の祝日 (自動判定) の一覧を扱う。初期表示は今年 (日本時間)。
 */
class CompanyHolidaysViewModel(
    getTodayUseCase: GetTodayUseCase,
    private val getCompanyHolidaysOfYearUseCase: GetCompanyHolidaysOfYearUseCase,
    private val addCompanyHolidaysUseCase: AddCompanyHolidaysUseCase,
    private val deleteCompanyHolidayUseCase: DeleteCompanyHolidayUseCase,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            CompanyHolidaysUiState(
                year = getTodayUseCase().year.coerceIn(JapaneseHolidays.SUPPORTED_YEARS),
            ),
        )
    val uiState: StateFlow<CompanyHolidaysUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(_uiState.value.year)
    }

    fun onRetryClicked() {
        load(_uiState.value.year)
    }

    fun onPreviousYearClicked() {
        if (!_uiState.value.canGoToPreviousYear) return
        load(_uiState.value.year - 1)
    }

    fun onNextYearClicked() {
        if (!_uiState.value.canGoToNextYear) return
        load(_uiState.value.year + 1)
    }

    /** [year] 年の一覧を読み込む。読み込み中に別の年に切り替えた場合は、前の読み込みを取り消す。 */
    private fun load(year: Int) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                year = year,
                companyHolidays = if (year == it.year) it.companyHolidays else emptyList(),
                nationalHolidays = JapaneseHolidays.holidaysOf(year).map { (date, name) -> NationalHolidayItem(date, name) },
                isLoading = true,
                loadFailed = false,
                deleteFailedHoliday = if (year == it.year) it.deleteFailedHoliday else null,
            )
        }

        loadJob =
            viewModelScope.launch {
                getCompanyHolidaysOfYearUseCase(year)
                    .onSuccess { holidays ->
                        _uiState.update { it.copy(companyHolidays = holidays, isLoading = false) }
                    }.onFailure {
                        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    }
            }
    }

    fun onInputChanged(input: CompanyHolidayInput) {
        _uiState.update { it.copy(input = input, inputViolations = emptySet(), addError = null, addedDayCount = null) }
    }

    fun onAddClicked() {
        val state = _uiState.value
        if (!state.canEdit) return
        _uiState.update { it.copy(isAdding = true, inputViolations = emptySet(), addError = null, addedDayCount = null) }

        viewModelScope.launch {
            addCompanyHolidaysUseCase(state.input)
                .onSuccess { added ->
                    _uiState.update {
                        it.copy(
                            companyHolidays = it.companyHolidays.merged(added.filter { holiday -> holiday.date.year == it.year }),
                            input = CompanyHolidaysUiState.EMPTY_INPUT,
                            isAdding = false,
                            addedDayCount = added.size,
                        )
                    }
                }.onFailure { error ->
                    if (error is InvalidCompanyHolidayInputException) {
                        _uiState.update { it.copy(isAdding = false, inputViolations = error.violations) }
                    } else {
                        val addError =
                            if (error is DuplicateCompanyHolidayException) {
                                CompanyHolidayAddError.DUPLICATE
                            } else {
                                CompanyHolidayAddError.SAVE_FAILED
                            }
                        _uiState.update { it.copy(isAdding = false, addError = addError) }
                        // 期間の途中で失敗した場合や、他の admin が先に追加していた場合に一覧を最新にする
                        load(_uiState.value.year)
                    }
                }
        }
    }

    fun onDeleteClicked(holiday: CompanyHoliday) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(deleteTarget = holiday, deleteFailedHoliday = null) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun onDeleteConfirmed() {
        val state = _uiState.value
        val target = state.deleteTarget ?: return
        if (!state.canEdit) return
        _uiState.update { it.copy(deleteTarget = null, deletingDate = target.date, deleteFailedHoliday = null) }

        viewModelScope.launch {
            deleteCompanyHolidayUseCase(target.date)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            companyHolidays =
                                it.companyHolidays.filterNot { holiday ->
                                    holiday.date == target.date
                                },
                            deletingDate = null,
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(deletingDate = null, deleteFailedHoliday = target) }
                    // 他の admin が先に削除していた (0件削除) 場合などに、存在しない行を残さないよう一覧を最新にする
                    load(_uiState.value.year)
                }
        }
    }
}

/** [added] を加え、日付の順に並べる (同じ日付は [added] を優先する)。 */
private fun List<CompanyHoliday>.merged(added: List<CompanyHoliday>): List<CompanyHoliday> {
    val addedDates = added.map { it.date }.toSet()
    return (filterNot { it.date in addedDates } + added).sortedBy { it.date }
}
