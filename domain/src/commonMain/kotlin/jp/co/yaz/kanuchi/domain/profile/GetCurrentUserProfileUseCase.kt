package jp.co.yaz.kanuchi.domain.profile

/**
 * ログイン中のユーザー自身のプロフィール (表示名・権限) を取得するユースケース。
 */
class GetCurrentUserProfileUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): Result<UserProfile> = profileRepository.getCurrentUserProfile()
}
