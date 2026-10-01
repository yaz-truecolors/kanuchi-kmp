package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.DuplicateCompanyHolidayException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate

/**
 * ViewModel のテスト用の [CompanyHolidayRepository] の偽物。[holidays] を DB の代わりに使う。
 *
 * 結果を [CompletableDeferred] で指定すると、complete するまで完了しない (読み込み中・保存中の状態をテストするため)。
 * 指定しない (null のまま) 場合は、[holidays] に対して即座に取得・追加・削除する。
 */
internal class FakeCompanyHolidayRepository : CompanyHolidayRepository {
    val holidays = mutableListOf<CompanyHoliday>()

    var getResult: CompletableDeferred<Result<List<CompanyHoliday>>>? = null
    var addResult: CompletableDeferred<Result<CompanyHoliday>>? = null
    var deleteResult: CompletableDeferred<Result<Unit>>? = null

    val requestedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()
    val added = mutableListOf<CompanyHoliday>()
    val deletedDates = mutableListOf<LocalDate>()

    override suspend fun getCompanyHolidays(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<CompanyHoliday>> {
        requestedRanges += from to to
        return getResult?.await() ?: Result.success(holidays.filter { it.date in from..to }.sortedBy { it.date })
    }

    override suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday> {
        added += holiday
        return addResult?.await() ?: addToHolidays(holiday)
    }

    private fun addToHolidays(holiday: CompanyHoliday): Result<CompanyHoliday> =
        if (holidays.any { it.date == holiday.date }) {
            Result.failure(DuplicateCompanyHolidayException())
        } else {
            holidays += holiday
            Result.success(holiday)
        }

    override suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit> {
        deletedDates += date
        deleteResult?.let { return it.await() }
        return if (holidays.removeAll { it.date == date }) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("not found: $date"))
        }
    }
}
