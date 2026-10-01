package jp.co.yaz.kanuchi.domain.project

/**
 * ユースケースのテスト用の [ProjectRepository] の偽物。返す値をテストから差し替えられ、渡された引数を記録する。
 */
internal class FakeProjectRepository : ProjectRepository {
    var projectsResult: Result<List<Project>> = Result.success(listOf(ACTIVE))
    var projectResult: Result<Project> = Result.success(ACTIVE)
    var addProjectResult: Result<Project>? = null
    var renameProjectResult: Result<Project>? = null
    var setProjectActiveResult: Result<Project>? = null
    var assignedUserIdsResult: Result<Set<String>> = Result.success(setOf("user-1"))
    var setAssignedUserIdsResult: Result<Unit> = Result.success(Unit)

    var lastAddedName: ProjectName? = null
    var lastRenamed: Pair<String, ProjectName>? = null
    var lastSetActive: Pair<String, Boolean>? = null
    var lastSetAssigned: Pair<String, Set<String>>? = null

    override suspend fun getProjects(): Result<List<Project>> = projectsResult

    override suspend fun getProject(projectId: String): Result<Project> = projectResult

    override suspend fun addProject(name: ProjectName): Result<Project> {
        lastAddedName = name
        return addProjectResult ?: Result.success(Project(id = "new", name = name.value, isActive = true))
    }

    override suspend fun renameProject(
        projectId: String,
        name: ProjectName,
    ): Result<Project> {
        lastRenamed = projectId to name
        return renameProjectResult ?: Result.success(Project(id = projectId, name = name.value, isActive = true))
    }

    override suspend fun setProjectActive(
        projectId: String,
        isActive: Boolean,
    ): Result<Project> {
        lastSetActive = projectId to isActive
        return setProjectActiveResult ?: Result.success(Project(id = projectId, name = "name", isActive = isActive))
    }

    override suspend fun getAssignedUserIds(projectId: String): Result<Set<String>> = assignedUserIdsResult

    override suspend fun setAssignedUserIds(
        projectId: String,
        userIds: Set<String>,
    ): Result<Unit> {
        lastSetAssigned = projectId to userIds
        return setAssignedUserIdsResult
    }

    companion object {
        val ACTIVE = Project(id = "project-1", name = "案件A", isActive = true)
    }
}
