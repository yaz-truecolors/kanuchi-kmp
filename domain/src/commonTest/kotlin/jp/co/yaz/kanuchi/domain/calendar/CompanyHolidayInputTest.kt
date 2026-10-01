package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CompanyHolidayInputTest {
    private fun violationsOf(input: CompanyHolidayInput): Set<CompanyHolidayViolation> =
        assertIs<InvalidCompanyHolidayInputException>(input.toCompanyHolidays().exceptionOrNull()).violations

    @Test
    fun `single day is converted when end date is blank`() {
        val holidays = CompanyHolidayInput(startDate = "2026-08-14", endDate = " ", name = " 夏季休業 ").toCompanyHolidays()

        assertEquals(listOf(CompanyHoliday(LocalDate(2026, 8, 14), "夏季休業")), holidays.getOrThrow())
    }

    @Test
    fun `period is converted to each day including the end of year`() {
        val holidays = CompanyHolidayInput(startDate = "2026-12-29", endDate = "2027-01-03", name = "年末年始休業").toCompanyHolidays()

        assertEquals(
            listOf(
                LocalDate(2026, 12, 29),
                LocalDate(2026, 12, 30),
                LocalDate(2026, 12, 31),
                LocalDate(2027, 1, 1),
                LocalDate(2027, 1, 2),
                LocalDate(2027, 1, 3),
            ),
            holidays.getOrThrow().map { it.date },
        )
        assertEquals(setOf("年末年始休業"), holidays.getOrThrow().map { it.name }.toSet())
    }

    @Test
    fun `same start and end date is a single day`() {
        val holidays = CompanyHolidayInput(startDate = "2026-05-01", endDate = "2026-05-01", name = "創立記念日").toCompanyHolidays()

        assertEquals(listOf(LocalDate(2026, 5, 1)), holidays.getOrThrow().map { it.date })
    }

    @Test
    fun `period up to the maximum days is accepted`() {
        val holidays = CompanyHolidayInput(startDate = "2026-01-01", endDate = "2026-01-31", name = "休業").toCompanyHolidays()

        assertEquals(CompanyHolidayInput.MAX_DAYS, holidays.getOrThrow().size)
    }

    @Test
    fun `period longer than the maximum days is rejected`() {
        val violations = violationsOf(CompanyHolidayInput(startDate = "2026-01-01", endDate = "2026-02-01", name = "休業"))

        assertEquals(setOf(CompanyHolidayViolation.PERIOD_TOO_LONG), violations)
    }

    @Test
    fun `end date before start date is rejected`() {
        val violations = violationsOf(CompanyHolidayInput(startDate = "2026-01-03", endDate = "2026-01-02", name = "休業"))

        assertEquals(setOf(CompanyHolidayViolation.END_DATE_BEFORE_START_DATE), violations)
    }

    @Test
    fun `all violations are reported at once`() {
        val violations = violationsOf(CompanyHolidayInput(startDate = "2026-13-01", endDate = "abc", name = "  "))

        assertEquals(
            setOf(
                CompanyHolidayViolation.START_DATE_INVALID,
                CompanyHolidayViolation.END_DATE_INVALID,
                CompanyHolidayViolation.NAME_BLANK,
            ),
            violations,
        )
    }

    @Test
    fun `blank start date is invalid`() {
        val violations = violationsOf(CompanyHolidayInput(startDate = "", endDate = "", name = "休業"))

        assertEquals(setOf(CompanyHolidayViolation.START_DATE_INVALID), violations)
    }

    @Test
    fun `name length is limited`() {
        val maxName = "あ".repeat(CompanyHoliday.MAX_NAME_LENGTH)

        assertEquals(
            maxName,
            CompanyHolidayInput(startDate = "2026-05-01", endDate = "", name = maxName)
                .toCompanyHolidays()
                .getOrThrow()
                .single()
                .name,
        )
        assertEquals(
            setOf(CompanyHolidayViolation.NAME_TOO_LONG),
            violationsOf(CompanyHolidayInput(startDate = "2026-05-01", endDate = "", name = maxName + "あ")),
        )
    }

    @Test
    fun `date accepts slash separator and single digit month and day`() {
        assertEquals(LocalDate(2026, 5, 1), CompanyHolidayInput.parseDate("2026/5/1"))
        assertEquals(LocalDate(2026, 5, 1), CompanyHolidayInput.parseDate(" 2026-05-01 "))
        assertEquals(LocalDate(2026, 12, 31), CompanyHolidayInput.parseDate("2026-12-31"))
    }

    @Test
    fun `invalid dates are rejected`() {
        assertNull(CompanyHolidayInput.parseDate(""))
        assertNull(CompanyHolidayInput.parseDate("20260501"))
        assertNull(CompanyHolidayInput.parseDate("2026-02-29"))
        assertNull(CompanyHolidayInput.parseDate("2026-04-31"))
        assertNull(CompanyHolidayInput.parseDate("2026-00-10"))
        assertNull(CompanyHolidayInput.parseDate("2026-05-001"))
        assertNull(CompanyHolidayInput.parseDate("26-05-01"))
        assertEquals(LocalDate(2028, 2, 29), CompanyHolidayInput.parseDate("2028-02-29"))
    }

    @Test
    fun `dates outside the supported years are rejected`() {
        assertNull(CompanyHolidayInput.parseDate("1999-12-31"))
        assertNull(CompanyHolidayInput.parseDate("2100-01-01"))
        assertEquals(LocalDate(2000, 1, 1), CompanyHolidayInput.parseDate("2000-01-01"))
        assertEquals(LocalDate(2099, 12, 31), CompanyHolidayInput.parseDate("2099-12-31"))
    }
}
