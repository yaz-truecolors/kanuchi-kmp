package jp.co.yaz.kanuchi.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ログイン状態を監視し、[KanuchiNavHost] が表示する画面を決めるための ViewModel。
 */
class AuthGateViewModel(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> =
        observeAuthStateUseCase()
            .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Unknown)
}
