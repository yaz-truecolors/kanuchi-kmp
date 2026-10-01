package jp.co.yaz.kanuchi.domain.work

import kotlinx.datetime.LocalDate
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

    /**
     * ログイン中のユーザー自身の [record] の日の記録を保存する (その日の記録がまだ無ければ追加、あれば上書き)。
     * 案件ごとの配分も [WorkRecord.allocations] のとおりにする (含まれない案件の配分は消す。時間が 0 の配分は保存しない)。
     * 途中で失敗した場合、一部だけが反映されていることがある (同じ内容で再度呼べば、残りが反映される)。
     */
    suspend fun saveWorkRecord(record: WorkRecord): Result<Unit>

    /**
     * ログイン中のユーザー自身の [date] の記録 (案件ごとの配分を含む) を削除する。
     * 削除した日は「何も入力していない日」として扱われる (稼働日なら定時どおり)。記録が無い場合も成功とする。
     */
    suspend fun deleteWorkRecord(date: LocalDate): Result<Unit>
}
