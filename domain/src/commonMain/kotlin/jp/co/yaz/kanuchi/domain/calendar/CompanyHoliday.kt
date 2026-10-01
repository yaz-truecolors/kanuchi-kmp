package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate

/**
 * 会社の休業日 (土日・日本の祝日以外で、会社として休みにする日)。DB の `company_holidays` テーブルの1行に対応する。
 * admin が休業日管理画面で追加・削除する。休業日は稼働日に含めない ([WorkCalendar])。
 *
 * @property date 休業日 (1日に1件まで)
 * @property name 休業日の名前 (例: 年末年始休業)。前後の空白なしで 1〜[MAX_NAME_LENGTH] 文字
 */
data class CompanyHoliday(
    val date: LocalDate,
    val name: String,
) {
    init {
        require(isValidName(name)) { "invalid company holiday name: $name" }
    }

    companion object {
        /** 名前の最大文字数。DB の check 制約 (`company_holidays_name_check`) と一致させること。 */
        const val MAX_NAME_LENGTH: Int = 50

        /** [name] が休業日の名前として正しいか (前後の空白なしで 1〜[MAX_NAME_LENGTH] 文字)。 */
        fun isValidName(name: String): Boolean = name.isNotEmpty() && name == name.trim() && name.length <= MAX_NAME_LENGTH
    }
}
