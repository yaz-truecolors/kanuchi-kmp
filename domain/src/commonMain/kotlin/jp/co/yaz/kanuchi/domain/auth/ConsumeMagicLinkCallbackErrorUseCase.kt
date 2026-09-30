package jp.co.yaz.kanuchi.domain.auth

/**
 * マジックリンクを開いてアプリに戻ってきた際にログインできなかった理由を、1回だけ取り出すユースケース。
 * 詳細な契約は [AuthRepository.consumeMagicLinkCallbackError] を参照。
 */
class ConsumeMagicLinkCallbackErrorUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): MagicLinkCallbackError? = authRepository.consumeMagicLinkCallbackError()
}
