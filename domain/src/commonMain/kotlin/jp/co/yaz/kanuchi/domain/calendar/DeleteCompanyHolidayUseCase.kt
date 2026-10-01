package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate

/**
 * [date] の会社の休業日を削除するユースケース (admin のみ)。
 * 該当する休業日が無い (他の admin が先に削除した、権限が無い等で削除できなかった) 場合も失敗として返す。
 */
class DeleteCompanyHolidayUseCase(
    private val companyHolidayRepository: CompanyHolidayRepository,
) {
    suspend operator fun invoke(date: LocalDate): Result<Unit> = companyHolidayRepository.deleteCompanyHoliday(date)
}
