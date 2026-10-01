package jp.co.yaz.kanuchi.domain.profile

/**
 * ユースケースのテスト用の [ProfileRepository] の偽物。返す値をテストから差し替えられる。
 */
internal class FakeProfileRepository : ProfileRepository {
    var currentUserProfileResult: Result<UserProfile> = Result.success(PROFILE)
    var profilesResult: Result<List<UserProfile>> = Result.success(listOf(PROFILE))

    override suspend fun getCurrentUserProfile(): Result<UserProfile> = currentUserProfileResult

    override suspend fun getProfiles(): Result<List<UserProfile>> = profilesResult

    companion object {
        val PROFILE = UserProfile(id = "user-1", email = "taro@example.com", displayName = "taro", role = UserRole.MEMBER)
    }
}
