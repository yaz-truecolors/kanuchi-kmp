package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import kotlinx.datetime.YearMonth

/**
 * ログイン中のユーザー自身の月次シートを取得するユースケース (日次入力の月の一覧で使う)。
 * ユーザーIDはプロフィール ([ProfileRepository.getCurrentUserProfile]) から取る。どちらかの取得に失敗した場合は、その失敗を返す。
 */
class GetCurrentUserMonthlyWorkSheetUseCase(
    private val profileRepository: ProfileRepository,
    private val getMonthlyWorkSheetUseCase: GetMonthlyWorkSheetUseCase,
) {
    suspend operator fun invoke(yearMonth: YearMonth): Result<MonthlyWorkSheet> {
        val profile = profileRepository.getCurrentUserProfile().getOrElse { return Result.failure(it) }
        return getMonthlyWorkSheetUseCase(profile.id, yearMonth)
    }
}
