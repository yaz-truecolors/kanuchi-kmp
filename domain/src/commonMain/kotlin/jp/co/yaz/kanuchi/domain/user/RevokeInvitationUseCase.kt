package jp.co.yaz.kanuchi.domain.user

/**
 * 招待を取り消すユースケース。
 */
class RevokeInvitationUseCase(
    private val userManagementRepository: UserManagementRepository,
) {
    suspend operator fun invoke(email: String): Result<Unit> = userManagementRepository.revokeInvitation(email)
}
