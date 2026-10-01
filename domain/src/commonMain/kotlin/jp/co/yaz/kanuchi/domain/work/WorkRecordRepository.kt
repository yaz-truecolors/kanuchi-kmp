package jp.co.yaz.kanuchi.domain.work

import kotlinx.datetime.YearMonth

/**
 * 日次の稼働記録を扱うRepositoryインターフェース。実装はdata層 (SupabaseWorkRecordRepository) が提供する。
 *
 * 失敗時は [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す (presentation 層が文言に変換して表示する)。
 * 参照できるのは本人の記録と、admin なら全員分 (DB のアクセス制御 (RLS) で強制している)。
 * 権限の無いユーザーの記録は、エラーにならず空として返る。
 */
interface WorkRecordRepository {
    /** [userId] のユーザーの [yearMonth] の稼働記録 (案件ごとの配分を含む) を、日付の順に取得する。 */
    suspend fun getWorkRecords(
        userId: String,
        yearMonth: YearMonth,
    ): Result<List<WorkRecord>>
}
