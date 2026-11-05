package jp.co.yaz.kanuchi.domain.project

/**
 * ログイン中のユーザー自身の個人案件を削除するユースケース (論理削除)。
 * 当月以降の日に工数が配分されている場合は [PersonalProjectInUseException] を返す。
 */
class DeletePersonalProjectUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(projectId: String): Result<Unit> = projectRepository.deletePersonalProject(projectId)
}
