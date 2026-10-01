package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JapaneseHolidaysTest {
    /**
     * 内閣府「国民の祝日について」の祝日一覧 (https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv、2026年10月取得) の
     * 2024〜2027 年分。振替休日・国民の休日は一覧では「休日」と表記される。
     */
    private val officialHolidays2024To2027: List<Pair<String, String>> =
        listOf(
            // 2024 年
            "2024-01-01" to "元日",
            "2024-01-08" to "成人の日",
            "2024-02-11" to "建国記念の日",
            "2024-02-12" to "休日",
            "2024-02-23" to "天皇誕生日",
            "2024-03-20" to "春分の日",
            "2024-04-29" to "昭和の日",
            "2024-05-03" to "憲法記念日",
            "2024-05-04" to "みどりの日",
            "2024-05-05" to "こどもの日",
            "2024-05-06" to "休日",
            "2024-07-15" to "海の日",
            "2024-08-11" to "山の日",
            "2024-08-12" to "休日",
            "2024-09-16" to "敬老の日",
            "2024-09-22" to "秋分の日",
            "2024-09-23" to "休日",
            "2024-10-14" to "スポーツの日",
            "2024-11-03" to "文化の日",
            "2024-11-04" to "休日",
            "2024-11-23" to "勤労感謝の日",
            // 2025 年
            "2025-01-01" to "元日",
            "2025-01-13" to "成人の日",
            "2025-02-11" to "建国記念の日",
            "2025-02-23" to "天皇誕生日",
            "2025-02-24" to "休日",
            "2025-03-20" to "春分の日",
            "2025-04-29" to "昭和の日",
            "2025-05-03" to "憲法記念日",
            "2025-05-04" to "みどりの日",
            "2025-05-05" to "こどもの日",
            "2025-05-06" to "休日",
            "2025-07-21" to "海の日",
            "2025-08-11" to "山の日",
            "2025-09-15" to "敬老の日",
            "2025-09-23" to "秋分の日",
            "2025-10-13" to "スポーツの日",
            "2025-11-03" to "文化の日",
            "2025-11-23" to "勤労感謝の日",
            "2025-11-24" to "休日",
            // 2026 年
            "2026-01-01" to "元日",
            "2026-01-12" to "成人の日",
            "2026-02-11" to "建国記念の日",
            "2026-02-23" to "天皇誕生日",
            "2026-03-20" to "春分の日",
            "2026-04-29" to "昭和の日",
            "2026-05-03" to "憲法記念日",
            "2026-05-04" to "みどりの日",
            "2026-05-05" to "こどもの日",
            "2026-05-06" to "休日",
            "2026-07-20" to "海の日",
            "2026-08-11" to "山の日",
            "2026-09-21" to "敬老の日",
            "2026-09-22" to "休日",
            "2026-09-23" to "秋分の日",
            "2026-10-12" to "スポーツの日",
            "2026-11-03" to "文化の日",
            "2026-11-23" to "勤労感謝の日",
            // 2027 年
            "2027-01-01" to "元日",
            "2027-01-11" to "成人の日",
            "2027-02-11" to "建国記念の日",
            "2027-02-23" to "天皇誕生日",
            "2027-03-21" to "春分の日",
            "2027-03-22" to "休日",
            "2027-04-29" to "昭和の日",
            "2027-05-03" to "憲法記念日",
            "2027-05-04" to "みどりの日",
            "2027-05-05" to "こどもの日",
            "2027-07-19" to "海の日",
            "2027-08-11" to "山の日",
            "2027-09-20" to "敬老の日",
            "2027-09-23" to "秋分の日",
            "2027-10-11" to "スポーツの日",
            "2027-11-03" to "文化の日",
            "2027-11-23" to "勤労感謝の日",
        )

    /** 同じ一覧の 2000〜2023 年分の日付 (月-日)。法改正・特例 (2019 年の即位、2020・2021 年の東京オリンピック等) を含む。 */
    private val officialHolidayDates2000To2023: Map<Int, String> =
        mapOf(
            2000 to "01-01 01-10 02-11 03-20 04-29 05-03 05-04 05-05 07-20 09-15 09-23 10-09 11-03 11-23 12-23",
            2001 to "01-01 01-08 02-11 02-12 03-20 04-29 04-30 05-03 05-04 05-05 07-20 09-15 09-23 09-24 10-08 11-03 11-23 12-23 12-24",
            2002 to "01-01 01-14 02-11 03-21 04-29 05-03 05-04 05-05 05-06 07-20 09-15 09-16 09-23 10-14 11-03 11-04 11-23 12-23",
            2003 to "01-01 01-13 02-11 03-21 04-29 05-03 05-05 07-21 09-15 09-23 10-13 11-03 11-23 11-24 12-23",
            2004 to "01-01 01-12 02-11 03-20 04-29 05-03 05-04 05-05 07-19 09-20 09-23 10-11 11-03 11-23 12-23",
            2005 to "01-01 01-10 02-11 03-20 03-21 04-29 05-03 05-04 05-05 07-18 09-19 09-23 10-10 11-03 11-23 12-23",
            2006 to "01-01 01-02 01-09 02-11 03-21 04-29 05-03 05-04 05-05 07-17 09-18 09-23 10-09 11-03 11-23 12-23",
            2007 to "01-01 01-08 02-11 02-12 03-21 04-29 04-30 05-03 05-04 05-05 07-16 09-17 09-23 09-24 10-08 11-03 11-23 12-23 12-24",
            2008 to "01-01 01-14 02-11 03-20 04-29 05-03 05-04 05-05 05-06 07-21 09-15 09-23 10-13 11-03 11-23 11-24 12-23",
            2009 to "01-01 01-12 02-11 03-20 04-29 05-03 05-04 05-05 05-06 07-20 09-21 09-22 09-23 10-12 11-03 11-23 12-23",
            2010 to "01-01 01-11 02-11 03-21 03-22 04-29 05-03 05-04 05-05 07-19 09-20 09-23 10-11 11-03 11-23 12-23",
            2011 to "01-01 01-10 02-11 03-21 04-29 05-03 05-04 05-05 07-18 09-19 09-23 10-10 11-03 11-23 12-23",
            2012 to "01-01 01-02 01-09 02-11 03-20 04-29 04-30 05-03 05-04 05-05 07-16 09-17 09-22 10-08 11-03 11-23 12-23 12-24",
            2013 to "01-01 01-14 02-11 03-20 04-29 05-03 05-04 05-05 05-06 07-15 09-16 09-23 10-14 11-03 11-04 11-23 12-23",
            2014 to "01-01 01-13 02-11 03-21 04-29 05-03 05-04 05-05 05-06 07-21 09-15 09-23 10-13 11-03 11-23 11-24 12-23",
            2015 to "01-01 01-12 02-11 03-21 04-29 05-03 05-04 05-05 05-06 07-20 09-21 09-22 09-23 10-12 11-03 11-23 12-23",
            2016 to "01-01 01-11 02-11 03-20 03-21 04-29 05-03 05-04 05-05 07-18 08-11 09-19 09-22 10-10 11-03 11-23 12-23",
            2017 to "01-01 01-02 01-09 02-11 03-20 04-29 05-03 05-04 05-05 07-17 08-11 09-18 09-23 10-09 11-03 11-23 12-23",
            2018 to
                "01-01 01-08 02-11 02-12 03-21 04-29 04-30 05-03 05-04 05-05 " +
                "07-16 08-11 09-17 09-23 09-24 10-08 11-03 11-23 12-23 12-24",
            2019 to
                "01-01 01-14 02-11 03-21 04-29 04-30 05-01 05-02 05-03 05-04 05-05 " +
                "05-06 07-15 08-11 08-12 09-16 09-23 10-14 10-22 11-03 11-04 11-23",
            2020 to "01-01 01-13 02-11 02-23 02-24 03-20 04-29 05-03 05-04 05-05 05-06 07-23 07-24 08-10 09-21 09-22 11-03 11-23",
            2021 to "01-01 01-11 02-11 02-23 03-20 04-29 05-03 05-04 05-05 07-22 07-23 08-08 08-09 09-20 09-23 11-03 11-23",
            2022 to "01-01 01-10 02-11 02-23 03-21 04-29 05-03 05-04 05-05 07-18 08-11 09-19 09-23 10-10 11-03 11-23",
            2023 to "01-01 01-02 01-09 02-11 02-23 03-21 04-29 05-03 05-04 05-05 07-17 08-11 09-18 09-23 10-09 11-03 11-23",
        )

    @Test
    fun `holidays from 2024 to 2027 match the official list including names`() {
        val expected = officialHolidays2024To2027.associate { (date, name) -> LocalDate.parse(date) to name }
        val actual =
            (2024..2027)
                .flatMap { JapaneseHolidays.holidaysOf(it).entries }
                .associate { (date, name) -> date to officialNameOf(name) }
        assertEquals(expected, actual)
    }

    @Test
    fun `holiday dates from 2000 to 2023 match the official list`() {
        officialHolidayDates2000To2023.forEach { (year, dates) ->
            val expected = dates.split(" ").map { LocalDate.parse("$year-$it") }
            assertEquals(expected, JapaneseHolidays.holidaysOf(year).keys.toList(), "$year")
        }
    }

    @Test
    fun `holidayNameOf returns the name only for holidays`() {
        assertEquals("元日", JapaneseHolidays.holidayNameOf(LocalDate(2026, 1, 1)))
        assertEquals(JapaneseHolidays.SUBSTITUTE_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2026, 5, 6)))
        assertEquals(JapaneseHolidays.CITIZENS_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2026, 9, 22)))
        assertNull(JapaneseHolidays.holidayNameOf(LocalDate(2026, 10, 1)))
        assertTrue(JapaneseHolidays.isHoliday(LocalDate(2026, 11, 3)))
        assertFalse(JapaneseHolidays.isHoliday(LocalDate(2026, 11, 4)))
    }

    @Test
    fun `substitute holiday moves past consecutive holidays from 2007`() {
        // 2008-05-04 (日・みどりの日) の振替休日は、5/5 (こどもの日) の翌日の 5/6
        assertEquals(JapaneseHolidays.SUBSTITUTE_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2008, 5, 6)))
        // 2006 年以前は翌日の月曜日だけ: 2003-11-23 (日) → 11/24
        assertEquals(JapaneseHolidays.SUBSTITUTE_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2003, 11, 24)))
    }

    @Test
    fun `citizens holiday before 2007 is not set on Sunday`() {
        // 2003-05-04 は日曜日のため国民の休日にならない (2002-05-04 は土曜日で国民の休日)
        assertNull(JapaneseHolidays.holidayNameOf(LocalDate(2003, 5, 4)))
        assertEquals(JapaneseHolidays.CITIZENS_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2002, 5, 4)))
    }

    @Test
    fun `special holidays of 2019 2020 and 2021 are applied`() {
        assertEquals("天皇の即位の日", JapaneseHolidays.holidayNameOf(LocalDate(2019, 5, 1)))
        assertEquals("即位礼正殿の儀の行われる日", JapaneseHolidays.holidayNameOf(LocalDate(2019, 10, 22)))
        assertEquals(JapaneseHolidays.CITIZENS_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2019, 4, 30)))
        assertNull(JapaneseHolidays.holidayNameOf(LocalDate(2019, 12, 23)))
        assertNull(JapaneseHolidays.holidayNameOf(LocalDate(2019, 2, 23)))
        assertEquals("スポーツの日", JapaneseHolidays.holidayNameOf(LocalDate(2020, 7, 24)))
        assertEquals("体育の日", JapaneseHolidays.holidayNameOf(LocalDate(2019, 10, 14)))
        assertEquals(JapaneseHolidays.SUBSTITUTE_HOLIDAY, JapaneseHolidays.holidayNameOf(LocalDate(2021, 8, 9)))
    }

    @Test
    fun `every supported year has the regular holidays`() {
        JapaneseHolidays.SUPPORTED_YEARS.forEach { year ->
            val holidays = JapaneseHolidays.holidaysOf(year)
            val equinoxes = holidays.filterValues { it == "春分の日" || it == "秋分の日" }.keys
            assertEquals(2, equinoxes.size, "$year")
            // 春分日は 3/19〜3/21、秋分日は 9/21〜9/24 の範囲 (1980〜2099 年の近似式の範囲)
            equinoxes.forEach { date ->
                val range = if (date.month.ordinal == 2) 19..21 else 21..24
                assertTrue(date.day in range, "$date")
            }
            // 振替休日は祝日 (振替休日以外) の翌日以降で、日曜日にならない
            holidays.filterValues { it == JapaneseHolidays.SUBSTITUTE_HOLIDAY }.keys.forEach { date ->
                assertTrue(date.dayOfWeek != DayOfWeek.SUNDAY, "$date")
                assertTrue(holidays.containsKey(date.plus(-1, DateTimeUnit.DAY)), "$date")
            }
            // 日付の順に並んでいる
            assertEquals(holidays.keys.sorted(), holidays.keys.toList(), "$year")
        }
    }

    @Test
    fun `equinox days of 2099 follow the formula`() {
        assertEquals("春分の日", JapaneseHolidays.holidayNameOf(LocalDate(2099, 3, 20)))
        assertEquals("秋分の日", JapaneseHolidays.holidayNameOf(LocalDate(2099, 9, 23)))
    }

    private fun officialNameOf(name: String): String =
        when (name) {
            JapaneseHolidays.SUBSTITUTE_HOLIDAY, JapaneseHolidays.CITIZENS_HOLIDAY -> "休日"
            else -> name
        }
}
