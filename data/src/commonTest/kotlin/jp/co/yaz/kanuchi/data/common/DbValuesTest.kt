package jp.co.yaz.kanuchi.data.common

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DbValuesTest {
    @Test
    fun `db time is parsed and seconds are truncated`() {
        assertEquals(TimeOfDay.of(9, 30), parseDbTime("09:30:00"))
        assertEquals(TimeOfDay.of(23, 59), parseDbTime("23:59:59.999"))
        assertFailsWith<IllegalArgumentException> { parseDbTime("0930") }
    }

    @Test
    fun `numeric hours are converted with rounding`() {
        assertEquals(Hours.ofHundredths(30), hoursOf(0.1 + 0.2))
        assertEquals(Hours.ofHundredths(12_050), hoursOf(120.5))
        assertEquals(7.75, Hours.ofHundredths(775).toDbValue())
        assertEquals(0.0, Hours.ZERO.toDbValue())
    }
}
