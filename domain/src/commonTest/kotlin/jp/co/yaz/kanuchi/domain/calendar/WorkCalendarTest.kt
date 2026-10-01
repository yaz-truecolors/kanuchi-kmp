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

    @Test
    fun `company holiday name length is counted in code points`() {
        val emoji = "\uD83C\uDF8D"
        assertTrue(CompanyHoliday.isValidName(emoji.repeat(CompanyHoliday.MAX_NAME_LENGTH)))
        assertFalse(CompanyHoliday.isValidName(emoji.repeat(CompanyHoliday.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun `company holiday name must not start or end with any whitespace the database also rejects`() {
        // DB の company_holidays_name_check で前後に置けない文字の集合と一致させる
        val whitespaces =
            listOf('\t', '\n', '\u000B', '\u000C', '\r', '\u001C', '\u001F', ' ', '\u00A0', '\u1680') +
                ('\u2000'..'\u200A') + listOf('\u2028', '\u2029', '\u202F', '\u205F', '\u3000')
        for (whitespace in whitespaces) {
            assertFalse(CompanyHoliday.isValidName("${whitespace}創立記念日"), "leading U+${whitespace.code.toString(16)}")
            assertFalse(CompanyHoliday.isValidName("創立記念日$whitespace"), "trailing U+${whitespace.code.toString(16)}")
        }
        assertTrue(CompanyHoliday.isValidName("年末\u3000年始"))
    }
}
