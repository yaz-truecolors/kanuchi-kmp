package jp.co.yaz.kanuchi.domain.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveAuthStateUseCaseTest {
    @Test
    fun `current auth state from the repository is emitted`() =
        runTest {
            val user = AuthenticatedUser(id = "user-1", email = "taro@example.com")
            val repository = FakeAuthRepository().apply { authState.value = AuthState.SignedIn(user) }
            val useCase = ObserveAuthStateUseCase(repository)

            assertEquals(AuthState.SignedIn(user), useCase().first())
        }
}
