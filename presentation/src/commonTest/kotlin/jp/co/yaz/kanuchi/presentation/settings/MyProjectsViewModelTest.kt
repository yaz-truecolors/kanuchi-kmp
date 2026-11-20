package jp.co.yaz.kanuchi.presentation.settings

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.AddPersonalProjectUseCase
import jp.co.yaz.kanuchi.domain.project.DeletePersonalProjectUseCase
import jp.co.yaz.kanuchi.domain.project.GetMyProjectsUseCase
import jp.co.yaz.kanuchi.domain.project.PersonalProjectInUseException
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository.Companion.ACTIVE
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository.Companion.INACTIVE
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository.Companion.PERSONAL
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MyProjectsViewModelTest : MainDispatcherTest() {
    private val repository = FakeProjectRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() =
        MyProjectsViewModel(
            GetMyProjectsUseCase(repository, profileRepository),
            AddPersonalProjectUseCase(repository),
            DeletePersonalProjectUseCase(repository),
        )

    @Test
    fun `assigned shared projects and own personal projects are loaded`() {
        repository.projectsResult = Result.success(listOf(ACTIVE, INACTIVE, PERSONAL))
        repository.assignedProjectIdsOfCurrentUserResult = Result.success(setOf(ACTIVE.id, INACTIVE.id))

        val state = createViewModel().uiState.value

        assertEquals(listOf(ACTIVE), state.assignedProjects)
        assertEquals(listOf(PERSONAL), state.personalProjects)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        repository.projectsResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.canEdit)

        repository.projectsResult = Result.success(listOf(PERSONAL))
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(listOf(PERSONAL), viewModel.uiState.value.personalProjects)
    }

    @Test
    fun `personal project is added and listed in name order`() {
        repository.projectsResult = Result.success(listOf(PERSONAL))
        val deferred = CompletableDeferred<Result<Project>>()
        repository.addPersonalProjectResult = deferred
        val viewModel = createViewModel()

        viewModel.onNewProjectNameChanged(" A案件 ")
        viewModel.onAddClicked()

        assertTrue(viewModel.uiState.value.isAdding)
        assertFalse(viewModel.uiState.value.canEdit)
        assertEquals(listOf("A案件"), repository.addedPersonalNames)

        val added = Project(id = "added", name = "A案件", isActive = true, ownerId = FakeProfileRepository.MEMBER.id)
        deferred.complete(Result.success(added))

        val state = viewModel.uiState.value
        assertFalse(state.isAdding)
        assertEquals("", state.newProjectName)
        assertEquals(listOf(added, PERSONAL), state.personalProjects)
    }

    @Test
    fun `blank name is shown as an error without saving`() {
        val viewModel = createViewModel()

        viewModel.onNewProjectNameChanged("  ")
        viewModel.onAddClicked()

        assertEquals(PersonalProjectAddError.BLANK_NAME, viewModel.uiState.value.addError)
        assertTrue(repository.addedPersonalNames.isEmpty())

        viewModel.onNewProjectNameChanged("案件")
        assertNull(viewModel.uiState.value.addError)
    }

    @Test
    fun `add failure is shown and the input is kept`() {
        repository.addPersonalProjectResult = CompletableDeferred(Result.failure(GenericDataFailureException()))
        val viewModel = createViewModel()

        viewModel.onNewProjectNameChanged("案件")
        viewModel.onAddClicked()

        assertEquals(PersonalProjectAddError.SAVE_FAILED, viewModel.uiState.value.addError)
        assertEquals("案件", viewModel.uiState.value.newProjectName)
    }

    @Test
    fun `personal project is deleted after confirmation`() {
        repository.projectsResult = Result.success(listOf(PERSONAL))
        val deferred = CompletableDeferred<Result<Unit>>()
        repository.deletePersonalProjectResult = deferred
        val viewModel = createViewModel()

        viewModel.onDeleteClicked(PERSONAL)
        assertEquals(PERSONAL, viewModel.uiState.value.deleteTarget)
        assertTrue(repository.deletedPersonalProjectIds.isEmpty())

        viewModel.onDeleteConfirmed()
        assertNull(viewModel.uiState.value.deleteTarget)
        assertEquals(PERSONAL.id, viewModel.uiState.value.deletingProjectId)
        assertEquals(listOf(PERSONAL.id), repository.deletedPersonalProjectIds)

        deferred.complete(Result.success(Unit))

        assertNull(viewModel.uiState.value.deletingProjectId)
        assertEquals(emptyList(), viewModel.uiState.value.personalProjects)
    }

    @Test
    fun `dismissing the confirmation does not delete`() {
        repository.projectsResult = Result.success(listOf(PERSONAL))
        val viewModel = createViewModel()

        viewModel.onDeleteClicked(PERSONAL)
        viewModel.onDeleteDismissed()

        assertNull(viewModel.uiState.value.deleteTarget)
        assertTrue(repository.deletedPersonalProjectIds.isEmpty())
        assertEquals(listOf(PERSONAL), viewModel.uiState.value.personalProjects)
    }

    @Test
    fun `project in use this month is not deleted and the reason is shown`() {
        repository.projectsResult = Result.success(listOf(PERSONAL))
        repository.deletePersonalProjectResult = CompletableDeferred(Result.failure(PersonalProjectInUseException()))
        val viewModel = createViewModel()

        viewModel.onDeleteClicked(PERSONAL)
        viewModel.onDeleteConfirmed()

        val state = viewModel.uiState.value
        assertEquals(PERSONAL.id, state.deleteFailedProjectId)
        assertEquals(PersonalProjectDeleteError.IN_USE, state.deleteError)
        assertEquals(listOf(PERSONAL), state.personalProjects)
    }

    @Test
    fun `other delete failure is shown as a generic error`() {
        repository.projectsResult = Result.success(listOf(PERSONAL))
        repository.deletePersonalProjectResult = CompletableDeferred(Result.failure(GenericDataFailureException()))
        val viewModel = createViewModel()

        viewModel.onDeleteClicked(PERSONAL)
        viewModel.onDeleteConfirmed()

        assertEquals(PersonalProjectDeleteError.DELETE_FAILED, viewModel.uiState.value.deleteError)

        // もう一度削除しようとすると、前のエラーは消える
        viewModel.onDeleteClicked(PERSONAL)
        assertNull(viewModel.uiState.value.deleteError)
    }
}
