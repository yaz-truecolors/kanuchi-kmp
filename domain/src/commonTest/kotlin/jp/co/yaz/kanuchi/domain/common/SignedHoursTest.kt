package jp.co.yaz.kanuchi.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedHoursTest {
    @Test
    fun `hours subtraction can be negative`() {
        val result = Hours.ofHundredths(750) - Hours.ofHundredths(800)
        assertEquals(SignedHours.ofHundredths(-50), result)
        assertTrue(result.isNegative)
        assertFalse(result.isZero)
        assertFalse(result.isPositive)
        assertEquals(Hours.ofHundredths(50), result.absolute())
    }

    @Test
    fun `arithmetic and comparison`() {
        val a = SignedHours.ofHundredths(150)
        val b = SignedHours.ofHundredths(-225)
        assertEquals(SignedHours.ofHundredths(-75), a + b)
        assertEquals(SignedHours.ofHundredths(375), a - b)
        assertEquals(SignedHours.ofHundredths(225), -b)
        assertTrue(b < a)
        assertTrue((a + (-a)).isZero)
        assertEquals(SignedHours.ZERO, Hours.ZERO.toSignedHours())
    }

    @Test
    fun `toString shows the sign only when negative`() {
        assertEquals("1.5", SignedHours.ofHundredths(150).toString())
        assertEquals("-1.5", SignedHours.ofHundredths(-150).toString())
        assertEquals("-0.05", SignedHours.ofHundredths(-5).toString())
        assertEquals("0", SignedHours.ZERO.toString())
    }

    @Test
    fun `hours addition`() {
        assertEquals(Hours.ofHundredths(1_025), Hours.ofHundredths(750) + Hours.ofHundredths(275))
        assertEquals(Hours.ofHundredths(750), Hours.ofHundredths(750) + Hours.ZERO)
    }
}
