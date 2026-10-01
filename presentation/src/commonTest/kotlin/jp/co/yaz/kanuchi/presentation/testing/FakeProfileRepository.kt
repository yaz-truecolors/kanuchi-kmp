package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.profile.DisplayName
import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole

/**
 * ViewModel のテスト用の [ProfileRepository] の偽物。
 */
internal class FakeProfileRepository : ProfileRepository {
    var currentUserProfileResult: Result<UserProfile> = Result.success(MEMBER)
    var profilesResult: Result<List<UserProfile>> = Result.success(listOf(MEMBER, ADMIN))
    var getCurrentUserProfileCallCount = 0

    override suspend fun getCurrentUserProfile(): Result<UserProfile> {
        getCurrentUserProfileCallCount++
        return currentUserProfileResult
    }

    /** updateDisplayName() の結果。null の場合は [MEMBER] の表示名を変更したプロフィールを返す。 */
    var updateDisplayNameResult: Result<UserProfile>? = null
    val updatedDisplayNames = mutableListOf<String>()

    override suspend fun getProfiles(): Result<List<UserProfile>> = profilesResult

    override suspend fun updateDisplayName(displayName: DisplayName): Result<UserProfile> {
        updatedDisplayNames += displayName.value
        return updateDisplayNameResult ?: Result.success(MEMBER.copy(displayName = displayName.value))
    }

    companion object {
        val MEMBER = UserProfile(id = "user-1", email = "taro@example.com", displayName = "taro", role = UserRole.MEMBER)
        val ADMIN = UserProfile(id = "user-2", email = "hanako@example.com", displayName = "hanako", role = UserRole.ADMIN)
    }
}
