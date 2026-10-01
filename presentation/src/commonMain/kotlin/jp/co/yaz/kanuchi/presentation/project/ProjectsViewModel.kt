package jp.co.yaz.kanuchi.presentation.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.yaz.kanuchi.domain.project.AddProjectUseCase
import jp.co.yaz.kanuchi.domain.project.BlankProjectNameException
import jp.co.yaz.kanuchi.domain.project.DuplicateProjectNameException
import jp.co.yaz.kanuchi.domain.project.GetProjectsUseCase
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.project.RenameProjectUseCase
import jp.co.yaz.kanuchi.domain.project.SetProjectActiveUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 案件管理画面の ViewModel。案件の一覧・追加・名前変更・有効／無効の切り替え。
 * 案件は削除できない (使わなくなった案件は無効にする)。
 */
class ProjectsViewModel(
    private val getProjectsUseCase: GetProjectsUseCase,
    private val addProjectUseCase: AddProjectUseCase,
    private val renameProjectUseCase: RenameProjectUseCase,
    private val setProjectActiveUseCase: SetProjectActiveUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun onRetryClicked() {
        loadProjects()
    }

    private fun loadProjects() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        viewModelScope.launch {
            getProjectsUseCase()
                .onSuccess { projects ->
                    _uiState.update { it.copy(projects = projects, isLoading = false) }
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
        if (state.isSaving) return
        _uiState.update { it.copy(isAdding = true, addError = null) }

        viewModelScope.launch {
            addProjectUseCase(state.newProjectName)
                .onSuccess { project ->
                    _uiState.update {
                        it.copy(
                            projects = (it.projects + project).sortedWith(Project.DISPLAY_ORDER),
                            newProjectName = "",
                            isAdding = false,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(isAdding = false, addError = error.toNameSaveError()) }
                }
        }
    }

    fun onRenameClicked(project: Project) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(renamingProjectId = project.id, renamingName = project.name, renameError = null)
        }
    }

    fun onRenamingNameChanged(name: String) {
        _uiState.update { it.copy(renamingName = name, renameError = null) }
    }

    fun onRenameCancelled() {
        val state = _uiState.value
        if (state.savingProjectId != null && state.savingProjectId == state.renamingProjectId) return
        _uiState.update { it.copy(renamingProjectId = null, renamingName = "", renameError = null) }
    }

    fun onRenameSaveClicked() {
        val state = _uiState.value
        val projectId = state.renamingProjectId ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(savingProjectId = projectId, renameError = null) }

        viewModelScope.launch {
            renameProjectUseCase(projectId, state.renamingName)
                .onSuccess { project ->
                    _uiState.update {
                        it.copy(
                            projects = it.projects.replaced(project),
                            renamingProjectId = null,
                            renamingName = "",
                            savingProjectId = null,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(savingProjectId = null, renameError = error.toNameSaveError()) }
                }
        }
    }

    fun onToggleActiveClicked(project: Project) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(savingProjectId = project.id, activeChangeFailedProjectId = null) }

        viewModelScope.launch {
            setProjectActiveUseCase(project.id, !project.isActive)
                .onSuccess { updated ->
                    _uiState.update { it.copy(projects = it.projects.replaced(updated), savingProjectId = null) }
                }.onFailure {
                    _uiState.update { it.copy(savingProjectId = null, activeChangeFailedProjectId = project.id) }
                }
        }
    }
}

/** 同じIDの案件を [project] に置き換え、表示順に並べ直す。 */
private fun List<Project>.replaced(project: Project): List<Project> =
    map { if (it.id == project.id) project else it }.sortedWith(Project.DISPLAY_ORDER)

private fun Throwable.toNameSaveError(): ProjectNameSaveError =
    when (this) {
        is BlankProjectNameException -> ProjectNameSaveError.BLANK_NAME
        is DuplicateProjectNameException -> ProjectNameSaveError.DUPLICATE_NAME
        else -> ProjectNameSaveError.SAVE_FAILED
    }
