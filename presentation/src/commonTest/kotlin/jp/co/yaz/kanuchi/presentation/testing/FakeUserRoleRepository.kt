package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.role.UserRoleRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * ViewModel のテスト用の [UserRoleRepository] の偽物。
 */
internal class FakeUserRoleRepository : UserRoleRepository {
    /** changeRole() の結果。null の間は [pendingResult] を complete するまで完了しない (保存中の状態をテストするため)。 */
    var changeRoleResult: Result<Unit>? = Result.success(Unit)
    val pendingResult = CompletableDeferred<Result<Unit>>()

    val roleChangeRequests = mutableListOf<Pair<String, UserRole>>()

    override suspend fun changeRole(
        userId: String,
        role: UserRole,
    ): Result<Unit> {
        roleChangeRequests += userId to role
        return changeRoleResult ?: pendingResult.await()
    }
}
