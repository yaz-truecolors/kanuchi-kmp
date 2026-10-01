package jp.co.yaz.kanuchi.domain.work

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class FakeWorkRecordRepository : WorkRecordRepository {
    /** ユーザーIDごとの稼働記録。 */
    val records = mutableMapOf<String, List<WorkRecord>>()

    /** getWorkRecords() を失敗させる場合の例外。 */
    var getFailure: Throwable? = null

    override suspend fun getWorkRecords(
        userId: String,
        yearMonth: YearMonth,
    ): Result<List<WorkRecord>> {
        getFailure?.let { return Result.failure(it) }
        return Result.success(records[userId].orEmpty().filter { it.date.year == yearMonth.year && it.date.month == yearMonth.month })
    }

    /** saveWorkRecord() / deleteWorkRecord() の結果。 */
    var saveResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)

    /** saveWorkRecord() / deleteWorkRecord() に渡された値。 */
    val savedRecords = mutableListOf<WorkRecord>()
    val deletedDates = mutableListOf<LocalDate>()

    override suspend fun saveWorkRecord(record: WorkRecord): Result<Unit> {
        savedRecords += record
        return saveResult
    }

    override suspend fun deleteWorkRecord(date: LocalDate): Result<Unit> {
        deletedDates += date
        return deleteResult
    }
}
