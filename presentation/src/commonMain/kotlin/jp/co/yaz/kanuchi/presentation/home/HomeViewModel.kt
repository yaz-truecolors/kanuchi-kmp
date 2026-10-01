package jp.co.yaz.kanuchi.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val getCurrentUserProfileUseCase: GetCurrentUserProfileUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var profileLoadJob: Job? = null

    init {
        viewModelScope.launch {
            observeAuthStateUseCase()
                .filterIsInstance<AuthState.SignedIn>()
                .collect { signedIn -> _uiState.update { it.copy(email = signedIn.user.email) } }
        }
    }

    /**
     * ホーム画面が表示されるたびに呼ぶ (初回の表示と、ホーム画面から開いた画面から戻ったとき)。
     * この ViewModel はホーム画面から開いた画面を表示している間も残るため、そこで変更された内容
     * (個人設定での表示名の変更等) を反映できるよう、表示されるたびにプロフィールを読み込み直す。
     */
    fun onScreenShown() {
        loadProfile()
    }

    fun onRetryProfileClicked() {
        loadProfile()
    }

    private fun loadProfile() {
        if (profileLoadJob?.isActive == true) return
        // 読み込み済みの場合 (他の画面から戻ったとき) は、読み込み中の表示を出さずに読み込み直す
        val isReload = _uiState.value.profile != null
        if (!isReload) {
            _uiState.update { it.copy(isLoadingProfile = true, profileLoadFailed = false) }
        }

        profileLoadJob =
            viewModelScope.launch {
                getCurrentUserProfileUseCase()
                    .onSuccess { profile ->
                        _uiState.update { it.copy(profile = profile, isLoadingProfile = false, profileLoadFailed = false) }
                    }.onFailure {
                        // 読み込み直しに失敗した場合は、前に読み込んだプロフィールの表示を続ける
                        if (!isReload) {
                            _uiState.update { it.copy(isLoadingProfile = false, profileLoadFailed = true) }
                        }
                    }
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
