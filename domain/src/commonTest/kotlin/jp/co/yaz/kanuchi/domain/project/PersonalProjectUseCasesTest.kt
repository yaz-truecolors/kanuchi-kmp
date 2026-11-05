package jp.co.yaz.kanuchi.domain.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class PersonalProjectUseCasesTest {
    private val repository = FakeProjectRepository()
    private val profileRepository = FakeProfileRepository()
    private val userId = FakeProfileRepository.PROFILE.id

    @Test
    fun `shared projects exclude personal projects and are sorted`() =
        runTest {
            repository.projectsResult =
                Result.success(
                    listOf(
                        Project(id = "1", name = "B", isActive = false),
                        Project(id = "2", name = "A", isActive = true, ownerId = userId),
                        Project(id = "3", name = "C", isActive = true),
                    ),
                )

            val projects = GetSharedProjectsUseCase(repository)().getOrThrow()

            assertEquals(listOf("3", "1"), projects.map { it.id })
        }

    @Test
    fun `my projects contain assigned active shared projects and own personal projects that are not deleted`() =
        runTest {
            repository.projectsResult =
                Result.success(
                    listOf(
                        Project(id = "shared-b", name = "B", isActive = true),
                        Project(id = "shared-a", name = "A", isActive = true),
                        Project(id = "shared-inactive", name = "C", isActive = false),
                        Project(id = "shared-not-assigned", name = "D", isActive = true),
                        Project(id = "own-y", name = "Y", isActive = true, ownerId = userId),
                        Project(id = "own-x", name = "X", isActive = true, ownerId = userId),
                        Project(id = "own-deleted", name = "W", isActive = true, ownerId = userId, isDeleted = true),
                        // admin は他のユーザーの個人案件も参照できる
                        Project(id = "others", name = "V", isActive = true, ownerId = "user-2"),
                    ),
                )
            repository.assignedProjectIdsOfCurrentUserResult = Result.success(setOf("shared-b", "shared-a", "shared-inactive"))

            val myProjects = GetMyProjectsUseCase(repository, profileRepository)().getOrThrow()

            assertEquals(listOf("shared-a", "shared-b"), myProjects.assignedProjects.map { it.id })
            assertEquals(listOf("own-x", "own-y"), myProjects.personalProjects.map { it.id })
        }

    @Test
    fun `my projects return the failure of any repository`() =
        runTest {
            val useCase = GetMyProjectsUseCase(repository, profileRepository)
            val assignedError = IllegalStateException("assigned")
            repository.assignedProjectIdsOfCurrentUserResult = Result.failure(assignedError)
            assertSame(assignedError, useCase().exceptionOrNull())

            val projectsError = IllegalStateException("projects")
            repository.projectsResult = Result.failure(projectsError)
            assertSame(projectsError, useCase().exceptionOrNull())

            val profileError = IllegalStateException("profile")
            profileRepository.currentUserProfileResult = Result.failure(profileError)
            assertSame(profileError, useCase().exceptionOrNull())
        }

    @Test
    fun `trimmed name is added as a personal project`() =
        runTest {
            val project = AddPersonalProjectUseCase(repository)("  個人案件 ").getOrThrow()

            assertEquals("個人案件", repository.lastAddedPersonalName?.value)
            assertEquals("個人案件", project.name)
            assertNull(repository.lastAddedName)
        }

    @Test
    fun `blank name is rejected before reaching the repository on adding a personal project`() =
        runTest {
            assertIs<BlankProjectNameException>(AddPersonalProjectUseCase(repository)(" ").exceptionOrNull())
            assertNull(repository.lastAddedPersonalName)
        }

    @Test
    fun `personal project is deleted`() =
        runTest {
            DeletePersonalProjectUseCase(repository)("own").getOrThrow()

            assertEquals("own", repository.lastDeletedPersonalProjectId)
        }

    @Test
    fun `failure of deleting a personal project is propagated as-is`() =
        runTest {
            repository.deletePersonalProjectResult = Result.failure(PersonalProjectInUseException())
            assertIs<PersonalProjectInUseException>(DeletePersonalProjectUseCase(repository)("own").exceptionOrNull())

            repository.deletePersonalProjectResult = Result.failure(GenericDataFailureException())
            assertIs<GenericDataFailureException>(DeletePersonalProjectUseCase(repository)("own").exceptionOrNull())
        }
}
