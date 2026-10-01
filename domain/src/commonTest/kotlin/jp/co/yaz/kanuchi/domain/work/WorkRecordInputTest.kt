package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkRecordInputTest {
    private val date = LocalDate(2026, 10, 1)
    private val shift = ShiftSettings.DEFAULT

    private fun WorkRecordInput.toRecord(dayKind: DayKind = DayKind.Weekday) = toWorkRecord(date, dayKind, shift)

    private fun WorkRecordInput.failure(dayKind: DayKind = DayKind.Weekday): InvalidWorkRecordException =
        assertIs<InvalidWorkRecordException>(toRecord(dayKind).exceptionOrNull())

    @Test
    fun `blank input becomes an empty record`() {
        val record = WorkRecordInput().toRecord().getOrThrow()

        assertEquals(WorkRecord(date), record)
    }

    @Test
    fun `entered values are parsed and zero or blank allocations are dropped`() {
        val input =
            WorkRecordInput(
                clockIn = "9:00",
                clockOut = " 19:15 ",
                breakHours = "0.75",
                note = "  客先訪問 ",
                allocations = mapOf("a" to "5.5", "b" to "0", "c" to " ", "d" to "3"),
            )

        val record = input.toRecord().getOrThrow()

        assertEquals(TimeOfDay.of(9, 0), record.clockIn)
        assertEquals(TimeOfDay.of(19, 15), record.clockOut)
        assertEquals(Hours.ofHundredths(75), record.breakHours)
        assertEquals("客先訪問", record.note)
        assertEquals(mapOf("a" to Hours.ofHundredths(550), "d" to Hours.ofHundredths(300)), record.allocations)
    }

    @Test
    fun `invalid formats are reported for each field`() {
        val error =
            WorkRecordInput(clockIn = "9時", clockOut = "25:00", breakHours = "-1", allocations = mapOf("a" to "x", "b" to "1.234"))
                .failure()

        assertEquals(
            setOf(
                WorkRecordViolation.CLOCK_IN_INVALID_FORMAT,
                WorkRecordViolation.CLOCK_OUT_INVALID_FORMAT,
                WorkRecordViolation.BREAK_HOURS_INVALID_FORMAT,
            ),
            error.violations,
        )
        assertEquals(
            mapOf("a" to AllocationHoursViolation.INVALID_FORMAT, "b" to AllocationHoursViolation.INVALID_FORMAT),
            error.allocationViolations,
        )
    }

    @Test
    fun `hours over the db column limit are rejected`() {
        val error = WorkRecordInput(breakHours = "100", allocations = mapOf("a" to "99.99", "b" to "100")).failure()

        assertEquals(setOf(WorkRecordViolation.BREAK_HOURS_TOO_LARGE), error.violations)
        assertEquals(mapOf("b" to AllocationHoursViolation.TOO_LARGE), error.allocationViolations)
    }

    @Test
    fun `clock out must be after clock in when both are entered`() {
        val error = WorkRecordInput(clockIn = "18:00", clockOut = "18:00").failure()

        assertEquals(setOf(WorkRecordViolation.CLOCK_OUT_NOT_AFTER_CLOCK_IN), error.violations)
    }

    @Test
    fun `negative working hours combined with the shift is rejected`() {
        // 退勤だけ 09:45 (出勤は定時の 09:30) で休憩 1 時間 → 負
        val error = WorkRecordInput(clockOut = "09:45").failure()

        assertEquals(setOf(WorkRecordViolation.WORKING_HOURS_NEGATIVE), error.violations)
    }

    @Test
    fun `break only on a day off is not negative`() {
        val record = WorkRecordInput(breakHours = "5").toRecord(DayKind.Weekend).getOrThrow()

        assertEquals(Hours.ofHundredths(500), record.breakHours)
    }

    @Test
    fun `work inputs are ignored and not validated when a flag is set`() {
        val input =
            WorkRecordInput(
                clockIn = "abc",
                clockOut = "10:00",
                breakHours = "x",
                flag = DayFlag.VACATION,
                note = "通院",
                allocations = mapOf("a" to "x"),
            )

        val record = input.toRecord().getOrThrow()

        assertEquals(WorkRecord(date, flag = DayFlag.VACATION, note = "通院"), record)
        assertFalse(input.acceptsWorkInput)
    }

    @Test
    fun `preview recalculates working hours allocation and balance`() {
        val preview =
            WorkRecordInput(clockIn = "09:00", clockOut = "19:00", allocations = mapOf("a" to "6", "b" to "2.5"))
                .preview(DayKind.Weekday, shift)

        assertEquals(DailyWorkingHours(Hours.ofHundredths(900), isNegative = false), preview.workingHours)
        assertEquals(Hours.ofHundredths(850), preview.allocatedHours)
        assertEquals(SignedHours.ofHundredths(50), preview.balance)
    }

    @Test
    fun `preview of blank input on a weekday uses the shift`() {
        val preview = WorkRecordInput().preview(DayKind.Weekday, shift)

        assertEquals(Hours.ofHundredths(800), preview.workingHours?.hours)
        assertEquals(SignedHours.ofHundredths(800), preview.balance)
    }

    @Test
    fun `preview cannot calculate values with invalid formats`() {
        val preview = WorkRecordInput(clockIn = "x", allocations = mapOf("a" to "y")).preview(DayKind.Weekday, shift)

        assertNull(preview.workingHours)
        assertNull(preview.allocatedHours)
        assertNull(preview.balance)
    }

    @Test
    fun `preview shows negative working hours`() {
        val preview = WorkRecordInput(breakHours = "10").preview(DayKind.Weekday, shift)

        assertTrue(preview.workingHours!!.isNegative)
    }

    @Test
    fun `preview of a flagged day is zero`() {
        val preview =
            WorkRecordInput(
                clockIn = "08:00",
                flag = DayFlag.ABSENCE,
                allocations = mapOf("a" to "3"),
            ).preview(DayKind.Weekday, shift)

        assertEquals(Hours.ZERO, preview.workingHours?.hours)
        assertEquals(SignedHours.ZERO, preview.balance)
    }

    @Test
    fun `saved record is converted to input text`() {
        val record =
            WorkRecord(
                date = date,
                clockIn = TimeOfDay.of(9, 5),
                breakHours = Hours.ofHundredths(150),
                flag = DayFlag.ABSENCE,
                note = "メモ",
                allocations = mapOf("a" to Hours.ofHundredths(725)),
            )

        val input = WorkRecordInput.from(record)

        assertEquals(
            WorkRecordInput(clockIn = "09:05", breakHours = "1.5", flag = DayFlag.ABSENCE, note = "メモ", allocations = mapOf("a" to "7.25")),
            input,
        )
        assertEquals(WorkRecordInput(), WorkRecordInput.from(null))
    }
}
