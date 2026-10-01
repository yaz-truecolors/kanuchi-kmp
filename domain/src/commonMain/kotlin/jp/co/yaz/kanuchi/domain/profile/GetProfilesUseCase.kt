package jp.co.yaz.kanuchi.domain.profile

/**
 * ユーザーのプロフィール一覧を取得するユースケース (管理者向けの画面で使う)。
 * 参照できる範囲は [ProfileRepository.getProfiles] を参照。
 */
class GetProfilesUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): Result<List<UserProfile>> = profileRepository.getProfiles()
}
