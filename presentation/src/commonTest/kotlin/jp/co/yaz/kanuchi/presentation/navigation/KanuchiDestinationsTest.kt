package jp.co.yaz.kanuchi.presentation.navigation

import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class KanuchiDestinationsTest {
    @Test
    fun `summary route omits arguments that are not given`() {
        assertEquals("summary", KanuchiDestinations.summaryOf())
        assertEquals("summary?userId=user-1", KanuchiDestinations.summaryOf(userId = "user-1"))
        assertEquals("summary?yearMonth=2026-10", KanuchiDestinations.summaryOf(yearMonth = YearMonth(2026, 10)))
        assertEquals(
            "summary?userId=user-1&yearMonth=2027-01",
            KanuchiDestinations.summaryOf(userId = "user-1", yearMonth = YearMonth(2027, 1)),
        )
    }

    @Test
    fun `year month argument can be parsed back`() {
        assertEquals(YearMonth(2027, 1), YearMonth.parse("2027-01"))
    }
}
