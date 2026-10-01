package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings

/**
 * 1日の稼働時間の計算 (Domain Service。Excel の稼働時間の列 (G 列) の数式に相当)。
 *
 * - 印 (「休」「欠」) の付いた日は 0。
 * - 稼働日 ([DayKind.isWorkingDay]): `(退勤 ?: 定時終業) − (出勤 ?: 定時始業) − (休憩 ?: 定時休憩)`。
 *   何も入力していない (記録が無い) 日も、定時どおり働いたとみなす。
 * - 休み (土日・祝日・会社の休業日): 出勤か退勤が入力されている場合だけ稼働として上の式で計算する
 *   (片方だけなら他方は定時)。どちらも入力されていなければ 0。
 * - 計算結果が負になる場合 (入力ミス) は 0 とし、[DailyWorkingHours.isNegative] で分かるようにする。
 *
 * 時刻の差 (分) は 0.01 時間単位に四捨五入してから休憩時間を引く (例: 7 分 = 0.1166… 時間 → 0.12 時間)。
 */
object WorkingHoursCalculator {
    /**
     * @param dayKind 日の種類
     * @param record その日の稼働記録 (無ければ null)
     * @param shiftSettings 本人の勤務時間設定 (定時)
     */
    fun calculate(
        dayKind: DayKind,
        record: WorkRecord?,
        shiftSettings: ShiftSettings,
    ): DailyWorkingHours {
        val hasClockInput = record?.clockIn != null || record?.clockOut != null
        val isWorked = record?.flag == null && (dayKind.isWorkingDay || hasClockInput)
        if (!isWorked) return DailyWorkingHours(Hours.ZERO, isNegative = false)

        val clockIn = record?.clockIn ?: shiftSettings.startTime
        val clockOut = record?.clockOut ?: shiftSettings.endTime
        val breakHours = record?.breakHours ?: shiftSettings.breakHours
        val hundredths = minutesToHundredths(clockOut.minuteOfDay - clockIn.minuteOfDay) - breakHours.hundredths
        return if (hundredths < 0) {
            DailyWorkingHours(Hours.ZERO, isNegative = true)
        } else {
            DailyWorkingHours(Hours.ofHundredths(hundredths), isNegative = false)
        }
    }

    /** 分を 0.01 時間単位に丸める (端数がちょうど 0.5 の場合は大きい方へ。正の値では四捨五入と同じ)。 */
    private fun minutesToHundredths(minutes: Int): Int =
        (minutes * HUNDREDTHS_PER_MINUTE_NUMERATOR + ROUNDING).floorDiv(MINUTES_DENOMINATOR)

    // 1分 = 100/60 = 10/6 (0.01 時間単位)。四捨五入のため分子に 6/2 を足してから 6 で割る
    private const val HUNDREDTHS_PER_MINUTE_NUMERATOR = 10
    private const val MINUTES_DENOMINATOR = 6
    private const val ROUNDING = 3
}

/**
 * 1日の稼働時間の計算結果。
 *
 * @property hours 稼働時間 (0 以上)
 * @property isNegative 計算結果が負だった (退勤が出勤より前、休憩が長すぎる等の入力ミス) か。その場合 [hours] は 0
 */
data class DailyWorkingHours(
    val hours: Hours,
    val isNegative: Boolean,
)
