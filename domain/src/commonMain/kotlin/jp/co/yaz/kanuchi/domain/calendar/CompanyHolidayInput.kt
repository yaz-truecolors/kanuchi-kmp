package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * 利用者が入力した会社の休業日 (入力欄の文字列そのまま)。
 * [toCompanyHolidays] で形式と値の組み合わせを検証して、期間内の日ごとの [CompanyHoliday] に変換する。
 *
 * - 日付 ([startDate] / [endDate]): `YYYY-MM-DD` 形式 (区切りは `/` も可、月日は1桁も可。[parseDate])。
 *   [endDate] が空なら [startDate] の1日だけ。期間は [MAX_DAYS] 日まで
 * - 名前 ([name]): 前後の空白を除いて 1〜[CompanyHoliday.MAX_NAME_LENGTH] 文字。期間内のすべての日に同じ名前を付ける
 *
 * 土日・日本の祝日と重なる日も追加できる (期間に含まれる日はすべて休業日にする)。
 */
data class CompanyHolidayInput(
    val startDate: String,
    val endDate: String,
    val name: String,
) {
    /**
     * 入力を検証して、期間内の日ごとの休業日 (日付の順) に変換する。条件を満たさない場合は、満たしていない条件を
     * すべて持つ [InvalidCompanyHolidayInputException] で失敗する。
     */
    fun toCompanyHolidays(): Result<List<CompanyHoliday>> {
        val start = parseDate(startDate)
        val end = if (endDate.isBlank()) start else parseDate(endDate)
        val trimmedName = name.trim()

        val violations =
            buildSet {
                if (start == null) add(CompanyHolidayViolation.START_DATE_INVALID)
                if (endDate.isNotBlank() && end == null) add(CompanyHolidayViolation.END_DATE_INVALID)
                if (start != null && end != null) {
                    val days = start.daysUntil(end) + 1
                    if (days < 1) add(CompanyHolidayViolation.END_DATE_BEFORE_START_DATE)
                    if (days > MAX_DAYS) add(CompanyHolidayViolation.PERIOD_TOO_LONG)
                }
                if (trimmedName.isEmpty()) add(CompanyHolidayViolation.NAME_BLANK)
                if (CompanyHoliday.nameLength(trimmedName) > CompanyHoliday.MAX_NAME_LENGTH) add(CompanyHolidayViolation.NAME_TOO_LONG)
            }
        if (violations.isNotEmpty()) return Result.failure(InvalidCompanyHolidayInputException(violations))
        // 違反が無い = 日付の形式が正しい (null でない)
        val first = checkNotNull(start)
        val days = first.daysUntil(checkNotNull(end)) + 1
        return Result.success(List(days) { CompanyHoliday(first.plus(DatePeriod(days = it)), trimmedName) })
    }

    companion object {
        /** 一度に追加できる期間の最大日数 (誤入力で大量の休業日を追加しないための上限)。 */
        const val MAX_DAYS: Int = 31

        private val DATE_PATTERN = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})""")

        /**
         * 日付の入力 (`YYYY-MM-DD`。区切りは `/` も可、月日は1桁も可。前後の空白は無視) を解釈する。
         * 形式が正しくない、存在しない日付、[JapaneseHolidays.SUPPORTED_YEARS] の範囲外の年の場合は null。
         */
        fun parseDate(text: String): LocalDate? {
            val (year, month, day) =
                DATE_PATTERN
                    .matchEntire(text.trim())
                    ?.destructured
                    ?.toList()
                    ?.map(String::toInt) ?: return null
            return if (year in JapaneseHolidays.SUPPORTED_YEARS) localDateOrNull(year, month, day) else null
        }

        private fun localDateOrNull(
            year: Int,
            month: Int,
            day: Int,
        ): LocalDate? =
            try {
                LocalDate(year, month, day)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

/**
 * 会社の休業日の入力が満たしていない条件。文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 *
 * @property field 違反を表示する入力欄
 */
enum class CompanyHolidayViolation(
    val field: CompanyHolidayField,
) {
    /** 開始日が日付の形式でない (存在しない日付・対応範囲外の年を含む)。 */
    START_DATE_INVALID(CompanyHolidayField.START_DATE),

    /** 終了日が日付の形式でない (存在しない日付・対応範囲外の年を含む)。 */
    END_DATE_INVALID(CompanyHolidayField.END_DATE),

    /** 終了日が開始日より前。 */
    END_DATE_BEFORE_START_DATE(CompanyHolidayField.END_DATE),

    /** 期間が [CompanyHolidayInput.MAX_DAYS] 日を超えている。 */
    PERIOD_TOO_LONG(CompanyHolidayField.END_DATE),

    /** 名前が空 (空白のみ)。 */
    NAME_BLANK(CompanyHolidayField.NAME),

    /** 名前が [CompanyHoliday.MAX_NAME_LENGTH] 文字を超えている (DB の check 制約)。 */
    NAME_TOO_LONG(CompanyHolidayField.NAME),
}

/** 会社の休業日の入力欄。 */
enum class CompanyHolidayField {
    START_DATE,
    END_DATE,
    NAME,
}

/**
 * 会社の休業日の入力が条件を満たしていないため追加できないことを示す例外。
 * 文言は持たず、presentation 層が [violations] を strings.xml の文言に変換して表示する。
 */
class InvalidCompanyHolidayInputException(
    val violations: Set<CompanyHolidayViolation>,
) : Exception()
