package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate

/**
 * 会社の休業日を扱うRepositoryインターフェース。実装はdata層 (SupabaseCompanyHolidayRepository) が提供する。
 *
 * 失敗時は、特に記載が無い限り [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す
 * (presentation 層が文言に変換して表示する)。
 * 参照は全員ができるが、追加・削除は admin だけができる (DB のアクセス制御 (RLS) で強制している)。
 * 権限が無く変更できなかった場合も失敗として返す。
 */
interface CompanyHolidayRepository {
    /** [from]〜[to] (両端を含む) の休業日を、日付の順に取得する。 */
    suspend fun getCompanyHolidays(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<CompanyHoliday>>

    /**
     * 休業日を追加し、追加した休業日を返す。
     * 同じ日付の休業日が既にある場合は [DuplicateCompanyHolidayException] を返す。
     */
    suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday>

    /** [date] の休業日を削除する。該当する休業日が無い (削除できなかった) 場合も失敗として返す。 */
    suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit>
}
