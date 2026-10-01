package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate

class FakeCompanyHolidayRepository : CompanyHolidayRepository {
    /** 登録済みの会社の休業日。 */
    val holidays = mutableListOf<CompanyHoliday>()

    /** getCompanyHolidays() を失敗させる場合の例外。 */
    var getFailure: Throwable? = null

    /** getCompanyHolidays() に渡された期間。 */
    val requestedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override suspend fun getCompanyHolidays(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<CompanyHoliday>> {
        requestedRanges += from to to
        getFailure?.let { return Result.failure(it) }
        return Result.success(holidays.filter { it.date in from..to }.sortedBy { it.date })
    }

    override suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday> {
        if (holidays.any { it.date == holiday.date }) return Result.failure(DuplicateCompanyHolidayException())
        holidays += holiday
        return Result.success(holiday)
    }

    override suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit> =
        if (holidays.removeAll { it.date == date }) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("not found: $date"))
        }
}
