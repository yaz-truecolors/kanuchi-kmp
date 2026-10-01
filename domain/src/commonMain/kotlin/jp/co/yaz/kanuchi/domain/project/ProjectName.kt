package jp.co.yaz.kanuchi.domain.project

/**
 * 検証済みの案件名を表す Value Object。前後の空白を取り除き、空の名前は作れない。
 *
 * 不正な名前を保持したインスタンスを作れないよう、コンストラクタは private にし [ProjectName.of] ファクトリ経由でのみ生成する。
 * 名前の重複 (DB の unique 制約) はここでは確認しない (Repository が [DuplicateProjectNameException] を返す)。
 */
value class ProjectName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        fun of(rawValue: String): Result<ProjectName> {
            val trimmed = rawValue.trim()
            return if (trimmed.isEmpty()) {
                Result.failure(BlankProjectNameException())
            } else {
                Result.success(ProjectName(trimmed))
            }
        }
    }
}
