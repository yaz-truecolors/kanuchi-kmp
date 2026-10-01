package jp.co.yaz.kanuchi.domain.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DisplayNameTest {
    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("山田 太郎", DisplayName.of(" 　山田 太郎\t").getOrThrow().value)
    }

    @Test
    fun `blank display name is rejected`() {
        listOf("", "   ", "　").forEach {
            val error = assertIs<InvalidDisplayNameException>(DisplayName.of(it).exceptionOrNull())
            assertEquals(DisplayNameViolation.BLANK, error.violation)
        }
    }

    @Test
    fun `display name up to the max length is accepted`() {
        val name = "あ".repeat(DisplayName.MAX_LENGTH)

        assertEquals(name, DisplayName.of(name).getOrThrow().value)
    }

    @Test
    fun `display name longer than the max length is rejected`() {
        val error = assertIs<InvalidDisplayNameException>(DisplayName.of("あ".repeat(DisplayName.MAX_LENGTH + 1)).exceptionOrNull())

        assertEquals(DisplayNameViolation.TOO_LONG, error.violation)
    }

    @Test
    fun `surrogate pairs are counted as one character`() {
        val name = "😀".repeat(DisplayName.MAX_LENGTH)

        assertEquals(name, DisplayName.of(name).getOrThrow().value)
    }
}
