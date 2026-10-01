package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.domain.work.WorkRecordRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * ViewModel のテスト用の [WorkRecordRepository] の偽物。
 */
internal class FakeWorkRecordRepository : WorkRecordRepository {
    /** ユーザーIDごとの稼働記録。 */
    val records = mutableMapOf<String, List<WorkRecord>>()

    /** getWorkRecords() を失敗させる場合の例外。 */
    var getFailure: Throwable? = null
    var getCallCount = 0

    /** saveWorkRecord() / deleteWorkRecord() の結果。 */
    var saveResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)

    /** null でない場合、saveWorkRecord() / deleteWorkRecord() はこれが complete するまで完了しない (保存中の状態をテストするため)。 */
    var gate: CompletableDeferred<Unit>? = null

    val savedRecords = mutableListOf<WorkRecord>()
    val deletedDates = mutableListOf<LocalDate>()

    override suspend fun getWorkRecords(
        userId: String,
        yearMonth: YearMonth,
    ): Result<List<WorkRecord>> {
        getCallCount++
        getFailure?.let { return Result.failure(it) }
        return Result.success(records[userId].orEmpty().filter { it.date.year == yearMonth.year && it.date.month == yearMonth.month })
    }

    override suspend fun saveWorkRecord(record: WorkRecord): Result<Unit> {
        savedRecords += record
        gate?.await()
        return saveResult
    }

    override suspend fun deleteWorkRecord(date: LocalDate): Result<Unit> {
        deletedDates += date
        gate?.await()
        return deleteResult
    }

    override suspend fun getWorkRecordsOfAllUsers(yearMonth: YearMonth): Result<Map<String, List<WorkRecord>>> = error("not used")
}
