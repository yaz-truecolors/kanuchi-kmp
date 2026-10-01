package jp.co.yaz.kanuchi.domain.project

/**
 * 案件を追加するユースケース。名前の検証 ([ProjectName.of]) と追加 ([ProjectRepository.addProject]) を束ねる。
 * 名前が空なら [BlankProjectNameException]、同じ名前の案件があれば [DuplicateProjectNameException] を返す。
 */
class AddProjectUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(rawName: String): Result<Project> {
        val name = ProjectName.of(rawName).getOrElse { return Result.failure(it) }
        return projectRepository.addProject(name)
    }
}
