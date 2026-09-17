package jp.co.yaz.kanuchi.domain.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SendMagicLinkUseCaseTest {
    private class FakeAuthRepository : AuthRepository {
        var lastRequestedEmail: EmailAddress? = null
        var resultToReturn: Result<Unit> = Result.success(Unit)

        override suspend fun sendMagicLink(email: EmailAddress): Result<Unit> {
            lastRequestedEmail = email
            return resultToReturn
        }
    }

    @Test
    fun `valid email is forwarded to the repository`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SendMagicLinkUseCase(repository)

            val result = useCase("taro@example.com")

            assertTrue(result.isSuccess)
            assertEquals("taro@example.com", repository.lastRequestedEmail?.value)
        }

    @Test
    fun `invalid email is rejected before reaching the repository`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SendMagicLinkUseCase(repository)

            val result = useCase("not-an-email")

            assertTrue(result.isFailure)
            assertIs<IllegalArgumentException>(result.exceptionOrNull())
            assertFalse(repository.lastRequestedEmail != null)
        }

    @Test
    fun `repository failure is propagated`() =
        runTest {
            val repository =
                FakeAuthRepository().apply {
                    resultToReturn = Result.failure(RuntimeException("network error"))
                }
            val useCase = SendMagicLinkUseCase(repository)

            val result = useCase("taro@example.com")

            assertTrue(result.isFailure)
        }
}
