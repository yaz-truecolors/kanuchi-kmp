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

    /** getWorkRecordsOfAllUsers() を失敗させる場合の例外。 */
    var getAllFailure: Throwable? = null

    /** getWorkRecordsOfAllUsers() に渡された月。 */
    val requestedMonthsOfAllUsers = mutableListOf<YearMonth>()

    override suspend fun getWorkRecordsOfAllUsers(yearMonth: YearMonth): Result<Map<String, List<WorkRecord>>> {
        requestedMonthsOfAllUsers += yearMonth
        getAllFailure?.let { return Result.failure(it) }
        return Result.success(
            records
                .mapValues { (_, list) -> list.filter { it.date.year == yearMonth.year && it.date.month == yearMonth.month } }
                .filterValues { it.isNotEmpty() },
        )
    }
}
