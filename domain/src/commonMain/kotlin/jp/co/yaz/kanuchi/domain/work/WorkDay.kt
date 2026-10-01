package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import kotlinx.datetime.LocalDate

/**
 * 月次シート ([MonthlyWorkSheet]) の1日分の行。記録と、そこから計算した導出値 (稼働時間・配分合計・過不足) を持つ。
 *
 * @property date 日付
 * @property dayKind 日の種類 (平日 / 土日 / 日本の祝日 / 会社の休業日)
 * @property record その日の稼働記録。何も入力していない日は null
 * @property workingHours 稼働時間 ([WorkingHoursCalculator])。計算結果が負の場合は 0
 * @property isWorkingHoursNegative 稼働時間の計算結果が負だった (入力ミス) か。日次入力で警告するのに使う
 * @property allocatedHours 案件への配分の合計
 * @property balance 過不足 (稼働時間 − 配分の合計。[AllocationBalancer])
 * @property isCounted 月の合計に含める日 (今日以前の日) か。今日より後の日は実績が無いため合計に含めない
 */
data class WorkDay(
    val date: LocalDate,
    val dayKind: DayKind,
    val record: WorkRecord?,
    val workingHours: Hours,
    val isWorkingHoursNegative: Boolean,
    val allocatedHours: Hours,
    val balance: SignedHours,
    val isCounted: Boolean,
) {
    /** 日ごとの印。 */
    val flag: DayFlag? get() = record?.flag

    /** 過不足がある (合計に含める日で、過不足が 0 でない) か。 */
    val hasImbalance: Boolean get() = isCounted && !balance.isZero
}
