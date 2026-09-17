package jp.co.yaz.kanuchi.domain.auth

/**
 * 検証済みのメールアドレスを表す Value Object。
 *
 * 不正な形式のメールアドレスを保持したインスタンスを作れないよう、
 * コンストラクタは private にし [EmailAddress.of] ファクトリ経由でのみ生成する。
 */
value class EmailAddress private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        // RFC完全準拠ではなく、UIでの簡易バリデーションを目的とした緩めのパターン。
        // 最終的な正当性の担保はSupabase Auth側のメール送信結果に委ねる。
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun of(rawValue: String): Result<EmailAddress> {
            val trimmed = rawValue.trim()
            return if (EMAIL_PATTERN.matches(trimmed)) {
                Result.success(EmailAddress(trimmed))
            } else {
                Result.failure(IllegalArgumentException("Invalid email address: $rawValue"))
            }
        }
    }
}
