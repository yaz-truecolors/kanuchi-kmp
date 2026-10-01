package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.calendar.WorkCalendar
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2026年10月で確認する。10/1 は木曜日、祝日は 10/12 (スポーツの日・月曜日)、土日は 9 日あり、稼働日は 21 日。
 * 定時は [ShiftSettings.DEFAULT] (9:30〜18:30、休憩 1 時間で 8 時間、下限 140・上限 180 時間)。
 */
class MonthlyWorkSheetTest {
    private val october = YearMonth(2026, 10)
    private val endOfOctober = LocalDate(2026, 10, 31)

    private fun sheet(
        records: List<WorkRecord> = emptyList(),
        today: LocalDate = endOfOctober,
        yearMonth: YearMonth = october,
        shiftSettings: ShiftSettings = ShiftSettings.DEFAULT,
        companyHolidays: List<CompanyHoliday> = emptyList(),
    ) = MonthlyWorkSheet.create(yearMonth, records, shiftSettings, WorkCalendar(companyHolidays), today)

    private fun h(hundredths: Int) = Hours.ofHundredths(hundredths)

    private fun october(day: Int) = LocalDate(2026, 10, day)

    @Test
    fun `month without records counts every working day as the regular shift`() {
        val sheet = sheet()
        assertEquals(31, sheet.days.size)
        assertEquals(october(1), sheet.days.first().date)
        assertEquals(endOfOctober, sheet.days.last().date)
        assertEquals(21, sheet.workingDayCount)
        assertEquals(21, sheet.businessDayCount)
        assertEquals(h(16_800), sheet.totalWorkingHours)
        assertEquals(Hours.ZERO, sheet.totalAllocatedHours)
        assertEquals(SignedHours.ofHundredths(16_800), sheet.unallocatedHours)
        assertEquals(AllocationRatio.ofTenthsOfPercent(1_000), sheet.unallocatedRatio)
        assertEquals(WorkingHoursRangeStatus.WITHIN_RANGE, sheet.rangeStatus)
        assertEquals(21, sheet.imbalanceDayCount)
        assertEquals(emptyList(), sheet.projectWorkHours)
        assertEquals(DayKind.NationalHoliday("スポーツの日"), sheet.dayOf(october(12))?.dayKind)
        assertEquals(Hours.ZERO, sheet.dayOf(october(12))?.workingHours)
        assertEquals(DayKind.Weekend, sheet.dayOf(october(3))?.dayKind)
        assertNull(sheet.dayOf(LocalDate(2026, 11, 1)))
    }

    @Test
    fun `days after today are not counted in the totals`() {
        val sheet = sheet(today = october(5))
        // 10/1・10/2・10/5 の 3 日分
        assertEquals(h(2_400), sheet.totalWorkingHours)
        assertEquals(3, sheet.imbalanceDayCount)
        assertTrue(sheet.dayOf(october(5))!!.isCounted)
        val future = sheet.dayOf(october(6))!!
        assertFalse(future.isCounted)
        assertEquals(h(800), future.workingHours)
        assertFalse(future.hasImbalance)
        // 営業日数は月全体で数える
        assertEquals(21, sheet.businessDayCount)
    }

    @Test
    fun `month in the future has no totals`() {
        val sheet = sheet(today = LocalDate(2026, 9, 30))
        assertEquals(Hours.ZERO, sheet.totalWorkingHours)
        assertNull(sheet.unallocatedRatio)
        assertEquals(WorkingHoursRangeStatus.BELOW_MIN, sheet.rangeStatus)
        assertEquals(0, sheet.imbalanceDayCount)
        assertTrue(sheet.days.none { it.isCounted })
    }

    @Test
    fun `vacation is excluded from business days but absence is not`() {
        val sheet =
            sheet(
                listOf(
                    WorkRecord(october(1), flag = DayFlag.VACATION),
                    WorkRecord(october(2), flag = DayFlag.ABSENCE),
                    // 休みの日の「休」は営業日数に影響しない
                    WorkRecord(october(3), flag = DayFlag.VACATION),
                ),
            )
        assertEquals(21, sheet.workingDayCount)
        assertEquals(20, sheet.businessDayCount)
        assertEquals(h(15_200), sheet.totalWorkingHours)
        assertEquals(DayFlag.VACATION, sheet.dayOf(october(1))?.flag)
        assertFalse(sheet.dayOf(october(1))!!.hasImbalance)
        assertEquals(19, sheet.imbalanceDayCount)
    }

