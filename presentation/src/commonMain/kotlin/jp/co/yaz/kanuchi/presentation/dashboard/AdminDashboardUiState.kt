package jp.co.yaz.kanuchi.presentation.dashboard

import jp.co.yaz.kanuchi.domain.work.TeamMonthlySummary
import kotlinx.datetime.YearMonth

/**
 * 管理者ダッシュボードのUI状態。
 *
 * @property yearMonth 表示中の月
 * @property summary 表示中の月のチームの集計。読み込み前・読み込み中・読み込み失敗時は null
 */
data class AdminDashboardUiState(
    val yearMonth: YearMonth,
    val summary: TeamMonthlySummary? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)
