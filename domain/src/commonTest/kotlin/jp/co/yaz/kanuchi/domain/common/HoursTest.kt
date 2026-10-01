package jp.co.yaz.kanuchi.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoursTest {
    @Test
    fun `decimal text with up to two fraction digits is parsed`() {
        assertEquals(Hours.ofHundredths(0), Hours.parse("0"))
        assertEquals(Hours.ofHundredths(100), Hours.parse("1"))
        assertEquals(Hours.ofHundredths(150), Hours.parse("1.5"))
        assertEquals(Hours.ofHundredths(125), Hours.parse("1.25"))
        assertEquals(Hours.ofHundredths(5), Hours.parse("0.05"))
        assertEquals(Hours.ofHundredths(14_000), Hours.parse(" 140 "))
    }

    @Test
    fun `invalid text is not parsed`() {
        listOf("", "-1", "1.234", ".5", "1.", "1,5", "abc", "１", "12345678").forEach {
            assertNull(Hours.parse(it), it)
        }
    }

    @Test
    fun `negative hours are rejected`() {
        assertFailsWith<IllegalArgumentException> { Hours.ofHundredths(-1) }
    }

    @Test
    fun `hours are formatted without trailing zeros and compared by value`() {
        assertEquals("1", Hours.ofHundredths(100).toString())
        assertEquals("1.5", Hours.ofHundredths(150).toString())
        assertEquals("1.25", Hours.ofHundredths(125).toString())
        assertEquals("0.05", Hours.ofHundredths(5).toString())
        assertEquals("0", Hours.ofHundredths(0).toString())
        assertTrue(Hours.ofHundredths(150) > Hours.ofHundredths(100))
    }
}
