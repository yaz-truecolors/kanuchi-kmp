package jp.co.yaz.kanuchi.domain.profile

/**
 * ユースケースのテスト用の [ProfileRepository] の偽物。返す値をテストから差し替えられる。
 */
internal class FakeProfileRepository : ProfileRepository {
    var currentUserProfileResult: Result<UserProfile> = Result.success(PROFILE)
    var profilesResult: Result<List<UserProfile>> = Result.success(listOf(PROFILE))

    override suspend fun getCurrentUserProfile(): Result<UserProfile> = currentUserProfileResult

    var updateDisplayNameResult: Result<UserProfile>? = null
    val updatedDisplayNames = mutableListOf<DisplayName>()

    override suspend fun getProfiles(): Result<List<UserProfile>> = profilesResult

    override suspend fun updateDisplayName(displayName: DisplayName): Result<UserProfile> {
        updatedDisplayNames += displayName
        return updateDisplayNameResult ?: Result.success(PROFILE.copy(displayName = displayName.value))
    }

    companion object {
        val PROFILE = UserProfile(id = "user-1", email = "taro@example.com", displayName = "taro", role = UserRole.MEMBER)
    }
}
