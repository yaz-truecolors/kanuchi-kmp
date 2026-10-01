package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.calendar.FakeCompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.FixedClock
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.shift.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GetMonthlyWorkSheetUseCaseTest {
    private val workRecordRepository = FakeWorkRecordRepository()
    private val shiftSettingsRepository = FakeShiftSettingsRepository()
    private val companyHolidayRepository = FakeCompanyHolidayRepository()

    // 日本時間 2026-10-02 0:30 (UTC では 10/1)
    private val useCase =
        GetMonthlyWorkSheetUseCase(
            workRecordRepository,
            shiftSettingsRepository,
            companyHolidayRepository,
            FixedClock("2026-10-01T15:30:00Z"),
        )

    private val october = YearMonth(2026, 10)

    @Test
    fun `builds the sheet from records shift settings company holidays and today`() =
        runTest {
            val shift = ShiftSettings.DEFAULT.copy(breakHours = Hours.ZERO)
            shiftSettingsRepository.getOfResults["user-1"] = Result.success(shift)
            workRecordRepository.records["user-1"] =
                listOf(WorkRecord(LocalDate(2026, 10, 1), allocations = mapOf("p" to Hours.ofHundredths(900))))
            companyHolidayRepository.holidays += CompanyHoliday(LocalDate(2026, 10, 30), "創立記念日")

            val sheet = useCase("user-1", october).getOrThrow()

            assertEquals(october, sheet.yearMonth)
            assertEquals(shift, sheet.shiftSettings)
            assertEquals(DayKind.CompanyHoliday("創立記念日"), sheet.dayOf(LocalDate(2026, 10, 30))?.dayKind)
            // 今日は日本時間の 10/2 なので 10/1・10/2 が合計対象 (休憩 0 で 9 時間 × 2)
            assertTrue(sheet.dayOf(LocalDate(2026, 10, 2))!!.isCounted)
            assertFalse(sheet.dayOf(LocalDate(2026, 10, 3))!!.isCounted)
            assertEquals(Hours.ofHundredths(1_800), sheet.totalWorkingHours)
            assertEquals(Hours.ofHundredths(900), sheet.totalAllocatedHours)
            assertEquals(listOf(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31)), companyHolidayRepository.requestedRanges)
        }

    @Test
    fun `uses the default shift settings when the user has none`() =
        runTest {
            val sheet = useCase("user-2", october).getOrThrow()
            assertEquals(ShiftSettings.DEFAULT, sheet.shiftSettings)
            assertEquals(Hours.ofHundredths(1_600), sheet.totalWorkingHours)
        }

    @Test
    fun `returns the failure of any repository`() =
        runTest {
            val error = IllegalStateException("records")
            workRecordRepository.getFailure = error
            assertSame(error, useCase("user-1", october).exceptionOrNull())

            workRecordRepository.getFailure = null
            val shiftError = IllegalStateException("shift")
            shiftSettingsRepository.getOfResults["user-1"] = Result.failure(shiftError)
            assertSame(shiftError, useCase("user-1", october).exceptionOrNull())

            shiftSettingsRepository.getOfResults.clear()
            val holidayError = IllegalStateException("holidays")
            companyHolidayRepository.getFailure = holidayError
            assertSame(holidayError, useCase("user-1", october).exceptionOrNull())
        }
}
