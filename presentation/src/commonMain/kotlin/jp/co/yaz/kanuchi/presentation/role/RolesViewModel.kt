package jp.co.yaz.kanuchi.presentation.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.role.ChangeUserRoleUseCase
import jp.co.yaz.kanuchi.domain.role.GetRoleManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.role.RoleManagementOverview
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RolesViewModel(
    private val getRoleManagementOverviewUseCase: GetRoleManagementOverviewUseCase,
    private val changeUserRoleUseCase: ChangeUserRoleUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RolesUiState())
    val uiState: StateFlow<RolesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetryClicked() {
        load()
    }

    /** 「管理者にする」「メンバーにする」が押されたとき。確認ダイアログを表示する。 */
    fun onChangeRoleClicked(
        userId: String,
        newRole: UserRole,
    ) {
        val state = _uiState.value
        val item = state.users.firstOrNull { it.profile.id == userId }?.takeIf { it.canChangeRole && it.profile.role != newRole }
        if (state.isSaving || item == null) return
        _uiState.update { it.copy(pendingChange = RoleChange(item.profile, newRole)) }
    }

    fun onChangeRoleDismissed() {
        _uiState.update { it.copy(pendingChange = null) }
    }

    fun onChangeRoleConfirmed() {
        val change = _uiState.value.pendingChange ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(pendingChange = null, savingChange = change, completedChange = null, saveError = null) }

        viewModelScope.launch {
            changeUserRoleUseCase(change.user.id, change.newRole)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            users =
                                state.users.map { item ->
                                    if (item.profile.id == change.user.id) {
                                        item.copy(profile = item.profile.copy(role = change.newRole))
                                    } else {
                                        item
                                    }
                                },
                            savingChange = null,
                            completedChange = change,
                        )
                    }
                }.onFailure { error ->
                    val isLastAdminError = error is LastActiveAdminRequiredException
                    _uiState.update {
                        it.copy(
                            savingChange = null,
                            saveError = if (isLastAdminError) RoleChangeError.LAST_ACTIVE_ADMIN_REQUIRED else RoleChangeError.GENERIC,
                        )
                    }
                    // 他の admin が先に降格・利用停止していた等で一覧が古くなっているため、最新の状態を読み込み直す
                    if (isLastAdminError) load()
                }
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        viewModelScope.launch {
            getRoleManagementOverviewUseCase()
                .onSuccess { overview ->
                    _uiState.update { it.copy(users = overview.toItems(), isLoading = false) }
                }.onFailure {
                    _uiState.update { it.copy(users = emptyList(), isLoading = false, loadFailed = true) }
                }
        }
    }

    private fun RoleManagementOverview.toItems(): List<RoleUserItem> =
        users.map { user ->
            RoleUserItem(profile = user, isCurrentUser = user.id == currentUserId, canChangeRole = canChangeRole(user))
        }
}
