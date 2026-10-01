package jp.co.yaz.kanuchi.domain.project

/**
 * すべての案件 (無効な案件を含む) を、一覧に表示する順 ([Project.DISPLAY_ORDER]) で取得するユースケース。
 */
class GetProjectsUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(): Result<List<Project>> = projectRepository.getProjects().map { it.sortedWith(Project.DISPLAY_ORDER) }
}
