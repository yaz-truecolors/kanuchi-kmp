package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class GetTodayUseCaseTest {
    @Test
    fun `today is the date in Japan time`() {
        // UTC 14:59 は日本時間 23:59 (同じ日)、UTC 15:00 は日本時間の翌日 0:00
        assertEquals(LocalDate(2026, 10, 1), GetTodayUseCase(FixedClock("2026-10-01T14:59:59Z"))())
        assertEquals(LocalDate(2026, 10, 2), GetTodayUseCase(FixedClock("2026-10-01T15:00:00Z"))())
    }
}

/** テスト用の固定の時計。 */
class FixedClock(
    instant: String,
) : Clock {
    private val instant = Instant.parse(instant)

    override fun now(): Instant = instant
}
