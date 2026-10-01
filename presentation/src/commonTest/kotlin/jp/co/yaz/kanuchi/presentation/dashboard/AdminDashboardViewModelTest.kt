package jp.co.yaz.kanuchi.presentation.dashboard

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.work.GetTeamMonthlySummaryUseCase
import jp.co.yaz.kanuchi.domain.work.TeamProjectHours
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.domain.work.WorkRecordRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AdminDashboardViewModelTest : MainDispatcherTest() {
    private val member = FakeProfileRepository.MEMBER
    private val admin = FakeProfileRepository.ADMIN
    private val october = YearMonth(2026, 10)
    private val september = YearMonth(2026, 9)

    private val profileRepository = FakeProfileRepository().apply { profilesResult = Result.success(listOf(admin, member)) }
    private val workRecordRepository = FakeWorkRecordRepository()
    private val projectRepository = FakeProjectRepository()

    // 日本時間 2026-10-02 0:30 (UTC では 10/1)
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-01T15:30:00Z")
        }

    private fun createViewModel() =
        AdminDashboardViewModel(
            GetTeamMonthlySummaryUseCase(
                profileRepository,
                workRecordRepository,
                FakeShiftSettingsRepository(),
                FakeCompanyHolidayRepository(),
                projectRepository,
                clock,
            ),
            GetTodayUseCase(clock),
        )

    @Test
    fun `summary of the current month in japan is loaded initially`() {
        workRecordRepository.records[october] =
            mapOf(
                member.id to
                    listOf(
                        WorkRecord(LocalDate(2026, 10, 1), allocations = mapOf(FakeProjectRepository.ACTIVE.id to Hours.ofHundredths(300))),
                    ),
            )

        val state = createViewModel().uiState.value

        assertEquals(october, state.yearMonth)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        val summary = assertNotNull(state.summary)
        assertEquals(october, summary.yearMonth)
        assertEquals(listOf(admin, member), summary.members.map { it.profile })
        assertEquals(
            listOf(TeamProjectHours(FakeProjectRepository.ACTIVE.id, FakeProjectRepository.ACTIVE, Hours.ofHundredths(300))),
            summary.projectHours,
        )
        assertEquals(listOf(october), workRecordRepository.requestedMonths)
    }

    @Test
    fun `selecting another month loads the summary of that month`() {
        val viewModel = createViewModel()

        viewModel.onMonthSelected(september)

        val state = viewModel.uiState.value
        assertEquals(september, state.yearMonth)
        assertEquals(september, state.summary?.yearMonth)
        assertEquals(listOf(october, september), workRecordRepository.requestedMonths)
    }

    @Test
    fun `selecting the same month does not reload`() {
        val viewModel = createViewModel()

        viewModel.onMonthSelected(october)

        assertEquals(listOf(october), workRecordRepository.requestedMonths)
    }

    @Test
    fun `loading state is shown while the summary is loaded`() {
        val gate = CompletableDeferred<Unit>()
        workRecordRepository.gates[october] = gate
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.summary)

        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `result of the previous month does not overwrite the selected month`() {
        val octoberGate = CompletableDeferred<Unit>()
        workRecordRepository.gates[october] = octoberGate
        val viewModel = createViewModel()

        viewModel.onMonthSelected(september)
        octoberGate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(september, state.yearMonth)
        assertEquals(september, state.summary?.yearMonth)
        assertFalse(state.isLoading)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        profileRepository.profilesResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertNull(viewModel.uiState.value.summary)

        profileRepository.profilesResult = Result.success(listOf(member))
        viewModel.onRetryClicked()

        val state = viewModel.uiState.value
        assertFalse(state.loadFailed)
        assertEquals(listOf(member), state.summary?.members?.map { it.profile })
    }
}

/** 管理者ダッシュボードのテスト用の [WorkRecordRepository] の偽物 (全員分の取得だけを使う)。 */
private class FakeWorkRecordRepository : WorkRecordRepository {
    /** 月ごとの全員分の記録。 */
    val records = mutableMapOf<YearMonth, Map<String, List<WorkRecord>>>()

    /** 設定すると、その月の取得はこれが完了するまで待つ (読み込み中の状態を確かめる用)。 */
    val gates = mutableMapOf<YearMonth, CompletableDeferred<Unit>>()
    val requestedMonths = mutableListOf<YearMonth>()

    override suspend fun getWorkRecords(
        userId: String,
        yearMonth: YearMonth,
    ): Result<List<WorkRecord>> = Result.success(records[yearMonth]?.get(userId).orEmpty())

    override suspend fun getWorkRecordsOfAllUsers(yearMonth: YearMonth): Result<Map<String, List<WorkRecord>>> {
        requestedMonths += yearMonth
        gates[yearMonth]?.await()
        return Result.success(records[yearMonth].orEmpty())
    }

    override suspend fun saveWorkRecord(record: WorkRecord): Result<Unit> = error("not used")

    override suspend fun deleteWorkRecord(date: LocalDate): Result<Unit> = error("not used")
}

/** 管理者ダッシュボードのテスト用の [CompanyHolidayRepository] の偽物 (休業日なし)。 */
private class FakeCompanyHolidayRepository : CompanyHolidayRepository {
    override suspend fun getCompanyHolidays(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<CompanyHoliday>> = Result.success(emptyList())

    override suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday> = Result.success(holiday)

    override suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit> = Result.success(Unit)
}
