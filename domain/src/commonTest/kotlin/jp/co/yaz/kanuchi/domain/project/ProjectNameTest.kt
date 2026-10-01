package jp.co.yaz.kanuchi.domain.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectNameTest {
    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("案件A", ProjectName.of("  案件A　\n").getOrThrow().value)
    }

    @Test
    fun `whitespace inside the name is kept`() {
        assertEquals("案件 A", ProjectName.of(" 案件 A ").getOrThrow().value)
    }

    @Test
    fun `blank name is rejected`() {
        assertIs<BlankProjectNameException>(ProjectName.of("").exceptionOrNull())
        assertIs<BlankProjectNameException>(ProjectName.of(" \t　").exceptionOrNull())
    }
}
