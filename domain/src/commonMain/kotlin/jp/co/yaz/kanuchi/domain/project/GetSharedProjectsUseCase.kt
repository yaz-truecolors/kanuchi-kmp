package jp.co.yaz.kanuchi.domain.project

/**
 * 共有の案件 (無効な案件を含む。個人案件は含まない) を、一覧に表示する順 ([Project.DISPLAY_ORDER]) で取得するユースケース。
 * 案件管理画面 (admin) で使う。
 */
class GetSharedProjectsUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(): Result<List<Project>> =
        projectRepository.getProjects().map { projects ->
            projects.filterNot { it.isPersonal }.sortedWith(Project.DISPLAY_ORDER)
        }
}
