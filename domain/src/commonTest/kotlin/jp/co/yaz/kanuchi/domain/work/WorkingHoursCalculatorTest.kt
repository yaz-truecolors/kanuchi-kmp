package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkingHoursCalculatorTest {
    // 定時: 9:30〜18:30、休憩 1 時間 (8 時間)
    private val shift = ShiftSettings.DEFAULT
    private val date = LocalDate(2026, 10, 1)

    private fun calculate(
        dayKind: DayKind,
        record: WorkRecord?,
    ): DailyWorkingHours = WorkingHoursCalculator.calculate(dayKind, record, shift)

    private fun hours(hundredths: Int) = DailyWorkingHours(Hours.ofHundredths(hundredths), isNegative = false)

    @Test
    fun `working day without input counts as the regular shift`() {
        assertEquals(hours(800), calculate(DayKind.Weekday, null))
        assertEquals(hours(800), calculate(DayKind.Weekday, WorkRecord(date, note = "メモだけ")))
    }

    @Test
    fun `working day uses the entered values and the shift for the rest`() {
        assertEquals(
            hours(950),
            calculate(DayKind.Weekday, WorkRecord(date, TimeOfDay.of(9, 0), TimeOfDay.of(19, 30), Hours.ofHundredths(100))),
        )
        // 退勤だけ入力: 出勤・休憩は定時 (9:30〜20:00 − 1)
        assertEquals(hours(950), calculate(DayKind.Weekday, WorkRecord(date, clockOut = TimeOfDay.of(20, 0))))
        // 休憩だけ入力: 0 時間も入力として扱う
        assertEquals(hours(900), calculate(DayKind.Weekday, WorkRecord(date, breakHours = Hours.ZERO)))
    }

    @Test
    fun `minutes are rounded to hundredths of an hour`() {
        // 8 時間 20 分 = 8.333… → 8.33、休憩 1 時間で 7.33
        assertEquals(hours(733), calculate(DayKind.Weekday, WorkRecord(date, TimeOfDay.of(9, 0), TimeOfDay.of(17, 20))))
        // 8 時間 10 分 = 8.1666… → 8.17
        assertEquals(hours(717), calculate(DayKind.Weekday, WorkRecord(date, TimeOfDay.of(9, 0), TimeOfDay.of(17, 10))))
        // 1 分 = 0.0166… → 0.02、休憩 0
        assertEquals(
            hours(2),
            calculate(DayKind.Weekday, WorkRecord(date, TimeOfDay.of(9, 0), TimeOfDay.of(9, 1), Hours.ZERO)),
        )
    }

    @Test
    fun `day off without clock input is zero`() {
        assertEquals(hours(0), calculate(DayKind.Weekend, null))
        assertEquals(hours(0), calculate(DayKind.NationalHoliday("文化の日"), WorkRecord(date, breakHours = Hours.ZERO)))
        assertEquals(
            hours(0),
            calculate(DayKind.CompanyHoliday("年末休業"), WorkRecord(date, allocations = mapOf("p" to Hours.ofHundredths(100)))),
        )
    }

    @Test
    fun `day off with clock input counts as work`() {
        assertEquals(
            hours(400),
            calculate(DayKind.Weekend, WorkRecord(date, TimeOfDay.of(10, 0), TimeOfDay.of(14, 0), Hours.ZERO)),
        )
        // 出勤だけ入力: 退勤・休憩は定時 (10:00〜18:30 − 1)
        assertEquals(hours(750), calculate(DayKind.NationalHoliday("文化の日"), WorkRecord(date, clockIn = TimeOfDay.of(10, 0))))
        // 退勤だけ入力: 出勤・休憩は定時 (9:30〜12:30 − 1)
        assertEquals(hours(200), calculate(DayKind.CompanyHoliday("年末休業"), WorkRecord(date, clockOut = TimeOfDay.of(12, 30))))
    }

    @Test
    fun `flagged day is zero even with input`() {
        val input = WorkRecord(date, TimeOfDay.of(9, 0), TimeOfDay.of(18, 0))
        assertEquals(hours(0), calculate(DayKind.Weekday, input.copy(flag = DayFlag.VACATION)))
        assertEquals(hours(0), calculate(DayKind.Weekday, input.copy(flag = DayFlag.ABSENCE)))
        assertEquals(hours(0), calculate(DayKind.Weekend, input.copy(flag = DayFlag.VACATION)))
    }

    @Test
    fun `negative result becomes zero and is marked`() {
        val negative = DailyWorkingHours(Hours.ZERO, isNegative = true)
        assertEquals(negative, calculate(DayKind.Weekday, WorkRecord(date, TimeOfDay.of(18, 0), TimeOfDay.of(9, 0))))
        // 休憩が長すぎる
        assertEquals(negative, calculate(DayKind.Weekday, WorkRecord(date, breakHours = Hours.ofHundredths(1_000))))
        // ちょうど 0 は負ではない
        assertEquals(hours(0), calculate(DayKind.Weekday, WorkRecord(date, TimeOfDay.of(9, 0), TimeOfDay.of(10, 0))))
    }
}
