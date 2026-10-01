package jp.co.yaz.kanuchi.domain.calendar

/**
 * 会社の休業日 (1日、または連続した期間) を追加するユースケース (admin のみ)。
 * 入力の検証 ([CompanyHolidayInput.toCompanyHolidays]) と、期間内の日ごとの追加
 * ([CompanyHolidayRepository.addCompanyHoliday]) を束ね、追加した休業日を日付の順に返す。
 *
 * - 入力が条件を満たさない場合は [InvalidCompanyHolidayInputException] で失敗し、何も追加しない。
 * - 期間内に既に休業日がある日が含まれる場合は [DuplicateCompanyHolidayException] で失敗し、何も追加しない
 *   (追加の前に期間内の休業日を取得して確かめる)。確かめた後に他の admin が同じ日を追加した場合は、
 *   その日の追加が [DuplicateCompanyHolidayException] で失敗する。
 * - 途中の日の追加に失敗した場合は、その時点で失敗を返す。それより前の日は追加済みのまま残るため、
 *   呼び出し側は一覧を読み込み直すこと。
 */
class AddCompanyHolidaysUseCase(
    private val companyHolidayRepository: CompanyHolidayRepository,
) {
    suspend operator fun invoke(input: CompanyHolidayInput): Result<List<CompanyHoliday>> {
        val holidays = input.toCompanyHolidays().getOrElse { return Result.failure(it) }
        return companyHolidayRepository.getCompanyHolidays(holidays.first().date, holidays.last().date).fold(
            onSuccess = { existing -> if (existing.isEmpty()) addAll(holidays) else Result.failure(DuplicateCompanyHolidayException()) },
            onFailure = { Result.failure(it) },
        )
    }

    private suspend fun addAll(holidays: List<CompanyHoliday>): Result<List<CompanyHoliday>> {
        val added = mutableListOf<CompanyHoliday>()
        for (holiday in holidays) {
            companyHolidayRepository.addCompanyHoliday(holiday).onSuccess { added += it }.onFailure { return Result.failure(it) }
        }
        return Result.success(added)
    }
}
