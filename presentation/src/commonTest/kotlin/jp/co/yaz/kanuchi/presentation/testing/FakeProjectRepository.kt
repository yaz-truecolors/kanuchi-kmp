package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.project.ProjectName
import jp.co.yaz.kanuchi.domain.project.ProjectRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * ViewModel のテスト用の [ProjectRepository] の偽物。
 *
 * 変更系の結果は [CompletableDeferred] で、complete するまで完了しない (保存中の状態をテストするため)。
 * 結果を指定しない (null のまま) 場合は、渡された値どおりに変更できたものとして即座に返す。
 */
internal class FakeProjectRepository : ProjectRepository {
    var projectsResult: Result<List<Project>> = Result.success(listOf(ACTIVE, INACTIVE))
    var projectResult: Result<Project> = Result.success(ACTIVE)
    var assignedUserIdsResult: Result<Set<String>> = Result.success(setOf(FakeProfileRepository.MEMBER.id))

    /** 設定すると、一覧の取得はこれが完了するまで待つ (読み込み中の状態を確かめる用)。 */
    var getProjectsResult: CompletableDeferred<Result<List<Project>>>? = null
    var addProjectResult: CompletableDeferred<Result<Project>>? = null
    var renameProjectResult: CompletableDeferred<Result<Project>>? = null
    var setProjectActiveResult: CompletableDeferred<Result<Project>>? = null
    var setAssignedUserIdsResult: CompletableDeferred<Result<Unit>>? = null

    var getProjectsCallCount = 0
    var getProjectCallCount = 0
    val addedNames = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String>>()
    val activeChanges = mutableListOf<Pair<String, Boolean>>()
    val savedAssignments = mutableListOf<Pair<String, Set<String>>>()

    override suspend fun getProjects(): Result<List<Project>> {
        getProjectsCallCount++
        return getProjectsResult?.await() ?: projectsResult
    }

    override suspend fun getProject(projectId: String): Result<Project> {
        getProjectCallCount++
        return projectResult
    }

    override suspend fun addProject(name: ProjectName): Result<Project> {
        addedNames += name.value
        return addProjectResult?.await() ?: Result.success(Project(id = "new", name = name.value, isActive = true))
    }

    override suspend fun renameProject(
        projectId: String,
        name: ProjectName,
    ): Result<Project> {
        renamed += projectId to name.value
        return renameProjectResult?.await()
            ?: Result.success(Project(id = projectId, name = name.value, isActive = projectOf(projectId).isActive))
    }

    override suspend fun setProjectActive(
        projectId: String,
        isActive: Boolean,
    ): Result<Project> {
        activeChanges += projectId to isActive
        return setProjectActiveResult?.await() ?: Result.success(projectOf(projectId).copy(isActive = isActive))
    }

    override suspend fun getAssignedUserIds(projectId: String): Result<Set<String>> = assignedUserIdsResult

    override suspend fun setAssignedUserIds(
        projectId: String,
        userIds: Set<String>,
    ): Result<Unit> {
        savedAssignments += projectId to userIds
        return setAssignedUserIdsResult?.await() ?: Result.success(Unit)
    }

    var assignedProjectIdsOfCurrentUserResult: Result<Set<String>> = Result.success(setOf(ACTIVE.id))

    override suspend fun getAssignedProjectIdsOfCurrentUser(): Result<Set<String>> = assignedProjectIdsOfCurrentUserResult

    private fun projectOf(projectId: String): Project = projectsResult.getOrThrow().first { it.id == projectId }

    companion object {
        val ACTIVE = Project(id = "project-1", name = "案件B", isActive = true)
        val INACTIVE = Project(id = "project-2", name = "案件A", isActive = false)
    }
}
