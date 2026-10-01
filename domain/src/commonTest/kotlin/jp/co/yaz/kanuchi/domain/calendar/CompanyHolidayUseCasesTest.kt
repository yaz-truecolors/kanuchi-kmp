package jp.co.yaz.kanuchi.domain.calendar

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompanyHolidayUseCasesTest {
    private val repository = FakeCompanyHolidayRepository()

    @Test
    fun `holidays of the year are requested from january 1 to december 31`() =
        runTest {
            val holiday = CompanyHoliday(LocalDate(2026, 12, 31), "年末休業")
            repository.holidays += holiday
            repository.holidays += CompanyHoliday(LocalDate(2027, 1, 1), "年始休業")

            val result = GetCompanyHolidaysOfYearUseCase(repository)(2026)

            assertEquals(listOf(holiday), result.getOrThrow())
            assertEquals(listOf(LocalDate(2026, 1, 1) to LocalDate(2026, 12, 31)), repository.requestedRanges)
        }

    @Test
    fun `each day of the period is added`() =
        runTest {
            val result = AddCompanyHolidaysUseCase(repository)(CompanyHolidayInput("2026-12-30", "2027-01-01", "年末年始休業"))

            val expected =
                listOf(LocalDate(2026, 12, 30), LocalDate(2026, 12, 31), LocalDate(2027, 1, 1))
                    .map { CompanyHoliday(it, "年末年始休業") }
            assertEquals(expected, result.getOrThrow())
            assertEquals(expected, repository.holidays)
            assertEquals(listOf(LocalDate(2026, 12, 30) to LocalDate(2027, 1, 1)), repository.requestedRanges)
        }

    @Test
    fun `invalid input is not added`() =
        runTest {
            val result = AddCompanyHolidaysUseCase(repository)(CompanyHolidayInput("2026-12-30", "", ""))

            assertEquals(
                setOf(CompanyHolidayViolation.NAME_BLANK),
                assertIs<InvalidCompanyHolidayInputException>(result.exceptionOrNull()).violations,
            )
            assertTrue(repository.holidays.isEmpty())
            assertTrue(repository.requestedRanges.isEmpty())
        }

    @Test
    fun `nothing is added when the period contains an existing holiday`() =
        runTest {
            val existing = CompanyHoliday(LocalDate(2026, 12, 31), "大晦日")
            repository.holidays += existing

            val result = AddCompanyHolidaysUseCase(repository)(CompanyHolidayInput("2026-12-29", "2027-01-03", "年末年始休業"))

            assertIs<DuplicateCompanyHolidayException>(result.exceptionOrNull())
            assertEquals(listOf(existing), repository.holidays)
        }

    @Test
    fun `failure of the existing check is returned without adding`() =
        runTest {
            val failure = GenericDataFailureException()
            repository.getFailure = failure

            val result = AddCompanyHolidaysUseCase(repository)(CompanyHolidayInput("2026-12-29", "", "休業"))

            assertEquals(failure, result.exceptionOrNull())
            assertTrue(repository.holidays.isEmpty())
        }

    @Test
    fun `holiday is deleted by date`() =
        runTest {
            repository.holidays += CompanyHoliday(LocalDate(2026, 12, 31), "大晦日")

            val result = DeleteCompanyHolidayUseCase(repository)(LocalDate(2026, 12, 31))

            assertTrue(result.isSuccess)
            assertTrue(repository.holidays.isEmpty())
        }
}
