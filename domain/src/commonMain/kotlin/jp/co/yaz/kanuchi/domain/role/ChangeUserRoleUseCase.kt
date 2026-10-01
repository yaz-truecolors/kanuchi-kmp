package jp.co.yaz.kanuchi.domain.role

import jp.co.yaz.kanuchi.domain.profile.UserRole

/**
 * ユーザーの権限を変更する (admin への昇格・member への降格) ユースケース。
 * 失敗時の例外は [UserRoleRepository.changeRole] を参照。
 */
class ChangeUserRoleUseCase(
    private val userRoleRepository: UserRoleRepository,
) {
    suspend operator fun invoke(
        userId: String,
        role: UserRole,
    ): Result<Unit> = userRoleRepository.changeRole(userId, role)
}
