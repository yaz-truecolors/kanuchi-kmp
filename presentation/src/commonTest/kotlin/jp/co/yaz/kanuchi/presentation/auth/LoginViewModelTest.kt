package jp.co.yaz.kanuchi.presentation.auth

import jp.co.yaz.kanuchi.domain.auth.ConsumeMagicLinkCallbackErrorUseCase
import jp.co.yaz.kanuchi.domain.auth.MagicLinkCallbackError
import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeAuthRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginViewModelTest : MainDispatcherTest() {
    private val repository = FakeAuthRepository()

    private fun createViewModel() = LoginViewModel(SendMagicLinkUseCase(repository), ConsumeMagicLinkCallbackErrorUseCase(repository))

    @Test
    fun `magic link callback error is shown when the screen opens`() {
        repository.magicLinkCallbackError = MagicLinkCallbackError.EXPIRED

        val viewModel = createViewModel()

        assertEquals(MagicLinkCallbackError.EXPIRED, viewModel.uiState.value.magicLinkCallbackError)
    }

    @Test
    fun `magic link callback error is shown only once`() {
        repository.magicLinkCallbackError = MagicLinkCallbackError.UNKNOWN
        createViewModel()

        val secondViewModel = createViewModel()

        assertNull(secondViewModel.uiState.value.magicLinkCallbackError)
    }

    @Test
    fun `magic link callback error is cleared when email is edited`() {
        repository.magicLinkCallbackError = MagicLinkCallbackError.EXPIRED
        val viewModel = createViewModel()

        viewModel.onEmailChanged("taro@example.com")

        assertNull(viewModel.uiState.value.magicLinkCallbackError)
    }
}
