package jp.co.yaz.kanuchi.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeOfDayTest {
    @Test
    fun `hh mm text is parsed`() {
        assertEquals(TimeOfDay.of(9, 30), TimeOfDay.parse("09:30"))
        assertEquals(TimeOfDay.of(9, 30), TimeOfDay.parse("9:30"))
        assertEquals(TimeOfDay.of(0, 0), TimeOfDay.parse("00:00"))
        assertEquals(TimeOfDay.of(23, 59), TimeOfDay.parse(" 23:59 "))
    }

    @Test
    fun `invalid text is not parsed`() {
        listOf("", "930", "09:3", "09:300", "24:00", "12:60", "-1:00", "09:30:00", "９:30", "a:bc").forEach {
            assertNull(TimeOfDay.parse(it), it)
        }
    }

    @Test
    fun `out of range hour or minute is rejected`() {
        assertFailsWith<IllegalArgumentException> { TimeOfDay.of(24, 0) }
        assertFailsWith<IllegalArgumentException> { TimeOfDay.of(0, 60) }
        assertFailsWith<IllegalArgumentException> { TimeOfDay.of(-1, 0) }
    }

    @Test
    fun `time is formatted as hh mm and compared chronologically`() {
        assertEquals("09:05", TimeOfDay.of(9, 5).toString())
        assertEquals(9, TimeOfDay.of(9, 5).hour)
        assertEquals(5, TimeOfDay.of(9, 5).minute)
        assertTrue(TimeOfDay.of(9, 30) < TimeOfDay.of(18, 30))
    }
}
