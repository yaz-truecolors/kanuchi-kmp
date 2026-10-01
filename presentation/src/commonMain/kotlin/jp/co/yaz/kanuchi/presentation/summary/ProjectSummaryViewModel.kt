package jp.co.yaz.kanuchi.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.calendar.JapaneseHolidays
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.GetProfilesUseCase
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.project.GetProjectsUseCase
import jp.co.yaz.kanuchi.domain.work.GetMonthlyWorkSheetUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/**
 * 案件別集計画面の ViewModel。1人分の月の集計 (営業日数・稼働時間合計・案件ごとの実績等) を表示する。
 * 集計値は domain の月次シート ([GetMonthlyWorkSheetUseCase]) のものを使う。
 *
 * @param userId 集計の対象のユーザーID (画面のルートの引数)。null ならログイン中のユーザー自身。
 *   指定された場合は、参照できるプロフィールの一覧 ([GetProfilesUseCase]。admin は全員分、member は自分の分だけ) から
 *   探し、見つからなければ [ProjectSummaryLoadError.USER_NOT_VIEWABLE] にする (member が他人を指定しても、
 *   記録が空のシートを本人の集計として表示しないため)。
 * @param yearMonthArg 初めに表示する月 (画面のルートの引数。`yyyy-MM` 形式)。null または形式が正しくなければ日本時間の今月。
 *   切り替えられる範囲 (2000年1月〜今月) の外の月は、範囲の端の月にする
 */
class ProjectSummaryViewModel(
    private val userId: String?,
    yearMonthArg: String?,
    private val getCurrentUserProfileUseCase: GetCurrentUserProfileUseCase,
    private val getProfilesUseCase: GetProfilesUseCase,
    private val getMonthlyWorkSheetUseCase: GetMonthlyWorkSheetUseCase,
    private val getProjectsUseCase: GetProjectsUseCase,
    getTodayUseCase: GetTodayUseCase,
) : ViewModel() {
    private val minYearMonth = YearMonth(JapaneseHolidays.SUPPORTED_YEARS.first, 1)
    private val maxYearMonth = getTodayUseCase().yearMonth

    private val _uiState =
        MutableStateFlow(
            ProjectSummaryUiState(
                yearMonth = yearMonthArg?.let(::parseYearMonthOrNull)?.coerceIn(minYearMonth, maxYearMonth) ?: maxYearMonth,
                minYearMonth = minYearMonth,
                maxYearMonth = maxYearMonth,
                showsTargetUser = userId != null,
            ),
        )
    val uiState: StateFlow<ProjectSummaryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** 表示する月を切り替える。読み込み中に切り替えた場合は、前の月の読み込みを取り消す。 */
    fun onMonthChanged(yearMonth: YearMonth) {
        val month = yearMonth.coerceIn(minYearMonth, maxYearMonth)
        if (month == _uiState.value.yearMonth) return
        _uiState.update { it.copy(yearMonth = month) }
        load()
    }

    fun onRetryClicked() {
        if (_uiState.value.isLoading) return
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val yearMonth = _uiState.value.yearMonth
        _uiState.update { it.copy(isLoading = true, loadError = null, summary = null) }

        loadJob =
            viewModelScope.launch {
                val targetUser =
                    resolveTargetUser().getOrElse {
                        ensureActive()
                        _uiState.update { it.copy(isLoading = false, loadError = ProjectSummaryLoadError.LOAD_FAILED) }
                        return@launch
                    }
                ensureActive()
                if (targetUser == null) {
                    _uiState.update { it.copy(isLoading = false, loadError = ProjectSummaryLoadError.USER_NOT_VIEWABLE) }
                    return@launch
                }
                _uiState.update { it.copy(targetUser = targetUser) }

                val summary = loadSummary(targetUser.id, yearMonth)
                ensureActive()
                summary
                    .onSuccess { loaded -> _uiState.update { it.copy(summary = loaded, isLoading = false) } }
                    .onFailure { _uiState.update { it.copy(isLoading = false, loadError = ProjectSummaryLoadError.LOAD_FAILED) } }
            }
    }

    /**
     * 集計の対象のユーザー。一度取得できたら月を切り替えても取得し直さない。
     * 指定されたユーザーが参照できるプロフィールの一覧に無い場合は null。
     */
    private suspend fun resolveTargetUser(): Result<UserProfile?> {
        val resolved = _uiState.value.targetUser
        return when {
            resolved != null -> Result.success(resolved)
            userId == null -> getCurrentUserProfileUseCase()
            else -> getProfilesUseCase().map { profiles -> profiles.firstOrNull { it.id == userId } }
        }
    }

    private suspend fun loadSummary(
        userId: String,
        yearMonth: YearMonth,
    ): Result<ProjectSummary> =
        coroutineScope {
            val sheet = async { getMonthlyWorkSheetUseCase(userId, yearMonth) }
            // 無効な案件・割当を外した案件にも実績が残るため、すべての案件から名前を引く
            val projects = async { getProjectsUseCase() }
            val loadedSheet = sheet.await().getOrElse { return@coroutineScope Result.failure(it) }
            val projectsById = projects.await().getOrElse { return@coroutineScope Result.failure(it) }.associateBy { it.id }
            Result.success(
                ProjectSummary(
                    sheet = loadedSheet,
                    projectRows = loadedSheet.projectWorkHours.map { ProjectSummaryRow(it, projectsById[it.projectId]) },
                ),
            )
        }
}

private fun parseYearMonthOrNull(text: String): YearMonth? =
    try {
        YearMonth.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }
