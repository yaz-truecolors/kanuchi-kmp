package jp.co.yaz.kanuchi.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAuthStateUseCase()
                .filterIsInstance<AuthState.SignedIn>()
                .collect { signedIn -> _uiState.update { it.copy(email = signedIn.user.email) } }
        }
    }

    fun onSignOutClicked() {
        if (_uiState.value.isSigningOut) return
        _uiState.update { it.copy(isSigningOut = true, signOutFailed = false) }

        viewModelScope.launch {
            // 成功時はログイン状態が SignedOut に変わり、KanuchiNavHost がログイン画面へ切り替える
            // (この ViewModel も破棄される)。それまでの間に二重に押されないよう isSigningOut は戻さない。
            signOutUseCase().onFailure {
                _uiState.update { it.copy(isSigningOut = false, signOutFailed = true) }
            }
        }
    }
}
