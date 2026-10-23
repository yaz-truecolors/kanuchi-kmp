package jp.co.yaz.kanuchi.domain.project

/**
 * ログイン中のユーザー自身の個人案件を追加するユースケース。名前の検証 ([ProjectName.of]) と追加
 * ([ProjectRepository.addPersonalProject]) を束ねる。名前が空なら [BlankProjectNameException] を返す
 * (個人案件の名前は他の案件と重複してよいため、重複は失敗にならない)。
 */
class AddPersonalProjectUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(rawName: String): Result<Project> {
        val name = ProjectName.of(rawName).getOrElse { return Result.failure(it) }
        return projectRepository.addPersonalProject(name)
    }
}
