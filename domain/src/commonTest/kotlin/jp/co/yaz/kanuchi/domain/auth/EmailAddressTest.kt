package jp.co.yaz.kanuchi.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailAddressTest {
    @Test
    fun `valid email is accepted`() {
        val result = EmailAddress.of("taro@example.com")

        assertTrue(result.isSuccess)
        assertEquals("taro@example.com", result.getOrThrow().value)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val result = EmailAddress.of("  taro@example.com  ")

        assertEquals("taro@example.com", result.getOrThrow().value)
    }

    @Test
    fun `missing at sign is rejected`() {
        val result = EmailAddress.of("taro.example.com")

        assertTrue(result.isFailure)
    }

    @Test
    fun `missing domain is rejected`() {
        val result = EmailAddress.of("taro@")

        assertTrue(result.isFailure)
    }

    @Test
    fun `blank string is rejected`() {
        val result = EmailAddress.of("   ")

        assertTrue(result.isFailure)
    }
}
