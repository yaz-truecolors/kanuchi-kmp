package jp.co.yaz.kanuchi.presentation.dailyinput

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.work.GetCurrentUserMonthlyWorkSheetUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

/**
 * 日次入力 (月の一覧) 画面の ViewModel。ログイン中のユーザー自身の、選んだ月の日ごとの記録を表示する。
 * 初期表示は日本時間の今月。日の入力画面から戻ったときは ([onScreenShown]) 読み込み直して、保存した内容を反映する。
 */
class DailyInputViewModel(
    getTodayUseCase: GetTodayUseCase,
    private val getCurrentUserMonthlyWorkSheetUseCase: GetCurrentUserMonthlyWorkSheetUseCase,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            getTodayUseCase().let { today -> DailyInputUiState(today = today, yearMonth = YearMonth(today.year, today.month)) },
        )
    val uiState: StateFlow<DailyInputUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var isShownBefore = false

    init {
        load()
    }

    /** 画面が表示されるたびに呼ぶ。2回目以降 (日の入力画面から戻ったとき) は読み込み直す。 */
    fun onScreenShown() {
        if (isShownBefore) load() else isShownBefore = true
    }

    fun onRetryClicked() {
        load()
    }

    fun onYearMonthChanged(yearMonth: YearMonth) {
        if (yearMonth == _uiState.value.yearMonth) return
        _uiState.update { it.copy(yearMonth = yearMonth) }
        load()
    }

    private fun load() {
        // 月を切り替えた場合は、前の月の読み込みの結果を使わない
        loadJob?.cancel()
        val yearMonth = _uiState.value.yearMonth
        // 同じ月を読み込み直す場合 (戻ってきたとき) は、読み込み中の表示を出さずに前の表示を続ける
        val isReload = _uiState.value.sheet?.yearMonth == yearMonth
        if (!isReload) _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        loadJob =
            viewModelScope.launch {
                getCurrentUserMonthlyWorkSheetUseCase(yearMonth)
                    .onSuccess { sheet -> _uiState.update { it.copy(isLoading = false, loadFailed = false, sheet = sheet) } }
                    .onFailure {
                        // 読み込み直しに失敗した場合は、前に読み込んだ表示を続ける
                        if (!isReload) _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    }
            }
    }
}
