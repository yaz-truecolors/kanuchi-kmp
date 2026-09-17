package jp.co.yaz.kanuchi.domain.auth

/**
 * メールアドレス宛にマジックリンクを送信するユースケース。
 * 入力文字列の検証 ([EmailAddress.of]) と送信処理 ([AuthRepository.sendMagicLink]) を束ねる。
 */
class SendMagicLinkUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(rawEmail: String): Result<Unit> {
        val email = EmailAddress.of(rawEmail).getOrElse { return Result.failure(it) }
        return authRepository.sendMagicLink(email)
    }
}
