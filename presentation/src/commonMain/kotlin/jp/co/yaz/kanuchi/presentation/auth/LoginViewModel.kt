package jp.co.yaz.kanuchi.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.auth.GenericAuthFailureException
import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.login_generic_error_message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class LoginViewModel(
    private val sendMagicLinkUseCase: SendMagicLinkUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail, errorMessage = null, sentSuccessfully = false)
    }

    fun onSendMagicLinkClicked() {
        val currentState = _uiState.value
        if (currentState.isSending) return

        _uiState.value = currentState.copy(isSending = true, errorMessage = null, sentSuccessfully = false)

        viewModelScope.launch {
            sendMagicLinkUseCase(currentState.email)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSending = false, sentSuccessfully = true)
                }.onFailure { error ->
                    // GenericAuthFailureException はdata層がUI表示用の文言を持たないためのマーカー例外。
                    // それ以外はRepository実装が既に安全なメッセージに変換済み(AuthRepositoryのKDoc参照)。
                    val message =
                        if (error is GenericAuthFailureException) {
                            getString(Res.string.login_generic_error_message)
                        } else {
                            error.message ?: getString(Res.string.login_generic_error_message)
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            sentSuccessfully = false,
                            errorMessage = message,
                        )
                }
        }
    }
}
