package jp.co.yaz.kanuchi.domain.profile

/**
 * 利用者が設定する表示名を表す Value Object。前後の空白を除いた、空でない [MAX_LENGTH] 文字以下の文字列。
 *
 * 不正な表示名を作れないよう、コンストラクタは private にし [DisplayName.of] ファクトリ経由でのみ生成する。
 * 既存の表示名 ([UserProfile.displayName]) は初期値 (メールアドレスの `@` より前) のままだと上限を超えることが
 * あるため、この型にはしていない (変更するときにだけ検証する)。
 */
value class DisplayName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /** 表示名の最大文字数 (暫定。画面での表示幅を考慮して決めた。DB 側では制限していない)。 */
        const val MAX_LENGTH = 50

        /**
         * 入力された表示名を検証する。前後の空白 (全角スペースを含む) は除く。
         * 条件を満たさない場合は [InvalidDisplayNameException] で失敗する。
         */
        fun of(rawValue: String): Result<DisplayName> {
            val trimmed = rawValue.trim()
            val violation =
                when {
                    trimmed.isEmpty() -> DisplayNameViolation.BLANK
                    trimmed.codePointCount() > MAX_LENGTH -> DisplayNameViolation.TOO_LONG
                    else -> return Result.success(DisplayName(trimmed))
                }
            return Result.failure(InvalidDisplayNameException(violation))
        }

        // 絵文字等のサロゲートペアを1文字と数える (String.length は UTF-16 の単位数のため2文字になる)
        private fun String.codePointCount(): Int = count { !it.isLowSurrogate() }
    }
}

/** 表示名が満たしていない条件。文言は持たず、presentation 層が strings.xml の文言に変換して表示する。 */
enum class DisplayNameViolation {
    /** 空 (空白のみを含む)。 */
    BLANK,

    /** [DisplayName.MAX_LENGTH] 文字を超えている。 */
    TOO_LONG,
}

/**
 * 表示名が条件を満たしていないため変更できないことを示す例外。
 * 文言は持たず、presentation 層が [violation] を strings.xml の文言に変換して表示する。
 */
class InvalidDisplayNameException(
    val violation: DisplayNameViolation,
) : Exception()
