package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkCalendarTest {
    private val calendar =
        WorkCalendar(
            listOf(
                CompanyHoliday(LocalDate(2026, 12, 29), "年末休業"),
                CompanyHoliday(LocalDate(2026, 12, 26), "土曜の休業日"),
                CompanyHoliday(LocalDate(2027, 1, 1), "元日と重なる休業日"),
            ),
        )

    @Test
    fun `weekday that is not a holiday is a working day`() {
        assertEquals(DayKind.Weekday, calendar.dayKindOf(LocalDate(2026, 12, 28)))
        assertTrue(calendar.isWorkingDay(LocalDate(2026, 12, 28)))
    }

    @Test
    fun `weekend is not a working day`() {
        assertEquals(DayKind.Weekend, calendar.dayKindOf(LocalDate(2026, 12, 27)))
        assertFalse(calendar.isWorkingDay(LocalDate(2026, 12, 27)))
    }

    @Test
    fun `national holiday is not a working day`() {
        assertEquals(DayKind.NationalHoliday("文化の日"), calendar.dayKindOf(LocalDate(2026, 11, 3)))
        assertFalse(calendar.isWorkingDay(LocalDate(2026, 11, 3)))
    }

    @Test
    fun `company holiday is not a working day`() {
        assertEquals(DayKind.CompanyHoliday("年末休業"), calendar.dayKindOf(LocalDate(2026, 12, 29)))
        assertFalse(calendar.isWorkingDay(LocalDate(2026, 12, 29)))
    }

    @Test
    fun `national holiday takes priority over company holiday and company holiday over weekend`() {
        assertEquals(DayKind.NationalHoliday("元日"), calendar.dayKindOf(LocalDate(2027, 1, 1)))
        assertEquals(DayKind.CompanyHoliday("土曜の休業日"), calendar.dayKindOf(LocalDate(2026, 12, 26)))
    }

    @Test
    fun `company holiday name must be trimmed and within the length limit`() {
        assertTrue(CompanyHoliday.isValidName("創立記念日"))
        assertTrue(CompanyHoliday.isValidName("あ".repeat(CompanyHoliday.MAX_NAME_LENGTH)))
        assertFalse(CompanyHoliday.isValidName(""))
        assertFalse(CompanyHoliday.isValidName(" 創立記念日"))
        assertFalse(CompanyHoliday.isValidName("あ".repeat(CompanyHoliday.MAX_NAME_LENGTH + 1)))
        assertFailsWith<IllegalArgumentException> { CompanyHoliday(LocalDate(2026, 1, 5), " ") }
    }
}
