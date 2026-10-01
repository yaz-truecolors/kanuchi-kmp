package jp.co.yaz.kanuchi.presentation.summary

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.GetProfilesUseCase
import jp.co.yaz.kanuchi.domain.project.GetProjectsUseCase
import jp.co.yaz.kanuchi.domain.work.GetMonthlyWorkSheetUseCase
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.domain.work.WorkRecordRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository.Companion.ADMIN
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository.Companion.MEMBER
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectSummaryViewModelTest : MainDispatcherTest() {
    private val profileRepository = FakeProfileRepository()
    private val projectRepository = FakeProjectRepository()
    private val workRecordRepository = FakeWorkRecordRepository()
    private val companyHolidayRepository = FakeCompanyHolidayRepository()

    // 日本時間 2026-10-02 0:30 (UTC では 10/1)。今月は 2026年10月
    private val clock = FixedClock(Instant.parse("2026-10-01T15:30:00Z"))

    private val october = YearMonth(2026, 10)
    private val september = YearMonth(2026, 9)

    private fun createViewModel(
        userId: String? = null,
        yearMonthArg: String? = null,
    ) = ProjectSummaryViewModel(
        userId = userId,
        yearMonthArg = yearMonthArg,
        getCurrentUserProfileUseCase = GetCurrentUserProfileUseCase(profileRepository),
        getProfilesUseCase = GetProfilesUseCase(profileRepository),
        getMonthlyWorkSheetUseCase =
            GetMonthlyWorkSheetUseCase(workRecordRepository, FakeShiftSettingsRepository(), companyHolidayRepository, clock),
        getProjectsUseCase = GetProjectsUseCase(projectRepository),
        getTodayUseCase = GetTodayUseCase(clock),
    )

    @Test
    fun `own summary of this month is loaded with project names`() {
        workRecordRepository.records[MEMBER.id] =
            listOf(
                WorkRecord(
                    date = LocalDate(2026, 10, 1),
                    allocations =
                        mapOf(
                            FakeProjectRepository.ACTIVE.id to hours(300),
                            FakeProjectRepository.INACTIVE.id to hours(400),
                            UNKNOWN_PROJECT_ID to hours(100),
                        ),
                ),
            )

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(october, state.yearMonth)
        assertEquals(october, state.maxYearMonth)
        assertFalse(state.showsTargetUser)
        assertEquals(MEMBER, state.targetUser)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
        assertEquals(listOf(MEMBER.id to october), workRecordRepository.requests)
        val summary = state.summary!!
        assertEquals(october, summary.sheet.yearMonth)
        // 今日 (10/2) までの平日2日 × 定時8時間
        assertEquals(hours(1_600), summary.sheet.totalWorkingHours)
        // 実績時間の多い順。無効な案件・一覧に無い案件も含める
        assertEquals(
            listOf(FakeProjectRepository.INACTIVE, FakeProjectRepository.ACTIVE, null),
            summary.projectRows.map { it.project },
        )
        assertEquals(summary.sheet.projectWorkHours, summary.projectRows.map { it.workHours })
    }

    @Test
    fun `summary of the specified user is loaded and the user is shown`() {
        val viewModel = createViewModel(userId = ADMIN.id)
        val state = viewModel.uiState.value

        assertTrue(state.showsTargetUser)
        assertEquals(ADMIN, state.targetUser)
        assertEquals(listOf(ADMIN.id to october), workRecordRepository.requests)
        assertEquals(0, profileRepository.getCurrentUserProfileCallCount)
    }

    @Test
    fun `user that cannot be viewed is shown as an error without loading the sheet`() {
        // member が参照できるプロフィールは自分の分だけ
        profileRepository.profilesResult = Result.success(listOf(MEMBER))

        val viewModel = createViewModel(userId = ADMIN.id)
        val state = viewModel.uiState.value

        assertEquals(ProjectSummaryLoadError.USER_NOT_VIEWABLE, state.loadError)
        assertNull(state.targetUser)
        assertNull(state.summary)
        assertTrue(workRecordRepository.requests.isEmpty())
    }

    @Test
    fun `profile load failure is shown and can be retried`() {
        profileRepository.currentUserProfileResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertEquals(ProjectSummaryLoadError.LOAD_FAILED, viewModel.uiState.value.loadError)

        profileRepository.currentUserProfileResult = Result.success(MEMBER)
        viewModel.onRetryClicked()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals(MEMBER, viewModel.uiState.value.targetUser)
        assertEquals(
            october,
            viewModel.uiState.value.summary
                ?.sheet
                ?.yearMonth,
        )
    }

    @Test
    fun `sheet load failure is shown and can be retried`() {
        workRecordRepository.failure = GenericDataFailureException()
        val viewModel = createViewModel()

        assertEquals(ProjectSummaryLoadError.LOAD_FAILED, viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.summary)
        assertFalse(viewModel.uiState.value.isLoading)

        workRecordRepository.failure = null
        viewModel.onRetryClicked()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals(
            october,
            viewModel.uiState.value.summary
                ?.sheet
                ?.yearMonth,
        )
        // 対象のユーザーは取得済みなので取得し直さない
        assertEquals(1, profileRepository.getCurrentUserProfileCallCount)
    }

    @Test
    fun `project load failure is shown as a load failure`() {
        projectRepository.projectsResult = Result.failure(GenericDataFailureException())

        val viewModel = createViewModel()

        assertEquals(ProjectSummaryLoadError.LOAD_FAILED, viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `changing the month loads the summary of that month`() {
        val viewModel = createViewModel()

        viewModel.onMonthChanged(september)

        val state = viewModel.uiState.value
        assertEquals(september, state.yearMonth)
        assertEquals(september, state.summary?.sheet?.yearMonth)
        assertEquals(listOf(MEMBER.id to october, MEMBER.id to september), workRecordRepository.requests)
        assertEquals(1, profileRepository.getCurrentUserProfileCallCount)
    }

    @Test
    fun `month given as the argument is shown first`() {
        val viewModel = createViewModel(userId = ADMIN.id, yearMonthArg = "2026-09")

        assertEquals(september, viewModel.uiState.value.yearMonth)
        assertEquals(listOf(ADMIN.id to september), workRecordRepository.requests)
    }

    @Test
    fun `this month is shown when the month argument is invalid`() {
        val viewModel = createViewModel(yearMonthArg = "2026-13")

        assertEquals(october, viewModel.uiState.value.yearMonth)
        assertEquals(
            october,
            viewModel.uiState.value.summary
                ?.sheet
                ?.yearMonth,
        )
    }

    @Test
    fun `month argument outside the selectable range is clamped to the range`() {
        val future = createViewModel(yearMonthArg = "2026-11")
        val past = createViewModel(yearMonthArg = "1999-12")

        assertEquals(october, future.uiState.value.yearMonth)
        assertEquals(YearMonth(2000, 1), past.uiState.value.yearMonth)
        assertEquals(YearMonth(2000, 1), past.uiState.value.minYearMonth)
        assertEquals(listOf(MEMBER.id to october, MEMBER.id to YearMonth(2000, 1)), workRecordRepository.requests)
    }

    @Test
    fun `month outside the selectable range is not selected`() {
        val viewModel = createViewModel()

        viewModel.onMonthChanged(YearMonth(2026, 11))

        assertEquals(october, viewModel.uiState.value.yearMonth)
        assertEquals(1, workRecordRepository.requests.size)
    }

    @Test
    fun `selecting the same month does not reload`() {
        val viewModel = createViewModel()

        viewModel.onMonthChanged(october)

        assertEquals(1, workRecordRepository.requests.size)
    }

    @Test
    fun `summary is cleared while loading another month`() {
        val viewModel = createViewModel()
        val gate = CompletableDeferred<Unit>()
        workRecordRepository.gates[september] = gate

        viewModel.onMonthChanged(september)

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.summary)

        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            september,
            viewModel.uiState.value.summary
                ?.sheet
                ?.yearMonth,
        )
    }

    @Test
    fun `result of the previous month is discarded when the month is changed while loading`() {
        val octoberGate = CompletableDeferred<Unit>()
        workRecordRepository.gates[october] = octoberGate
        val viewModel = createViewModel()

        viewModel.onMonthChanged(september)
        octoberGate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(september, state.yearMonth)
        assertEquals(september, state.summary?.sheet?.yearMonth)
        assertFalse(state.isLoading)
    }

    private fun hours(hundredths: Int): Hours = Hours.ofHundredths(hundredths)

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }

    private class FakeWorkRecordRepository : WorkRecordRepository {
        /** ユーザーIDごとの稼働記録。 */
        val records = mutableMapOf<String, List<WorkRecord>>()

        /** getWorkRecords() を失敗させる場合の例外。 */
        var failure: Throwable? = null

        /** 月ごとに設定すると、その月の取得はこれが完了するまで待つ (読み込み中の状態を確かめる用)。 */
        val gates = mutableMapOf<YearMonth, CompletableDeferred<Unit>>()

        /** getWorkRecords() に渡されたユーザーIDと月。 */
        val requests = mutableListOf<Pair<String, YearMonth>>()

        override suspend fun getWorkRecords(
            userId: String,
            yearMonth: YearMonth,
        ): Result<List<WorkRecord>> {
            requests += userId to yearMonth
            gates[yearMonth]?.await()
            failure?.let { return Result.failure(it) }
            return Result.success(records[userId].orEmpty().filter { it.date.year == yearMonth.year && it.date.month == yearMonth.month })
        }
    }

    private class FakeCompanyHolidayRepository : CompanyHolidayRepository {
        override suspend fun getCompanyHolidays(
            from: LocalDate,
            to: LocalDate,
        ): Result<List<CompanyHoliday>> = Result.success(emptyList())

        override suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday> = error("not used")

        override suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit> = error("not used")
    }

    private companion object {
        const val UNKNOWN_PROJECT_ID = "deleted-project"
    }
}
