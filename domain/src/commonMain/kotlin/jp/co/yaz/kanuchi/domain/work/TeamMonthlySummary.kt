package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.WorkCalendar
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * チーム (参照できるすべてのユーザー) の1か月分の集計。管理者ダッシュボードに表示する。
 * メンバーごとの月次シート ([MonthlyWorkSheet]) を組み立てて集めたもので、[create] で組み立てる。
 *
 * @property yearMonth 対象の月
 * @property members メンバーごとの集計 (並び順は [create] を参照)
 * @property projectHours 案件ごとのチーム合計時間 (並び順は [create] を参照)
 */
class TeamMonthlySummary private constructor(
    val yearMonth: YearMonth,
    val members: List<MemberMonthlySummary>,
    val projectHours: List<TeamProjectHours>,
) {
    companion object {
        /**
         * メンバーごとの記録・勤務時間設定から、チームの集計を組み立てる。
         *
         * - メンバー: [profiles] の順 (利用中のユーザーを先に、利用停止中のユーザーを後に並べる)。利用停止中のユーザーは、
         *   その月に記録がある場合だけ含める (記録が無いと、何も入力していない稼働日を定時どおり働いたとみなす計算になり、
         *   働いていない期間の稼働時間が表示されてしまうため)。
         * - 案件ごとの合計: 含めたメンバーの案件ごとの実績時間 ([MonthlyWorkSheet.projectWorkHours]。今日以前の日の分) の合計。
         *   時間の多い順 (同じなら案件名の順) に並ぶ。無効な案件も実績があれば含める。
         *
         * @param yearMonth 対象の月
         * @param profiles 対象のユーザー (表示名の順)
         * @param recordsByUserId ユーザーIDごとの対象の月の稼働記録 (記録が無いユーザーは含まれなくてよい)
         * @param shiftSettingsByUserId ユーザーIDごとの勤務時間設定 (未保存のユーザーは [ShiftSettings.DEFAULT] を使う)
         * @param workCalendar 稼働日の判定 (対象の月の会社の休業日を含む)
         * @param today 日本時間の今日。これより後の日は合計に含めない
         * @param projects すべての案件 (案件名の表示に使う)
         */
        @Suppress("LongParameterList") // 集計に必要な入力をすべて受け取る組み立て用の関数のため
        fun create(
            yearMonth: YearMonth,
            profiles: List<UserProfile>,
            recordsByUserId: Map<String, List<WorkRecord>>,
            shiftSettingsByUserId: Map<String, ShiftSettings>,
            workCalendar: WorkCalendar,
            today: LocalDate,
            projects: List<Project>,
        ): TeamMonthlySummary {
            val members =
                profiles
                    .filter { !it.isSuspended || recordsByUserId[it.id].orEmpty().isNotEmpty() }
                    .sortedBy { it.isSuspended }
                    .map { profile ->
                        MemberMonthlySummary(
                            profile = profile,
                            sheet =
                                MonthlyWorkSheet.create(
                                    yearMonth = yearMonth,
                                    records = recordsByUserId[profile.id].orEmpty(),
                                    shiftSettings = shiftSettingsByUserId[profile.id] ?: ShiftSettings.DEFAULT,
                                    workCalendar = workCalendar,
                                    today = today,
                                ),
                        )
                    }
            val projectsById = projects.associateBy { it.id }
            val projectHours =
                members
                    .flatMap { it.sheet.projectWorkHours }
                    .groupBy({ it.projectId }, { it.hours })
                    .map { (projectId, hours) ->
                        TeamProjectHours(projectId, projectsById[projectId], hours.fold(Hours.ZERO, Hours::plus))
                    }.sortedWith(
                        compareByDescending<TeamProjectHours> { it.hours }
                            .thenBy(nullsLast()) { it.project?.name }
                            .thenBy { it.projectId },
                    )
            return TeamMonthlySummary(yearMonth, members, projectHours)
        }
    }
}

/**
 * メンバー1人の月の集計。
 *
 * @property profile メンバーのプロフィール (表示名・メールアドレス・利用停止中か)
 * @property sheet メンバーの月次シート (稼働時間合計・下限/上限との比較・営業日数・過不足のある日数等)
 */
data class MemberMonthlySummary(
    val profile: UserProfile,
    val sheet: MonthlyWorkSheet,
)

/**
 * 案件1件の月のチーム合計時間。
 *
 * @property projectId 案件ID
 * @property project 案件 (名前・有効か)。案件の一覧に見つからなかった場合は null
 * @property hours メンバー全員の実績時間の合計
 */
data class TeamProjectHours(
    val projectId: String,
    val project: Project?,
    val hours: Hours,
)
