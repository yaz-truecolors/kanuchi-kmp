package jp.co.yaz.kanuchi.domain.role

import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 権限管理画面に表示する内容 (ログイン中のユーザー・全ユーザー) を取得するユースケース。
 */
class GetRoleManagementOverviewUseCase(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): Result<RoleManagementOverview> =
        coroutineScope {
            val currentUser = async { profileRepository.getCurrentUserProfile() }
            val users = async { profileRepository.getProfiles() }

            val profiles = users.await().getOrElse { return@coroutineScope Result.failure(it) }
            val currentUserId = currentUser.await().getOrElse { return@coroutineScope Result.failure(it) }.id
            Result.success(RoleManagementOverview(currentUserId = currentUserId, users = profiles))
        }
}
