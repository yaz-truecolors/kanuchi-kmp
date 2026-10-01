package jp.co.yaz.kanuchi.presentation.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.project.GetProjectAssignmentUseCase
import jp.co.yaz.kanuchi.domain.project.SaveProjectAssignmentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 案件の担当メンバー画面の ViewModel。全ユーザーから担当メンバーを選び、まとめて保存する。
 *
 * @param projectId 対象の案件のID (画面のルートの引数)
 */
class ProjectMembersViewModel(
    private val projectId: String,
    private val getProjectAssignmentUseCase: GetProjectAssignmentUseCase,
    private val saveProjectAssignmentUseCase: SaveProjectAssignmentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectMembersUiState())
    val uiState: StateFlow<ProjectMembersUiState> = _uiState.asStateFlow()

    init {
        loadAssignment()
    }

    fun onRetryClicked() {
        loadAssignment()
    }

    private fun loadAssignment() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        viewModelScope.launch {
            getProjectAssignmentUseCase(projectId)
                .onSuccess { assignment ->
                    _uiState.update {
                        it.copy(
                            project = assignment.project,
                            users = assignment.users,
                            selectedUserIds = assignment.assignedUserIds,
                            savedUserIds = assignment.assignedUserIds,
                            isLoading = false,
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
        }
    }

    fun onUserCheckedChange(
        userId: String,
        checked: Boolean,
    ) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                selectedUserIds = if (checked) it.selectedUserIds + userId else it.selectedUserIds - userId,
                saveFailed = false,
                showsSavedMessage = false,
            )
        }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSaving || !state.hasChanges) return
        val userIds = state.selectedUserIds
        _uiState.update { it.copy(isSaving = true, saveFailed = false, showsSavedMessage = false) }

        viewModelScope.launch {
            saveProjectAssignmentUseCase(projectId, userIds)
                .onSuccess {
                    _uiState.update { it.copy(savedUserIds = userIds, isSaving = false, showsSavedMessage = true) }
                }.onFailure {
                    _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                }
        }
    }
}
