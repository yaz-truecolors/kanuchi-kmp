package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AllocationRatioTest {
    private fun ratio(
        part: Int,
        whole: Int,
    ) = AllocationRatio.of(Hours.ofHundredths(part), Hours.ofHundredths(whole))

    @Test
    fun `ratio is rounded to tenths of a percent`() {
        assertEquals(AllocationRatio.ofTenthsOfPercent(500), ratio(400, 800))
        // 1/3 = 33.333…% → 33.3%、2/3 = 66.666…% → 66.7%
        assertEquals(AllocationRatio.ofTenthsOfPercent(333), ratio(100, 300))
        assertEquals(AllocationRatio.ofTenthsOfPercent(667), ratio(200, 300))
        // 0.05% ちょうどは切り上げ (1/2000)
        assertEquals(AllocationRatio.ofTenthsOfPercent(1), ratio(1, 2_000))
        assertEquals(AllocationRatio.ofTenthsOfPercent(0), ratio(0, 800))
        assertEquals(AllocationRatio.ofTenthsOfPercent(1_000), ratio(800, 800))
    }

    @Test
    fun `negative part gives a negative ratio rounded away from zero`() {
        assertEquals(AllocationRatio.ofTenthsOfPercent(-1), AllocationRatio.of(SignedHours.ofHundredths(-1), Hours.ofHundredths(2_000)))
        assertEquals(AllocationRatio.ofTenthsOfPercent(-125), AllocationRatio.of(SignedHours.ofHundredths(-100), Hours.ofHundredths(800)))
    }

    @Test
    fun `ratio is null when the whole is zero`() {
        assertNull(ratio(0, 0))
        assertNull(AllocationRatio.of(SignedHours.ofHundredths(-100), Hours.ZERO))
    }

    @Test
    fun `toString shows one decimal place`() {
        assertEquals("33.3", AllocationRatio.ofTenthsOfPercent(333).toString())
        assertEquals("100.0", AllocationRatio.ofTenthsOfPercent(1_000).toString())
        assertEquals("0.0", AllocationRatio.ofTenthsOfPercent(0).toString())
        assertEquals("-0.5", AllocationRatio.ofTenthsOfPercent(-5).toString())
    }
}
