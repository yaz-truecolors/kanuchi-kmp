package jp.co.yaz.kanuchi.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.user.AlreadyInvitedException
import jp.co.yaz.kanuchi.domain.user.GetUserManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.user.InvalidEmailAddressException
import jp.co.yaz.kanuchi.domain.user.InviteUserUseCase
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import jp.co.yaz.kanuchi.domain.user.ReactivateUserUseCase
import jp.co.yaz.kanuchi.domain.user.RevokeInvitationUseCase
import jp.co.yaz.kanuchi.domain.user.SuspendUserUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ユーザー管理画面の ViewModel。表示内容の読み込みと、招待・取り消し・利用停止・復帰を扱う。
 * 操作が成功したら、他の admin による変更も反映されるよう、表示内容を読み込み直す。
 */
class UsersViewModel(
    private val getUserManagementOverviewUseCase: GetUserManagementOverviewUseCase,
    private val inviteUserUseCase: InviteUserUseCase,
    private val revokeInvitationUseCase: RevokeInvitationUseCase,
    private val suspendUserUseCase: SuspendUserUseCase,
    private val reactivateUserUseCase: ReactivateUserUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetryClicked() {
        load()
    }

    fun onInviteEmailChanged(email: String) {
        _uiState.update { it.copy(inviteEmail = email, inviteError = null, invitedEmail = null) }
    }

    fun onInviteClicked() {
        val state = _uiState.value
        if (!state.canOperate || state.inviteEmail.isBlank()) return
        _uiState.update { it.copy(isSubmitting = true, inviteError = null, invitedEmail = null) }

        viewModelScope.launch {
            inviteUserUseCase(state.inviteEmail)
                .onSuccess {
                    // DB 側で前後の空白除去・小文字化して登録されるため、表示も合わせる
                    _uiState.update {
                        it.copy(isSubmitting = false, inviteEmail = "", invitedEmail = state.inviteEmail.trim().lowercase())
                    }
                    load()
                }.onFailure { e ->
                    val error =
                        when (e) {
                            is InvalidEmailAddressException -> InviteError.INVALID_EMAIL
                            is AlreadyInvitedException -> InviteError.ALREADY_INVITED
                            else -> InviteError.SAVE_FAILED
                        }
                    _uiState.update { it.copy(isSubmitting = false, inviteError = error) }
                }
        }
    }

    fun onRevokeInvitationClicked(email: String) {
        submit { revokeInvitationUseCase(email) }
    }

    /** 利用停止は影響が大きいため、確認ダイアログを表示してから実行する。 */
    fun onSuspendClicked(user: UserProfile) {
        if (!_uiState.value.canOperate) return
        _uiState.update { it.copy(suspendConfirmationTarget = user) }
    }

    fun onSuspendConfirmed() {
        val target = _uiState.value.suspendConfirmationTarget ?: return
        _uiState.update { it.copy(suspendConfirmationTarget = null) }
        submit { suspendUserUseCase(target.id) }
    }

    fun onSuspendDismissed() {
        _uiState.update { it.copy(suspendConfirmationTarget = null) }
    }

    fun onReactivateClicked(user: UserProfile) {
        submit { reactivateUserUseCase(user.id) }
    }

    fun onActionErrorDismissed() {
        _uiState.update { it.copy(actionError = null) }
    }

    private fun submit(action: suspend () -> Result<Unit>) {
        if (!_uiState.value.canOperate) return
        _uiState.update { it.copy(isSubmitting = true, actionError = null, invitedEmail = null) }

        viewModelScope.launch {
            action()
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    load()
                }.onFailure { e ->
                    val error =
                        when (e) {
                            is LastActiveAdminRequiredException -> UserActionError.LAST_ACTIVE_ADMIN_REQUIRED
                            else -> UserActionError.SAVE_FAILED
                        }
                    _uiState.update { it.copy(isSubmitting = false, actionError = error) }
                }
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        viewModelScope.launch {
            getUserManagementOverviewUseCase()
                .onSuccess { overview ->
                    _uiState.update { it.copy(overview = overview, isLoading = false) }
                }.onFailure {
                    // 古い内容のまま操作させないよう、表示中の内容も消す
                    _uiState.update { it.copy(overview = null, isLoading = false, loadFailed = true) }
                }
        }
    }
}
