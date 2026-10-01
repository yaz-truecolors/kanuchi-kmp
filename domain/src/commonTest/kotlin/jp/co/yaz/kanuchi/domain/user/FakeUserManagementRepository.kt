package jp.co.yaz.kanuchi.domain.user

import jp.co.yaz.kanuchi.domain.auth.EmailAddress

/**
 * ユースケースのテスト用の [UserManagementRepository] の偽物。返す値をテストから差し替えられ、呼び出しを記録する。
 */
internal class FakeUserManagementRepository : UserManagementRepository {
    var invitedEmailsResult: Result<List<String>> = Result.success(emptyList())
    var inviteResult: Result<Unit> = Result.success(Unit)
    var revokeResult: Result<Unit> = Result.success(Unit)
    var setUserSuspendedResult: Result<Unit> = Result.success(Unit)

    val invitedEmails = mutableListOf<EmailAddress>()
    val revokedEmails = mutableListOf<String>()
    val suspensionRequests = mutableListOf<Pair<String, Boolean>>()

    override suspend fun getInvitedEmails(): Result<List<String>> = invitedEmailsResult

    override suspend fun invite(email: EmailAddress): Result<Unit> {
        invitedEmails += email
        return inviteResult
    }

    override suspend fun revokeInvitation(email: String): Result<Unit> {
        revokedEmails += email
        return revokeResult
    }

    override suspend fun setUserSuspended(
        userId: String,
        suspended: Boolean,
    ): Result<Unit> {
        suspensionRequests += userId to suspended
        return setUserSuspendedResult
    }
}
