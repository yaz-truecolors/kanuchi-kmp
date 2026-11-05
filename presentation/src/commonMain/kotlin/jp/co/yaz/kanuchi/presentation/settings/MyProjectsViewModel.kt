package jp.co.yaz.kanuchi.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.project.AddPersonalProjectUseCase
import jp.co.yaz.kanuchi.domain.project.BlankProjectNameException
import jp.co.yaz.kanuchi.domain.project.DeletePersonalProjectUseCase
import jp.co.yaz.kanuchi.domain.project.GetMyProjectsUseCase
import jp.co.yaz.kanuchi.domain.project.PersonalProjectInUseException
import jp.co.yaz.kanuchi.domain.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 案件の編集画面 (個人設定から開く) の ViewModel。ログイン中のユーザーが工数を記録する案件を表示し、
 * 個人の案件を追加・削除する。管理者が割り当てた共有の案件は表示するだけで、変更・削除はできない。
 */
class MyProjectsViewModel(
    private val getMyProjectsUseCase: GetMyProjectsUseCase,
    private val addPersonalProjectUseCase: AddPersonalProjectUseCase,
    private val deletePersonalProjectUseCase: DeletePersonalProjectUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyProjectsUiState())
    val uiState: StateFlow<MyProjectsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetryClicked() {
        load()
    }

    private fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        viewModelScope.launch {
            getMyProjectsUseCase()
                .onSuccess { myProjects ->
                    _uiState.update {
                        it.copy(
                            assignedProjects = myProjects.assignedProjects,
                            personalProjects = myProjects.personalProjects,
                            isLoading = false,
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
        }
    }

    fun onNewProjectNameChanged(name: String) {
        _uiState.update { it.copy(newProjectName = name, addError = null) }
    }

    fun onAddClicked() {
        val state = _uiState.value
        if (!state.canEdit) return
        _uiState.update { it.copy(isAdding = true, addError = null, deleteFailedProjectId = null, deleteError = null) }

        viewModelScope.launch {
            addPersonalProjectUseCase(state.newProjectName)
                .onSuccess { project ->
                    _uiState.update {
                        it.copy(
                            personalProjects = (it.personalProjects + project).sortedBy(Project::name),
                            newProjectName = "",
                            isAdding = false,
                        )
                    }
                }.onFailure { error ->
                    val addError =
                        if (error is BlankProjectNameException) PersonalProjectAddError.BLANK_NAME else PersonalProjectAddError.SAVE_FAILED
                    _uiState.update { it.copy(isAdding = false, addError = addError) }
                }
        }
    }

    fun onDeleteClicked(project: Project) {
        if (!_uiState.value.canEdit) return
        _uiState.update { it.copy(deleteTarget = project, deleteFailedProjectId = null, deleteError = null) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun onDeleteConfirmed() {
        val state = _uiState.value
        val target = state.deleteTarget ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(deleteTarget = null, deletingProjectId = target.id) }

        viewModelScope.launch {
            deletePersonalProjectUseCase(target.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            personalProjects =
                                it.personalProjects.filterNot { project ->
                                    project.id == target.id
                                },
                            deletingProjectId = null,
                        )
                    }
                }.onFailure { error ->
                    val deleteError =
                        if (error is PersonalProjectInUseException) {
                            PersonalProjectDeleteError.IN_USE
                        } else {
                            PersonalProjectDeleteError.DELETE_FAILED
                        }
                    _uiState.update { it.copy(deletingProjectId = null, deleteFailedProjectId = target.id, deleteError = deleteError) }
                }
        }
    }
}
