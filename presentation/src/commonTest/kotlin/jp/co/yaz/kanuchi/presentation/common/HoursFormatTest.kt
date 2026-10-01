package jp.co.yaz.kanuchi.presentation.common

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.work.AllocationRatio
import kotlin.test.Test
import kotlin.test.assertEquals

class HoursFormatTest {
    @Test
    fun `hours are shown with two decimal places`() {
        assertEquals("7.50", formatHours(Hours.ofHundredths(750)))
        assertEquals("0.05", formatHours(Hours.ofHundredths(5)))
        assertEquals("0.00", formatHours(Hours.ZERO))
        assertEquals("168.00", formatHours(Hours.ofHundredths(16_800)))
    }

    @Test
    fun `signed hours have a sign unless zero`() {
        assertEquals("+0.50", formatSignedHours(SignedHours.ofHundredths(50)))
        assertEquals("-1.25", formatSignedHours(SignedHours.ofHundredths(-125)))
        assertEquals("-0.05", formatSignedHours(SignedHours.ofHundredths(-5)))
        assertEquals("0.00", formatSignedHours(SignedHours.ZERO))
    }

    @Test
    fun `ratio is shown with one decimal place`() {
        assertEquals("33.3", formatRatio(AllocationRatio.ofTenthsOfPercent(333)))
        assertEquals("100.0", formatRatio(AllocationRatio.ofTenthsOfPercent(1_000)))
        assertEquals("-2.5", formatRatio(AllocationRatio.ofTenthsOfPercent(-25)))
    }
}
