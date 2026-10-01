package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import kotlinx.datetime.LocalDate

/**
 * 1日分の稼働記録 (Entity)。DB の `work_records` テーブルの1行と、それに紐づく `allocations` (案件ごとの配分) に対応する。
 * 1ユーザー・1日につき1件。記録が無い日は「何も入力していない日」として扱う ([WorkingHoursCalculator])。
 *
 * 稼働時間・過不足などの導出値は持たず、[MonthlyWorkSheet] が都度計算する。
 *
 * @property date 日付
 * @property clockIn 出勤時刻。null なら定時の始業時刻とみなす
 * @property clockOut 退勤時刻。null なら定時の終業時刻とみなす
 * @property breakHours 休憩時間。null なら定時の休憩時間とみなす
 * @property flag 日ごとの印 (「休」「欠」)。null なら印なし
 * @property note 備考
 * @property allocations 案件ごとの配分 (案件ID → 時間)
 */
data class WorkRecord(
    val date: LocalDate,
    val clockIn: TimeOfDay? = null,
    val clockOut: TimeOfDay? = null,
    val breakHours: Hours? = null,
    val flag: DayFlag? = null,
    val note: String? = null,
    val allocations: Map<String, Hours> = emptyMap(),
) {
    /** 案件への配分の合計。 */
    val allocatedHours: Hours get() = allocations.values.fold(Hours.ZERO, Hours::plus)
}
