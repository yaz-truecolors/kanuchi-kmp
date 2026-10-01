package jp.co.yaz.kanuchi.presentation.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.GetProjectAssignmentUseCase
import jp.co.yaz.kanuchi.domain.project.SaveProjectAssignmentUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository.Companion.ADMIN
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository.Companion.MEMBER
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectMembersViewModelTest : MainDispatcherTest() {
    private val projectRepository = FakeProjectRepository()
    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() =
        ProjectMembersViewModel(
            projectId = FakeProjectRepository.ACTIVE.id,
            getProjectAssignmentUseCase = GetProjectAssignmentUseCase(projectRepository, profileRepository),
            saveProjectAssignmentUseCase = SaveProjectAssignmentUseCase(projectRepository),
        )

    @Test
    fun `project, users and assigned users are loaded`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(FakeProjectRepository.ACTIVE, state.project)
        assertEquals(listOf(MEMBER, ADMIN), state.users)
        assertEquals(setOf(MEMBER.id), state.selectedUserIds)
        assertFalse(state.hasChanges)
        assertFalse(state.isLoading)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        profileRepository.profilesResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)

        profileRepository.profilesResult = Result.success(listOf(MEMBER, ADMIN))
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(listOf(MEMBER, ADMIN), viewModel.uiState.value.users)
        assertEquals(2, projectRepository.getProjectCallCount)
    }

    @Test
    fun `checking users marks the selection as changed`() {
        val viewModel = createViewModel()

        viewModel.onUserCheckedChange(ADMIN.id, true)
        assertEquals(setOf(MEMBER.id, ADMIN.id), viewModel.uiState.value.selectedUserIds)
        assertTrue(viewModel.uiState.value.hasChanges)

        viewModel.onUserCheckedChange(ADMIN.id, false)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test
    fun `selected users are saved and the saved message is shown`() {
        val viewModel = createViewModel()
        val result = CompletableDeferred<Result<Unit>>()
        projectRepository.setAssignedUserIdsResult = result

        viewModel.onUserCheckedChange(MEMBER.id, false)
        viewModel.onUserCheckedChange(ADMIN.id, true)
        viewModel.onSaveClicked()

        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals(listOf(FakeProjectRepository.ACTIVE.id to setOf(ADMIN.id)), projectRepository.savedAssignments)

        result.complete(Result.success(Unit))

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.showsSavedMessage)
        assertFalse(viewModel.uiState.value.hasChanges)

        viewModel.onUserCheckedChange(MEMBER.id, true)
        assertFalse(viewModel.uiState.value.showsSavedMessage)
    }

    @Test
    fun `save failure is shown and the selection is kept for retry`() {
        val viewModel = createViewModel()
        projectRepository.setAssignedUserIdsResult = CompletableDeferred(Result.failure(GenericDataFailureException()))

        viewModel.onUserCheckedChange(ADMIN.id, true)
        viewModel.onSaveClicked()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertEquals(setOf(MEMBER.id, ADMIN.id), viewModel.uiState.value.selectedUserIds)
    }

    @Test
    fun `nothing is saved without changes`() {
        val viewModel = createViewModel()

        viewModel.onSaveClicked()

        assertTrue(projectRepository.savedAssignments.isEmpty())
    }

    @Test
    fun `selection cannot be changed and save is not requested twice while saving`() {
        val viewModel = createViewModel()
        projectRepository.setAssignedUserIdsResult = CompletableDeferred()

        viewModel.onUserCheckedChange(ADMIN.id, true)
        viewModel.onSaveClicked()
        viewModel.onUserCheckedChange(MEMBER.id, false)
        viewModel.onSaveClicked()

        assertEquals(1, projectRepository.savedAssignments.size)
        assertEquals(setOf(MEMBER.id, ADMIN.id), viewModel.uiState.value.selectedUserIds)
    }
}
