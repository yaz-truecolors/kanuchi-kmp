package jp.co.yaz.kanuchi.domain.work

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
}
