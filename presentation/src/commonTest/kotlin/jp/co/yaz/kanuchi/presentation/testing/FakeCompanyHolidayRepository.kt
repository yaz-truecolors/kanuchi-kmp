package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import kotlinx.datetime.LocalDate

/**
 * ViewModel のテスト用の [CompanyHolidayRepository] の偽物。
 */
internal class FakeCompanyHolidayRepository : CompanyHolidayRepository {
    val holidays = mutableListOf<CompanyHoliday>()

    override suspend fun getCompanyHolidays(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<CompanyHoliday>> = Result.success(holidays.filter { it.date in from..to }.sortedBy { it.date })

    override suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday> {
        holidays += holiday
        return Result.success(holiday)
    }

    override suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit> {
        holidays.removeAll { it.date == date }
        return Result.success(Unit)
    }
}
