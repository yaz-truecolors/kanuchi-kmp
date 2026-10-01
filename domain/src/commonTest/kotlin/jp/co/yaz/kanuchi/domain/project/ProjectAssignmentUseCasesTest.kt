package jp.co.yaz.kanuchi.domain.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectAssignmentUseCasesTest {
    private val projectRepository = FakeProjectRepository()
    private val profileRepository = FakeProfileRepository()

    @Test
    fun `project, users and assigned users are returned together`() =
        runTest {
            val assignment = GetProjectAssignmentUseCase(projectRepository, profileRepository)("project-1").getOrThrow()

            assertEquals(
                ProjectAssignment(
                    project = FakeProjectRepository.ACTIVE,
                    users = listOf(FakeProfileRepository.PROFILE),
                    assignedUserIds = setOf("user-1"),
                ),
                assignment,
            )
        }

    @Test
    fun `failure of any part is propagated`() =
        runTest {
            val useCase = GetProjectAssignmentUseCase(projectRepository, profileRepository)

            projectRepository.projectResult = Result.failure(GenericDataFailureException())
            assertIs<GenericDataFailureException>(useCase("project-1").exceptionOrNull())

            projectRepository.projectResult = Result.success(FakeProjectRepository.ACTIVE)
            profileRepository.profilesResult = Result.failure(GenericDataFailureException())
            assertIs<GenericDataFailureException>(useCase("project-1").exceptionOrNull())

            profileRepository.profilesResult = Result.success(listOf(FakeProfileRepository.PROFILE))
            projectRepository.assignedUserIdsResult = Result.failure(GenericDataFailureException())
            assertIs<GenericDataFailureException>(useCase("project-1").exceptionOrNull())
        }

    @Test
    fun `selected users are saved`() =
        runTest {
            SaveProjectAssignmentUseCase(projectRepository)("project-1", setOf("user-1", "user-2")).getOrThrow()

            assertEquals("project-1" to setOf("user-1", "user-2"), projectRepository.lastSetAssigned)
        }

    @Test
    fun `save failure is propagated`() =
        runTest {
            projectRepository.setAssignedUserIdsResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(
                SaveProjectAssignmentUseCase(projectRepository)("project-1", emptySet()).exceptionOrNull(),
            )
        }
}
