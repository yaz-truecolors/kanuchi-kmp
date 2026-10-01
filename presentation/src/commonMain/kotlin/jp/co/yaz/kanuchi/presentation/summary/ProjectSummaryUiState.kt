package jp.co.yaz.kanuchi.presentation.summary

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.work.MonthlyWorkSheet
import jp.co.yaz.kanuchi.domain.work.ProjectWorkHours
import kotlinx.datetime.YearMonth

/**
 * 案件別集計画面のUI状態。
 */
data class ProjectSummaryUiState(
    /** 表示している月。 */
    val yearMonth: YearMonth,
    /**
     * 切り替えられる最初の月 (2000年1月)。祝日の判定 ([jp.co.yaz.kanuchi.domain.calendar.JapaneseHolidays]) が
     * 正確なのは 2000 年以降なので、それより前の月は営業日数が正しく出ないため切り替えさせない。
     */
    val minYearMonth: YearMonth,
    /** 切り替えられる最後の月 (日本時間の今月)。来月以降は実績が無く、合計が 0 で下限未満と表示されるだけなので切り替えさせない。 */
    val maxYearMonth: YearMonth,
    /**
     * 誰の集計かを画面上部に表示するか。他の画面 (管理者ダッシュボード) からユーザーを指定して開いた場合に表示する
     * (自分の分をホーム画面から開いた場合は表示しない)。
     */
    val showsTargetUser: Boolean = false,
    /** 集計の対象のユーザー。読み込み前・読み込み失敗時は null。 */
    val targetUser: UserProfile? = null,
    /** 表示する月の集計。読み込み中・読み込み失敗時は null。 */
    val summary: ProjectSummary? = null,
    val isLoading: Boolean = false,
    val loadError: ProjectSummaryLoadError? = null,
)

/**
 * 表示する月の集計。集計値は [sheet] (domain の [MonthlyWorkSheet]) のものをそのまま表示し、画面側では計算しない。
 *
 * @property sheet 月次シート (営業日数・稼働時間合計・下限/上限との比較・未配分の時間・過不足がある日数)
 * @property projectRows 案件ごとの実績 ([MonthlyWorkSheet.projectWorkHours] と同じ順。実績時間の多い順)
 */
data class ProjectSummary(
    val sheet: MonthlyWorkSheet,
    val projectRows: List<ProjectSummaryRow>,
)

/**
 * 案件ごとの実績の1行。
 *
 * @property workHours 実績時間と割合
 * @property project 案件 (案件名・有効/無効の表示用)。案件の一覧に無い (参照できない) 場合は null
 */
data class ProjectSummaryRow(
    val workHours: ProjectWorkHours,
    val project: Project?,
)

/** 読み込みの失敗の種類。 */
enum class ProjectSummaryLoadError {
    /** データの読み込みに失敗した (再試行できる)。 */
    LOAD_FAILED,

    /**
     * 指定されたユーザーの集計を表示できない (ユーザーが存在しない、または他人の集計を見る権限が無い)。
     * 再試行しても結果は変わらないため、再試行は出さない。
     */
    USER_NOT_VIEWABLE,
}
