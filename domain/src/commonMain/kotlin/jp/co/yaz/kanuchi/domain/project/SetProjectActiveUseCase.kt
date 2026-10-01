package jp.co.yaz.kanuchi.domain.project

/**
 * 案件の有効／無効を切り替えるユースケース。案件は削除せず、使わなくなったら無効にする。
 */
class SetProjectActiveUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(
        projectId: String,
        isActive: Boolean,
    ): Result<Project> = projectRepository.setProjectActive(projectId, isActive)
}
