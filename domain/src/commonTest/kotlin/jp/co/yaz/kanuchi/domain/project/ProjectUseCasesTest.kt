package jp.co.yaz.kanuchi.domain.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProjectUseCasesTest {
    private val repository = FakeProjectRepository()

    @Test
    fun `projects are sorted with active ones first and then by name`() =
        runTest {
            repository.projectsResult =
                Result.success(
                    listOf(
                        Project(id = "1", name = "B", isActive = false),
                        Project(id = "2", name = "C", isActive = true),
                        Project(id = "3", name = "A", isActive = false),
                        Project(id = "4", name = "A", isActive = true),
                    ),
                )

            val projects = GetProjectsUseCase(repository)().getOrThrow()

            assertEquals(listOf("4", "2", "3", "1"), projects.map { it.id })
        }

    @Test
    fun `failure of getting projects is propagated as-is`() =
        runTest {
            repository.projectsResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(GetProjectsUseCase(repository)().exceptionOrNull())
        }

    @Test
    fun `trimmed name is added`() =
        runTest {
            val project = AddProjectUseCase(repository)("  案件B ").getOrThrow()

            assertEquals("案件B", repository.lastAddedName?.value)
            assertEquals("案件B", project.name)
        }

    @Test
    fun `blank name is rejected before reaching the repository on add`() =
        runTest {
            assertIs<BlankProjectNameException>(AddProjectUseCase(repository)("  ").exceptionOrNull())
            assertNull(repository.lastAddedName)
        }

    @Test
    fun `duplicate name failure is propagated on add`() =
        runTest {
            repository.addProjectResult = Result.failure(DuplicateProjectNameException())

            assertIs<DuplicateProjectNameException>(AddProjectUseCase(repository)("案件A").exceptionOrNull())
        }

    @Test
    fun `trimmed name is used on rename`() =
        runTest {
            RenameProjectUseCase(repository)("project-1", " 新しい名前 ").getOrThrow()

            assertEquals("project-1", repository.lastRenamed?.first)
            assertEquals("新しい名前", repository.lastRenamed?.second?.value)
        }

    @Test
    fun `blank name is rejected before reaching the repository on rename`() =
        runTest {
            assertIs<BlankProjectNameException>(RenameProjectUseCase(repository)("project-1", "").exceptionOrNull())
            assertNull(repository.lastRenamed)
        }

    @Test
    fun `active state is forwarded to the repository`() =
        runTest {
            val project = SetProjectActiveUseCase(repository)("project-1", false).getOrThrow()

            assertEquals("project-1" to false, repository.lastSetActive)
            assertEquals(false, project.isActive)
        }
}
