package jp.co.yaz.kanuchi.presentation.dailyinput

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.MonthlyWorkSheet
import jp.co.yaz.kanuchi.domain.work.WorkDay
import jp.co.yaz.kanuchi.presentation.common.formatHours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * 日次入力 (月の一覧) 画面のUI状態。
 *
 * @property today 日本時間の今日 (今日の行の強調と、合計に含める範囲の説明に使う)
 * @property yearMonth 表示中の月
 * @property sheet 表示中の月の月次シート。読み込み前・読み込みに失敗した場合は null
 */
data class DailyInputUiState(
    val today: LocalDate,
    val yearMonth: YearMonth,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val sheet: MonthlyWorkSheet? = null,
) {
    /** 日ごとの行 (表示中の月の月次シートを読み込めた場合のみ)。 */
    val rows: List<DailyInputRow>
        get() = sheet?.takeIf { it.yearMonth == yearMonth }?.let { sheet -> sheet.days.map { DailyInputRow.of(it, sheet, today) } }.orEmpty()

    /** 表示中の月に、今日より後の (合計に含めない) 日があるか。 */
    val hasFutureDays: Boolean get() = yearMonth.lastDay > today
}

/**
 * 一覧の1日分の行。
 *
 * @property clockIn 出勤時刻の表示。稼働しない日 (休み・印のある日) は null
 * @property clockOut 退勤時刻の表示。稼働しない日は null
 * @property breakHours 休憩時間の表示。稼働しない日は null
 * @property balance 過不足。今日より後の日で、まだ何も入力していない日は null (表示しない)
 */
data class DailyInputRow(
    val date: LocalDate,
    val dayKind: DayKind,
    val flag: DayFlag?,
    val clockIn: ShownValue?,
    val clockOut: ShownValue?,
    val breakHours: ShownValue?,
    val workingHours: Hours,
    val isWorkingHoursNegative: Boolean,
    val balance: SignedHours?,
    val hasNote: Boolean,
    val isToday: Boolean,
    val isCounted: Boolean,
) {
    /** 過不足を目立たせるか (合計に含める日で、過不足が 0 でない)。 */
    val hasImbalance: Boolean get() = isCounted && balance != null && !balance.isZero

    companion object {
        fun of(
            day: WorkDay,
            sheet: MonthlyWorkSheet,
            today: LocalDate,
        ): DailyInputRow {
            val record = day.record
            val shift = sheet.shiftSettings
            val hasClockInput = record?.clockIn != null || record?.clockOut != null
            // 稼働する日 (印が無く、稼働日か、休みの日に出勤・退勤を入力した日) だけ時刻を表示する。空欄は定時を薄く表示する
            val isWorked = day.flag == null && (day.dayKind.isWorkingDay || hasClockInput)
            return DailyInputRow(
                date = day.date,
                dayKind = day.dayKind,
                flag = day.flag,
                clockIn = if (isWorked) ShownValue.of(record?.clockIn?.toString(), shift.startTime.toString()) else null,
                clockOut = if (isWorked) ShownValue.of(record?.clockOut?.toString(), shift.endTime.toString()) else null,
                breakHours = if (isWorked) ShownValue.of(record?.breakHours?.let(::formatHours), formatHours(shift.breakHours)) else null,
                workingHours = day.workingHours,
                isWorkingHoursNegative = day.isWorkingHoursNegative,
                balance = if (day.isCounted || record != null) day.balance else null,
                hasNote = !record?.note.isNullOrBlank(),
                isToday = day.date == today,
                isCounted = day.isCounted,
            )
        }
    }
}

/**
 * 一覧に表示する値。
 *
 * @property isDefault 入力が空欄のため、定時 (勤務時間設定) の値を表示しているか
 */
data class ShownValue(
    val text: String,
    val isDefault: Boolean,
) {
    companion object {
        fun of(
            entered: String?,
            default: String,
        ): ShownValue = if (entered != null) ShownValue(entered, isDefault = false) else ShownValue(default, isDefault = true)
    }
}
