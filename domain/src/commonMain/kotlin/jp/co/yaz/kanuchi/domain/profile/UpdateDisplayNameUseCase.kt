package jp.co.yaz.kanuchi.domain.profile

/**
 * ログイン中のユーザー自身の表示名を変更するユースケース。
 * 入力の検証 ([DisplayName.of]) と保存 ([ProfileRepository.updateDisplayName]) を束ねる。
 * 入力が条件を満たさない場合は [InvalidDisplayNameException] で失敗し、保存しない。
 */
class UpdateDisplayNameUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(rawDisplayName: String): Result<UserProfile> {
        val displayName = DisplayName.of(rawDisplayName).getOrElse { return Result.failure(it) }
        return profileRepository.updateDisplayName(displayName)
    }
}