    @Test
    fun `work on a day off is counted when clock times are entered`() {
        val sheet =
            sheet(
                listOf(
                    WorkRecord(october(3), TimeOfDay.of(10, 0), TimeOfDay.of(15, 0), Hours.ZERO),
                    WorkRecord(october(12), clockIn = TimeOfDay.of(13, 30)),
                ),
            )
        assertEquals(h(500), sheet.dayOf(october(3))?.workingHours)
        // 13:30〜18:30 − 休憩 1 時間
        assertEquals(h(400), sheet.dayOf(october(12))?.workingHours)
        assertEquals(h(17_700), sheet.totalWorkingHours)
        assertEquals(21, sheet.businessDayCount)
    }

    @Test
    fun `company holidays are not working days`() {
        val sheet = sheet(companyHolidays = listOf(CompanyHoliday(october(30), "創立記念日")))
        assertEquals(DayKind.CompanyHoliday("創立記念日"), sheet.dayOf(october(30))?.dayKind)
        assertEquals(20, sheet.workingDayCount)
        assertEquals(20, sheet.businessDayCount)
        assertEquals(h(16_000), sheet.totalWorkingHours)
    }

    @Test
    fun `negative working hours become zero and are marked`() {
        val sheet = sheet(listOf(WorkRecord(october(1), TimeOfDay.of(18, 0), TimeOfDay.of(9, 0))))
        val day = sheet.dayOf(october(1))!!
        assertEquals(Hours.ZERO, day.workingHours)
        assertTrue(day.isWorkingHoursNegative)
        assertEquals(1, sheet.negativeWorkingHoursDayCount)
        assertEquals(h(16_000), sheet.totalWorkingHours)
        // 配分が無く稼働も 0 なので過不足は無い
        assertFalse(day.hasImbalance)
    }

    @Test
    fun `negative working hours of future days are not counted`() {
        val sheet = sheet(listOf(WorkRecord(october(2), TimeOfDay.of(18, 0), TimeOfDay.of(9, 0))), today = october(1))
        assertTrue(sheet.dayOf(october(2))!!.isWorkingHoursNegative)
        assertEquals(0, sheet.negativeWorkingHoursDayCount)
    }

    @Test
    fun `allocations give balances project hours and ratios`() {
        val sheet =
            sheet(
                listOf(
                    WorkRecord(october(1), allocations = mapOf("a" to h(500), "b" to h(300))),
                    WorkRecord(october(2), allocations = mapOf("a" to h(200), "b" to h(250), "z" to Hours.ZERO)),
                    // 今日より後の日の配分は含めない
                    WorkRecord(october(5), allocations = mapOf("c" to h(800))),
                ),
                today = october(2),
            )
        val day1 = sheet.dayOf(october(1))!!
        assertEquals(h(800), day1.allocatedHours)
        assertEquals(SignedHours.ZERO, day1.balance)
        assertFalse(day1.hasImbalance)
        val day2 = sheet.dayOf(october(2))!!
        assertEquals(h(450), day2.allocatedHours)
        assertEquals(SignedHours.ofHundredths(350), day2.balance)
        assertTrue(day2.hasImbalance)
        assertEquals(1, sheet.imbalanceDayCount)

        assertEquals(h(1_600), sheet.totalWorkingHours)
        assertEquals(h(1_250), sheet.totalAllocatedHours)
        assertEquals(SignedHours.ofHundredths(350), sheet.unallocatedHours)
        // 7/16 = 43.75% → 43.8%、5.5/16 = 34.375% → 34.4%、3.5/16 = 21.875% → 21.9% (合計は端数で 100.1%)
        assertEquals(
            listOf(
                ProjectWorkHours("a", h(700), AllocationRatio.ofTenthsOfPercent(438)),
                ProjectWorkHours("b", h(550), AllocationRatio.ofTenthsOfPercent(344)),
            ),
            sheet.projectWorkHours,
        )
        assertEquals(AllocationRatio.ofTenthsOfPercent(219), sheet.unallocatedRatio)
    }

