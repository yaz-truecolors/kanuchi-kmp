package jp.co.yaz.kanuchi.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
                    _uiState.value =
                        _uiState.value.copy(
                            isSending = false,
                            sentSuccessfully = false,
                            errorMessage = error.message ?: "マジックリンクの送信に失敗しました",
                        )
                }
        }
    }
}
