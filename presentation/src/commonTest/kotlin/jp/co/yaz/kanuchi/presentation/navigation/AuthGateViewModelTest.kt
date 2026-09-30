package jp.co.yaz.kanuchi.presentation.navigation

import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.AuthenticatedUser
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeAuthRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthGateViewModelTest : MainDispatcherTest() {
    @Test
    fun `auth state follows the repository`() {
        val repository = FakeAuthRepository()
        val viewModel = AuthGateViewModel(ObserveAuthStateUseCase(repository))

        assertEquals(AuthState.Unknown, viewModel.authState.value)

        val signedIn = AuthState.SignedIn(AuthenticatedUser(id = "user-1", email = "taro@example.com"))
        repository.authState.value = signedIn
        assertEquals(signedIn, viewModel.authState.value)

        repository.authState.value = AuthState.SignedOut
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
    }
}
