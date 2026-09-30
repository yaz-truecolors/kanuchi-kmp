package jp.co.yaz.kanuchi.presentation.home

import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.AuthenticatedUser
import jp.co.yaz.kanuchi.domain.auth.GenericAuthFailureException
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeAuthRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest : MainDispatcherTest() {
    private val repository =
        FakeAuthRepository().apply {
            authState.value = AuthState.SignedIn(AuthenticatedUser(id = "user-1", email = "taro@example.com"))
        }

    private fun createViewModel() = HomeViewModel(ObserveAuthStateUseCase(repository), SignOutUseCase(repository))

    @Test
    fun `email of the signed in user is shown`() {
        val viewModel = createViewModel()

        assertEquals("taro@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `email is kept when auth state becomes signed out or unknown`() {
        val viewModel = createViewModel()

        repository.authState.value = AuthState.Unknown
        repository.authState.value = AuthState.SignedOut

        assertEquals("taro@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `signing out state is kept after success until the screen is replaced`() {
        val viewModel = createViewModel()

        viewModel.onSignOutClicked()
        assertTrue(viewModel.uiState.value.isSigningOut)

        repository.signOutResult.complete(Result.success(Unit))

        assertTrue(viewModel.uiState.value.isSigningOut)
        assertFalse(viewModel.uiState.value.signOutFailed)
    }

    @Test
    fun `sign out failure is shown and the button is enabled again`() {
        val viewModel = createViewModel()

        viewModel.onSignOutClicked()
        repository.signOutResult.complete(Result.failure(GenericAuthFailureException()))

        assertFalse(viewModel.uiState.value.isSigningOut)
        assertTrue(viewModel.uiState.value.signOutFailed)
    }

    @Test
    fun `sign out is not requested twice while signing out`() {
        val viewModel = createViewModel()

        viewModel.onSignOutClicked()
        viewModel.onSignOutClicked()

        assertEquals(1, repository.signOutCallCount)
    }
}
