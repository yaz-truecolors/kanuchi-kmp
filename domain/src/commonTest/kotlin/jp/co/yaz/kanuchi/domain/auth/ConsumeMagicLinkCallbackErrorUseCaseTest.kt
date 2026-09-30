package jp.co.yaz.kanuchi.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsumeMagicLinkCallbackErrorUseCaseTest {
    @Test
    fun `error from the repository is returned`() {
        val repository = FakeAuthRepository().apply { magicLinkCallbackError = MagicLinkCallbackError.EXPIRED }
        val useCase = ConsumeMagicLinkCallbackErrorUseCase(repository)

        assertEquals(MagicLinkCallbackError.EXPIRED, useCase())
    }

    @Test
    fun `null is returned when there is no error`() {
        val useCase = ConsumeMagicLinkCallbackErrorUseCase(FakeAuthRepository())

        assertNull(useCase())
    }
}
