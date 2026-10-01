package jp.co.yaz.kanuchi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.work.GetTeamMonthlySummaryUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

/**
 * 管理者ダッシュボードの ViewModel。初期表示は今月 (日本時間) で、月を切り替えるとその月の集計を読み込み直す。
 */
class AdminDashboardViewModel(
    private val getTeamMonthlySummaryUseCase: GetTeamMonthlySummaryUseCase,
    getTodayUseCase: GetTodayUseCase,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            getTodayUseCase().let { today -> AdminDashboardUiState(yearMonth = YearMonth(today.year, today.month)) },
        )
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** 表示する月が切り替えられたとき。 */
    fun onMonthSelected(yearMonth: YearMonth) {
        if (yearMonth == _uiState.value.yearMonth) return
        _uiState.update { it.copy(yearMonth = yearMonth) }
        load()
    }

    fun onRetryClicked() {
        load()
    }

    private fun load() {
        // 月を続けて切り替えた場合に、前の月の結果が後から届いて上書きしないよう、読み込み中のものは取り消す
        loadJob?.cancel()
        val yearMonth = _uiState.value.yearMonth
        _uiState.update { it.copy(summary = null, isLoading = true, loadFailed = false) }

        loadJob =
            viewModelScope.launch {
                getTeamMonthlySummaryUseCase(yearMonth)
                    .onSuccess { summary ->
                        _uiState.update { it.copy(summary = summary, isLoading = false) }
                    }.onFailure {
                        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    }
            }
    }
}
