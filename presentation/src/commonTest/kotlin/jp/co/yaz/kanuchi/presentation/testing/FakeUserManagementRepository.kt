package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.auth.EmailAddress
import jp.co.yaz.kanuchi.domain.user.UserManagementRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * ViewModel のテスト用の [UserManagementRepository] の偽物。
 * 招待リストはメモリ上に持ち、追加・取り消しで変化する。
 */
internal class FakeUserManagementRepository : UserManagementRepository {
    val invitedEmails = mutableListOf<String>()
    var getInvitedEmailsResult: Result<Unit> = Result.success(Unit)
    var inviteResult: Result<Unit> = Result.success(Unit)
    var revokeResult: Result<Unit> = Result.success(Unit)
    var setUserSuspendedResult: Result<Unit> = Result.success(Unit)

    /** 設定すると、利用停止・復帰がこれの完了まで待つ (実行中の状態のテスト用)。 */
    var setUserSuspendedGate: CompletableDeferred<Unit>? = null

    val suspensionRequests = mutableListOf<Pair<String, Boolean>>()
    var getInvitedEmailsCallCount = 0

    override suspend fun getInvitedEmails(): Result<List<String>> {
        getInvitedEmailsCallCount++
        return getInvitedEmailsResult.map { invitedEmails.toList() }
    }

    override suspend fun invite(email: EmailAddress): Result<Unit> = inviteResult.onSuccess { invitedEmails.add(0, email.value) }

    override suspend fun revokeInvitation(email: String): Result<Unit> = revokeResult.onSuccess { invitedEmails.remove(email) }

    override suspend fun setUserSuspended(
        userId: String,
        suspended: Boolean,
    ): Result<Unit> {
        suspensionRequests += userId to suspended
        setUserSuspendedGate?.await()
        return setUserSuspendedResult
    }
}
