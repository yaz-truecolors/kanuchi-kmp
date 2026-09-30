package jp.co.yaz.kanuchi.domain.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SignOutUseCaseTest {
    @Test
    fun `sign out is delegated to the repository`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignOutUseCase(repository)

            val result = useCase()

            assertTrue(result.isSuccess)
            assertEquals(1, repository.signOutCallCount)
        }

    @Test
    fun `repository failure is propagated as-is`() =
        runTest {
            val repository = FakeAuthRepository().apply { signOutResult = Result.failure(GenericAuthFailureException()) }
            val useCase = SignOutUseCase(repository)

            val result = useCase()

            assertIs<GenericAuthFailureException>(result.exceptionOrNull())
        }
}
