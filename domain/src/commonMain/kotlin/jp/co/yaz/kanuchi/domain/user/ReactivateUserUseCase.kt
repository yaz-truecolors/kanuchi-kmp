package jp.co.yaz.kanuchi.domain.user

/**
 * 利用停止中のユーザーを復帰させるユースケース。
 */
class ReactivateUserUseCase(
    private val userManagementRepository: UserManagementRepository,
) {
    suspend operator fun invoke(userId: String): Result<Unit> = userManagementRepository.setUserSuspended(userId, suspended = false)
}
