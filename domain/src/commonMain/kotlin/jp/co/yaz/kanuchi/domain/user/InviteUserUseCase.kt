package jp.co.yaz.kanuchi.domain.user

import jp.co.yaz.kanuchi.domain.auth.EmailAddress

/**
 * メールアドレスを招待リストに追加するユースケース。入力文字列の検証と追加を束ねる。
 * 形式が正しくない場合は [InvalidEmailAddressException] を返す。
 */
class InviteUserUseCase(
    private val userManagementRepository: UserManagementRepository,
) {
    suspend operator fun invoke(rawEmail: String): Result<Unit> {
        val email = EmailAddress.of(rawEmail).getOrElse { return Result.failure(InvalidEmailAddressException()) }
        return userManagementRepository.invite(email)
    }
}
