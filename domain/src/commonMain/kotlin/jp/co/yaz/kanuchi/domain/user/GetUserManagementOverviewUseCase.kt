package jp.co.yaz.kanuchi.domain.user

import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * ユーザー管理画面に表示する内容 (ログイン中のユーザー・全ユーザー・招待リスト) を取得するユースケース。
 * 招待リストの各メールアドレスについて、既にアカウントがあるか ([Invitation.hasAccount]) を判定する。
 */
class GetUserManagementOverviewUseCase(
    private val profileRepository: ProfileRepository,
    private val userManagementRepository: UserManagementRepository,
) {
    suspend operator fun invoke(): Result<UserManagementOverview> =
        coroutineScope {
            val currentUser = async { profileRepository.getCurrentUserProfile() }
            val users = async { profileRepository.getProfiles() }
            val invitedEmails = async { userManagementRepository.getInvitedEmails() }

            val profiles = users.await().getOrElse { return@coroutineScope Result.failure(it) }
            val currentUserId = currentUser.await().getOrElse { return@coroutineScope Result.failure(it) }.id
            val emails = invitedEmails.await().getOrElse { return@coroutineScope Result.failure(it) }
            // Supabase Auth はメールアドレスを小文字で保存し、招待リストも小文字に正規化しているが、念のため大文字小文字を区別しない
            val registeredEmails = profiles.map { it.email.lowercase() }.toSet()
            Result.success(
                UserManagementOverview(
                    currentUserId = currentUserId,
                    users = profiles,
                    invitations = emails.map { Invitation(email = it, hasAccount = it.lowercase() in registeredEmails) },
                ),
            )
        }
}
