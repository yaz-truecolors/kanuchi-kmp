package jp.co.yaz.kanuchi.domain.calendar

/**
 * 同じ日付の休業日が既にあるため、追加できなかったことを示すマーカー例外
 * (DB の `company_holidays` の主キー (`holiday_date`) の重複)。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。調査用に元の例外を [cause] として保持できる。
 */
class DuplicateCompanyHolidayException(
    cause: Throwable? = null,
) : Exception(null, cause)
