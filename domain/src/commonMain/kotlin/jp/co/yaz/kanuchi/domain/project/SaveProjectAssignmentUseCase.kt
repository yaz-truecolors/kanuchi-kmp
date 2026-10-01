package jp.co.yaz.kanuchi.domain.project

/**
 * 案件の担当メンバーを、選択されたユーザーのとおりに保存するユースケース。
 */
class SaveProjectAssignmentUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(
        projectId: String,
        userIds: Set<String>,
    ): Result<Unit> = projectRepository.setAssignedUserIds(projectId, userIds)
}
