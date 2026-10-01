package jp.co.yaz.kanuchi.domain.role

import jp.co.yaz.kanuchi.domain.profile.UserRole

/**
 * ユースケースのテスト用の [UserRoleRepository] の偽物。返す値をテストから差し替えられ、呼び出しを記録する。
 */
internal class FakeUserRoleRepository : UserRoleRepository {
    var changeRoleResult: Result<Unit> = Result.success(Unit)

    val roleChangeRequests = mutableListOf<Pair<String, UserRole>>()

    override suspend fun changeRole(
        userId: String,
        role: UserRole,
    ): Result<Unit> {
        roleChangeRequests += userId to role
        return changeRoleResult
    }
}
