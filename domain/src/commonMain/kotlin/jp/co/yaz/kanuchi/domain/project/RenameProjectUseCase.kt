package jp.co.yaz.kanuchi.domain.project

/**
 * 案件の名前を変更するユースケース。失敗の種類は [AddProjectUseCase] と同じ。
 */
class RenameProjectUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(
        projectId: String,
        rawName: String,
    ): Result<Project> {
        val name = ProjectName.of(rawName).getOrElse { return Result.failure(it) }
        return projectRepository.renameProject(projectId, name)
    }
}