    @Test
    fun `over allocation gives a negative balance`() {
        val sheet = sheet(listOf(WorkRecord(october(1), allocations = mapOf("a" to h(1_000)))), today = october(1))
        assertEquals(SignedHours.ofHundredths(-200), sheet.dayOf(october(1))?.balance)
        assertEquals(SignedHours.ofHundredths(-200), sheet.unallocatedHours)
        assertEquals(AllocationRatio.ofTenthsOfPercent(-250), sheet.unallocatedRatio)
        assertEquals(AllocationRatio.ofTenthsOfPercent(1_250), sheet.projectWorkHours.single().ratio)
    }

    @Test
    fun `allocation on a day off without work is an imbalance`() {
        val sheet = sheet(listOf(WorkRecord(october(3), allocations = mapOf("a" to h(100)))), today = october(3))
        assertEquals(SignedHours.ofHundredths(-100), sheet.dayOf(october(3))?.balance)
        assertTrue(sheet.dayOf(october(3))!!.hasImbalance)
    }

    @Test
    fun `projects with the same hours are ordered by id`() {
        val sheet =
            sheet(
                listOf(WorkRecord(october(1), allocations = mapOf("y" to h(400), "x" to h(400)))),
                today = october(1),
            )
        assertEquals(listOf("x", "y"), sheet.projectWorkHours.map { it.projectId })
    }

    @Test
    fun `range status compares the total with the shift settings`() {
        val shift = ShiftSettings.DEFAULT.copy(minHours = h(1_600), maxHours = h(2_000))
        assertEquals(WorkingHoursRangeStatus.BELOW_MIN, sheet(today = october(1), shiftSettings = shift).rangeStatus)
        // 下限・上限ちょうどは範囲内
        assertEquals(WorkingHoursRangeStatus.WITHIN_RANGE, sheet(today = october(2), shiftSettings = shift).rangeStatus)
        assertEquals(
            WorkingHoursRangeStatus.WITHIN_RANGE,
            sheet(today = october(2), shiftSettings = shift.copy(maxHours = h(1_600))).rangeStatus,
        )
        assertEquals(WorkingHoursRangeStatus.ABOVE_MAX, sheet(today = october(5), shiftSettings = shift).rangeStatus)
    }

    @Test
    fun `leap year february has 29 days`() {
        // 2028年2月: 2/1 は火曜日、祝日は 2/11 (金) と 2/23 (水)
        val sheet = sheet(yearMonth = YearMonth(2028, 2), today = LocalDate(2028, 3, 1))
        assertEquals(29, sheet.days.size)
        assertEquals(LocalDate(2028, 2, 29), sheet.days.last().date)
        assertEquals(19, sheet.workingDayCount)
        assertEquals(h(15_200), sheet.totalWorkingHours)
        assertEquals(28, sheet(yearMonth = YearMonth(2027, 2)).days.size)
    }

    @Test
    fun `month boundary uses only the target month`() {
        val sheet =
            sheet(
                listOf(
                    WorkRecord(LocalDate(2026, 9, 30), flag = DayFlag.VACATION),
                    WorkRecord(LocalDate(2026, 11, 2), flag = DayFlag.VACATION),
                    WorkRecord(october(31), TimeOfDay.of(9, 0), TimeOfDay.of(12, 0), Hours.ZERO),
                ),
                today = LocalDate(2026, 11, 2),
            )
        assertEquals(21, sheet.businessDayCount)
        assertEquals(h(17_100), sheet.totalWorkingHours)
        assertEquals(h(300), sheet.days.last().workingHours)
        // 12月 (31日・年末年始) と 11月 (30日) も組み立てられる
        assertEquals(31, sheet(yearMonth = YearMonth(2026, 12)).days.size)
        assertEquals(30, sheet(yearMonth = YearMonth(2026, 11)).days.size)
    }
}
