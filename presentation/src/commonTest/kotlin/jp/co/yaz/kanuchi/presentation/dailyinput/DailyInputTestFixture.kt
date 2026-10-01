package jp.co.yaz.kanuchi.presentation.dailyinput

import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.work.DeleteWorkRecordUseCase
import jp.co.yaz.kanuchi.domain.work.GetCurrentUserMonthlyWorkSheetUseCase
import jp.co.yaz.kanuchi.domain.work.GetMonthlyWorkSheetUseCase
import jp.co.yaz.kanuchi.domain.work.GetWorkDayEntryUseCase
import jp.co.yaz.kanuchi.domain.work.SaveWorkRecordUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeCompanyHolidayRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeWorkRecordRepository
import jp.co.yaz.kanuchi.presentation.testing.FixedClock
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 日次入力の ViewModel のテストで使う偽物のリポジトリと、それを使うユースケース。「今日」は [TODAY] に固定する。 */
internal class DailyInputTestFixture {
    val profileRepository = FakeProfileRepository()
    val projectRepository = FakeProjectRepository()
    val workRecordRepository = FakeWorkRecordRepository()
    val shiftSettingsRepository = FakeShiftSettingsRepository()
    val companyHolidayRepository = FakeCompanyHolidayRepository()

    private val clock = FixedClock(Instant.parse("2026-10-15T03:00:00Z"))
    private val getMonthlyWorkSheetUseCase =
        GetMonthlyWorkSheetUseCase(workRecordRepository, shiftSettingsRepository, companyHolidayRepository, clock)

    fun createListViewModel() =
        DailyInputViewModel(
            GetTodayUseCase(clock),
            GetCurrentUserMonthlyWorkSheetUseCase(profileRepository, getMonthlyWorkSheetUseCase),
        )

    fun createDayViewModel(date: LocalDate) =
        DailyInputDayViewModel(
            date,
            GetWorkDayEntryUseCase(profileRepository, projectRepository, getMonthlyWorkSheetUseCase),
            SaveWorkRecordUseCase(workRecordRepository),
            DeleteWorkRecordUseCase(workRecordRepository),
        )

    companion object {
        /** 2026-10-15 (木)。 */
        val TODAY = LocalDate(2026, 10, 15)
        val USER_ID = FakeProfileRepository.MEMBER.id
    }
}
