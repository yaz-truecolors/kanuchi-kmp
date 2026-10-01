package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.WorkCalendar
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * 1人・1か月分の稼働記録のシート (Aggregate Root。Excel の「稼働記録」シートに相当)。
 * 日ごとの行 ([days]) と、月の集計値 (営業日数・稼働時間合計・案件ごとの実績・過不足のある日数等) を持つ。
 *
 * 集計値はすべて記録から都度計算する導出値で、DB には保存しない。[create] で組み立てる。
 * 月の合計 (稼働時間・案件ごとの実績・過不足) には、今日以前の日 ([WorkDay.isCounted]) だけを含める。
 *
 * @property yearMonth 対象の月
 * @property shiftSettings 計算に使った本人の勤務時間設定 (定時・稼働時間の下限/上限)
 * @property days 月の1日から末日までの各日の行 (日付の順。記録が無い日も含む)
 */
class MonthlyWorkSheet private constructor(
    val yearMonth: YearMonth,
    val shiftSettings: ShiftSettings,
    val days: List<WorkDay>,
) {
    private val countedDays: List<WorkDay> = days.filter { it.isCounted }

    /** 稼働日数 (平日かつ祝日・会社の休業日でない日の数。月全体で数える)。 */
    val workingDayCount: Int = days.count { it.dayKind.isWorkingDay }

    /**
     * 営業日数 (Excel の営業日数に相当)。稼働日のうち「休」(個人の休暇) でない日の数。月全体で数える
     * (今日より後の日も含む)。「欠」(欠勤) の日は含める。休みの日に付けた「休」は数に影響しない。
     */
    val businessDayCount: Int = days.count { it.dayKind.isWorkingDay && it.flag != DayFlag.VACATION }

    /** 月の稼働時間合計 (今日以前の日の稼働時間の合計)。 */
    val totalWorkingHours: Hours = countedDays.fold(Hours.ZERO) { sum, day -> sum + day.workingHours }

    /** 案件への配分の合計 (今日以前の日)。 */
    val totalAllocatedHours: Hours = countedDays.fold(Hours.ZERO) { sum, day -> sum + day.allocatedHours }

    /**
     * 未配分の時間 (月の稼働時間合計 − 配分の合計)。負なら配分しすぎ。
     * 日ごとの過不足 ([WorkDay.balance]) の今日以前の合計と等しい。
     */
    val unallocatedHours: SignedHours = AllocationBalancer.balanceOf(totalWorkingHours, totalAllocatedHours)

    /** 未配分の時間の割合 (未配分の時間 ÷ 月の稼働時間合計)。月の稼働時間合計が 0 の場合は null。 */
    val unallocatedRatio: AllocationRatio? = AllocationRatio.of(unallocatedHours, totalWorkingHours)

    /**
     * 案件ごとの実績時間と割合 (今日以前の日の配分の合計。割合の分母は月の稼働時間合計)。
     * 実績時間の多い順 (同じなら案件IDの順) に並ぶ。実績時間が 0 の案件は含めない。
     */
    val projectWorkHours: List<ProjectWorkHours> =
        countedDays
            .flatMap {
                it.record
                    ?.allocations
                    ?.entries
                    .orEmpty()
            }.groupBy({ it.key }, { it.value })
            .mapValues { (_, hours) -> hours.fold(Hours.ZERO, Hours::plus) }
            .filterValues { it > Hours.ZERO }
            .map { (projectId, hours) -> ProjectWorkHours(projectId, hours, AllocationRatio.of(hours, totalWorkingHours)) }
            .sortedWith(compareByDescending<ProjectWorkHours> { it.hours }.thenBy { it.projectId })

    /** 月の稼働時間合計と、勤務時間設定の下限・上限との比較。 */
    val rangeStatus: WorkingHoursRangeStatus =
        when {
            totalWorkingHours < shiftSettings.minHours -> WorkingHoursRangeStatus.BELOW_MIN
            totalWorkingHours > shiftSettings.maxHours -> WorkingHoursRangeStatus.ABOVE_MAX
            else -> WorkingHoursRangeStatus.WITHIN_RANGE
        }

    /** 過不足がある日 ([WorkDay.hasImbalance]) の数。 */
    val imbalanceDayCount: Int = days.count { it.hasImbalance }

    /** 稼働時間の計算結果が負だった (入力ミスの) 日の数 (今日以前の日)。 */
    val negativeWorkingHoursDayCount: Int = countedDays.count { it.isWorkingHoursNegative }

    /** [date] の行。対象の月の日付でなければ null。 */
    fun dayOf(date: LocalDate): WorkDay? = days.getOrNull(date.day - 1)?.takeIf { it.date == date }

    companion object {
        /**
         * 記録から月次シートを組み立てる。
         *
         * @param yearMonth 対象の月
         * @param records 対象の月の稼働記録 (対象の月以外の日付の記録は無視する。1日に複数ある場合は後のものを使う)
         * @param shiftSettings 本人の勤務時間設定 (未保存なら [ShiftSettings.DEFAULT])
         * @param workCalendar 稼働日の判定 (会社の休業日を含む)
         * @param today 日本時間の今日。これより後の日は月の合計に含めない
         */
        fun create(
            yearMonth: YearMonth,
            records: List<WorkRecord>,
            shiftSettings: ShiftSettings,
            workCalendar: WorkCalendar,
            today: LocalDate,
        ): MonthlyWorkSheet {
            val recordsByDate = records.associateBy { it.date }
            val days =
                (1..yearMonth.numberOfDays).map { day ->
                    val date = LocalDate(yearMonth.year, yearMonth.month, day)
                    val dayKind = workCalendar.dayKindOf(date)
                    val record = recordsByDate[date]
                    val working = WorkingHoursCalculator.calculate(dayKind, record, shiftSettings)
                    val allocated = record?.allocatedHours ?: Hours.ZERO
                    WorkDay(
                        date = date,
                        dayKind = dayKind,
                        record = record,
                        workingHours = working.hours,
                        isWorkingHoursNegative = working.isNegative,
                        allocatedHours = allocated,
                        balance = AllocationBalancer.balanceOf(working.hours, allocated),
                        isCounted = date <= today,
                    )
                }
            return MonthlyWorkSheet(yearMonth, shiftSettings, days)
        }
    }
}
