package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * [year] 年 (1/1〜12/31) の会社の休業日を、日付の順に取得するユースケース (休業日管理画面の一覧)。
 */
class GetCompanyHolidaysOfYearUseCase(
    private val companyHolidayRepository: CompanyHolidayRepository,
) {
    suspend operator fun invoke(year: Int): Result<List<CompanyHoliday>> =
        companyHolidayRepository.getCompanyHolidays(
            from = LocalDate(year, Month.JANUARY, 1),
            to = LocalDate(year, Month.DECEMBER, LAST_DAY_OF_DECEMBER),
        )

    private companion object {
        const val LAST_DAY_OF_DECEMBER = 31
    }
}
