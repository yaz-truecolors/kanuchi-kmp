package jp.co.yaz.kanuchi.presentation.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.AddProjectUseCase
import jp.co.yaz.kanuchi.domain.project.DuplicateProjectNameException
import jp.co.yaz.kanuchi.domain.project.GetProjectsUseCase
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.project.RenameProjectUseCase
import jp.co.yaz.kanuchi.domain.project.SetProjectActiveUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository.Companion.ACTIVE
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository.Companion.INACTIVE
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectsViewModelTest : MainDispatcherTest() {
    private val repository = FakeProjectRepository()

    private fun createViewModel() =
        ProjectsViewModel(
            GetProjectsUseCase(repository),
            AddProjectUseCase(repository),
            RenameProjectUseCase(repository),
            SetProjectActiveUseCase(repository),
        )

    @Test
    fun `projects are loaded with active ones first`() {
        val viewModel = createViewModel()

        assertEquals(listOf(ACTIVE, INACTIVE), viewModel.uiState.value.projects)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadFailed)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        repository.projectsResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)

        repository.projectsResult = Result.success(listOf(ACTIVE))
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(listOf(ACTIVE), viewModel.uiState.value.projects)
        assertEquals(2, repository.getProjectsCallCount)
    }

    @Test
    fun `added project is inserted in display order and the input is cleared`() {
        val viewModel = createViewModel()
        val result = CompletableDeferred<Result<Project>>()
        repository.addProjectResult = result

        viewModel.onNewProjectNameChanged("  案件C ")
        viewModel.onAddClicked()

        assertTrue(viewModel.uiState.value.isAdding)
        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals(listOf("案件C"), repository.addedNames)

        val added = Project(id = "project-3", name = "案件C", isActive = true)
        result.complete(Result.success(added))

        assertFalse(viewModel.uiState.value.isAdding)
        assertEquals("", viewModel.uiState.value.newProjectName)
        assertEquals(listOf(ACTIVE, added, INACTIVE), viewModel.uiState.value.projects)
    }

    @Test
    fun `duplicate name on add is shown and the input is kept`() {
        val viewModel = createViewModel()
        repository.addProjectResult = CompletableDeferred(Result.failure(DuplicateProjectNameException()))

        viewModel.onNewProjectNameChanged("案件B")
        viewModel.onAddClicked()

        assertEquals(ProjectNameSaveError.DUPLICATE_NAME, viewModel.uiState.value.addError)
        assertEquals("案件B", viewModel.uiState.value.newProjectName)
        assertFalse(viewModel.uiState.value.isAdding)
    }

    @Test
    fun `blank name on add is shown without saving`() {
        val viewModel = createViewModel()

        viewModel.onNewProjectNameChanged("   ")
        viewModel.onAddClicked()

        assertEquals(ProjectNameSaveError.BLANK_NAME, viewModel.uiState.value.addError)
        assertTrue(repository.addedNames.isEmpty())
    }

    @Test
    fun `other add failure is shown as save failure and cleared when the name is edited`() {
        val viewModel = createViewModel()
        repository.addProjectResult = CompletableDeferred(Result.failure(GenericDataFailureException()))

        viewModel.onNewProjectNameChanged("案件C")
        viewModel.onAddClicked()
        assertEquals(ProjectNameSaveError.SAVE_FAILED, viewModel.uiState.value.addError)

        viewModel.onNewProjectNameChanged("案件D")
        assertNull(viewModel.uiState.value.addError)
    }

    @Test
    fun `add is not requested twice while adding`() {
        val viewModel = createViewModel()
        repository.addProjectResult = CompletableDeferred()

        viewModel.onNewProjectNameChanged("案件C")
        viewModel.onAddClicked()
        viewModel.onAddClicked()

        assertEquals(1, repository.addedNames.size)
    }

    @Test
    fun `rename starts with the current name and saves the new name`() {
        val viewModel = createViewModel()
        val result = CompletableDeferred<Result<Project>>()
        repository.renameProjectResult = result

        viewModel.onRenameClicked(ACTIVE)
        assertEquals(ACTIVE.id, viewModel.uiState.value.renamingProjectId)
        assertEquals(ACTIVE.name, viewModel.uiState.value.renamingName)

        viewModel.onRenamingNameChanged("案件0")
        viewModel.onRenameSaveClicked()
        assertEquals(ACTIVE.id, viewModel.uiState.value.savingProjectId)
        assertEquals(listOf(ACTIVE.id to "案件0"), repository.renamed)

        result.complete(Result.success(ACTIVE.copy(name = "案件0")))

        assertNull(viewModel.uiState.value.renamingProjectId)
        assertNull(viewModel.uiState.value.savingProjectId)
        assertEquals(listOf(ACTIVE.copy(name = "案件0"), INACTIVE), viewModel.uiState.value.projects)
    }

    @Test
    fun `duplicate name on rename is shown and editing continues`() {
        val viewModel = createViewModel()
        repository.renameProjectResult = CompletableDeferred(Result.failure(DuplicateProjectNameException()))

        viewModel.onRenameClicked(ACTIVE)
        viewModel.onRenamingNameChanged(INACTIVE.name)
        viewModel.onRenameSaveClicked()

        assertEquals(ProjectNameSaveError.DUPLICATE_NAME, viewModel.uiState.value.renameError)
        assertEquals(ACTIVE.id, viewModel.uiState.value.renamingProjectId)
        assertEquals(INACTIVE.name, viewModel.uiState.value.renamingName)
        assertNull(viewModel.uiState.value.savingProjectId)
        assertEquals(listOf(ACTIVE, INACTIVE), viewModel.uiState.value.projects)
    }

    @Test
    fun `rename can be cancelled`() {
        val viewModel = createViewModel()

        viewModel.onRenameClicked(ACTIVE)
        viewModel.onRenamingNameChanged("案件0")
        viewModel.onRenameCancelled()

        assertNull(viewModel.uiState.value.renamingProjectId)
        assertTrue(repository.renamed.isEmpty())
        assertEquals(listOf(ACTIVE, INACTIVE), viewModel.uiState.value.projects)
    }

    @Test
    fun `rename cannot be cancelled while saving it`() {
        val viewModel = createViewModel()
        repository.renameProjectResult = CompletableDeferred()

        viewModel.onRenameClicked(ACTIVE)
        viewModel.onRenameSaveClicked()
        viewModel.onRenameCancelled()

        assertEquals(ACTIVE.id, viewModel.uiState.value.renamingProjectId)
    }

    @Test
    fun `deactivated project is moved after active ones`() {
        val viewModel = createViewModel()
        val result = CompletableDeferred<Result<Project>>()
        repository.setProjectActiveResult = result

        viewModel.onToggleActiveClicked(ACTIVE)
        assertEquals(ACTIVE.id, viewModel.uiState.value.savingProjectId)
        assertEquals(listOf(ACTIVE.id to false), repository.activeChanges)

        result.complete(Result.success(ACTIVE.copy(isActive = false)))

        assertNull(viewModel.uiState.value.savingProjectId)
        assertEquals(listOf(INACTIVE, ACTIVE.copy(isActive = false)), viewModel.uiState.value.projects)
    }

    @Test
    fun `inactive project can be activated`() {
        val viewModel = createViewModel()

        viewModel.onToggleActiveClicked(INACTIVE)

        assertEquals(listOf(INACTIVE.id to true), repository.activeChanges)
        assertEquals(listOf(INACTIVE.copy(isActive = true), ACTIVE), viewModel.uiState.value.projects)
    }

    @Test
    fun `active change failure is shown for the project`() {
        val viewModel = createViewModel()
        repository.setProjectActiveResult = CompletableDeferred(Result.failure(GenericDataFailureException()))

        viewModel.onToggleActiveClicked(ACTIVE)

        assertEquals(ACTIVE.id, viewModel.uiState.value.activeChangeFailedProjectId)
        assertNull(viewModel.uiState.value.savingProjectId)
        assertEquals(listOf(ACTIVE, INACTIVE), viewModel.uiState.value.projects)
    }

    @Test
    fun `other changes are not accepted while saving`() {
        val viewModel = createViewModel()
        repository.setProjectActiveResult = CompletableDeferred()

        viewModel.onToggleActiveClicked(ACTIVE)
        viewModel.onToggleActiveClicked(INACTIVE)
        viewModel.onRenameClicked(INACTIVE)
        viewModel.onNewProjectNameChanged("案件C")
        viewModel.onAddClicked()

        assertEquals(1, repository.activeChanges.size)
        assertNull(viewModel.uiState.value.renamingProjectId)
        assertTrue(repository.addedNames.isEmpty())
    }
}
