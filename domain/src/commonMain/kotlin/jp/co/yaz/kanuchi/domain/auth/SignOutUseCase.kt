package jp.co.yaz.kanuchi.domain.auth

/**
 * ログアウトするユースケース。
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.signOut()
}
