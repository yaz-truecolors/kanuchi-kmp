package jp.co.yaz.kanuchi.domain.user

/**
 * ユーザーを利用停止するユースケース。
 */
class SuspendUserUseCase(
    private val userManagementRepository: UserManagementRepository,
) {
    suspend operator fun invoke(userId: String): Result<Unit> = userManagementRepository.setUserSuspended(userId, suspended = true)
}
