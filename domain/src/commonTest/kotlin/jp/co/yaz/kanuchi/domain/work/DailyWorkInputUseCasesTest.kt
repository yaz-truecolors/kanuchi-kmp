package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.calendar.FakeCompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.FixedClock
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.profile.FakeProfileRepository
import jp.co.yaz.kanuchi.domain.project.FakeProjectRepository
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.shift.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DailyWorkInputUseCasesTest {
    private val profileRepository = FakeProfileRepository()
    private val projectRepository = FakeProjectRepository()
    private val workRecordRepository = FakeWorkRecordRepository()
    private val shiftSettingsRepository = FakeShiftSettingsRepository()
    private val getMonthlyWorkSheetUseCase =
        GetMonthlyWorkSheetUseCase(
            workRecordRepository,
            shiftSettingsRepository,
            FakeCompanyHolidayRepository(),
            // 日本時間 2026-10-01
            FixedClock("2026-10-01T03:00:00Z"),
        )
    private val userId = FakeProfileRepository.PROFILE.id
    private val date = LocalDate(2026, 10, 1)

    private val assigned = Project(id = "p-assigned", name = "B案件", isActive = true)
    private val assignedInactive = Project(id = "p-inactive", name = "A案件", isActive = false)
    private val notAssigned = Project(id = "p-other", name = "C案件", isActive = true)
    private val allocatedNotAssigned = Project(id = "p-allocated", name = "D案件", isActive = true)

    @Test
    fun `current user monthly sheet uses the profile id`() =
        runTest {
            workRecordRepository.records[userId] = listOf(WorkRecord(date, flag = DayFlag.VACATION))
            val useCase = GetCurrentUserMonthlyWorkSheetUseCase(profileRepository, getMonthlyWorkSheetUseCase)

            val sheet = useCase(YearMonth(2026, 10)).getOrThrow()

            assertEquals(DayFlag.VACATION, sheet.dayOf(date)?.flag)
        }

    @Test
    fun `current user monthly sheet returns the profile failure`() =
        runTest {
            val error = IllegalStateException("profile")
            profileRepository.currentUserProfileResult = Result.failure(error)
            val useCase = GetCurrentUserMonthlyWorkSheetUseCase(profileRepository, getMonthlyWorkSheetUseCase)

            assertSame(error, useCase(YearMonth(2026, 10)).exceptionOrNull())
        }

    @Test
    fun `entry contains assigned active projects and already allocated projects`() =
        runTest {
            projectRepository.projectsResult = Result.success(listOf(notAssigned, assigned, assignedInactive, allocatedNotAssigned))
            projectRepository.assignedProjectIdsOfCurrentUserResult = Result.success(setOf(assigned.id, assignedInactive.id))
            workRecordRepository.records[userId] =
                listOf(WorkRecord(date, allocations = mapOf(allocatedNotAssigned.id to Hours.ofHundredths(100))))
            val shift = ShiftSettings.DEFAULT.copy(breakHours = Hours.ZERO)
            shiftSettingsRepository.getOfResults[userId] = Result.success(shift)

            val entry = GetWorkDayEntryUseCase(profileRepository, projectRepository, getMonthlyWorkSheetUseCase)(date).getOrThrow()

            assertEquals(date, entry.day.date)
            assertEquals(DayKind.Weekday, entry.day.dayKind)
            assertEquals(shift, entry.shiftSettings)
            assertEquals(
                listOf(AllocatableProject(assigned, isAssigned = true), AllocatableProject(allocatedNotAssigned, isAssigned = false)),
                entry.projects,
            )
        }

    @Test
    fun `entry returns the failure of any repository`() =
        runTest {
            val useCase = GetWorkDayEntryUseCase(profileRepository, projectRepository, getMonthlyWorkSheetUseCase)
            val error = IllegalStateException("assigned")
            projectRepository.assignedProjectIdsOfCurrentUserResult = Result.failure(error)
            assertSame(error, useCase(date).exceptionOrNull())

            projectRepository.assignedProjectIdsOfCurrentUserResult = Result.success(emptySet())
            val projectsError = IllegalStateException("projects")
            projectRepository.projectsResult = Result.failure(projectsError)
            assertSame(projectsError, useCase(date).exceptionOrNull())

            val profileError = IllegalStateException("profile")
            profileRepository.currentUserProfileResult = Result.failure(profileError)
            assertSame(profileError, useCase(date).exceptionOrNull())
        }

    @Test
    fun `valid input is saved`() =
        runTest {
            val entry = GetWorkDayEntryUseCase(profileRepository, projectRepository, getMonthlyWorkSheetUseCase)(date).getOrThrow()

            val result =
                SaveWorkRecordUseCase(workRecordRepository)(
                    entry,
                    WorkRecordInput(
                        clockIn = "10:00",
                        allocations =
                            mapOf(
                                "a" to "7",
                            ),
                    ),
                )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    WorkRecord(
                        date,
                        clockIn =
                            jp.co.yaz.kanuchi.domain.common.TimeOfDay
                                .of(10, 0),
                        allocations = mapOf("a" to Hours.ofHundredths(700)),
                    ),
                ),
                workRecordRepository.savedRecords,
            )
        }

    @Test
    fun `invalid input is not saved`() =
        runTest {
            val entry = GetWorkDayEntryUseCase(profileRepository, projectRepository, getMonthlyWorkSheetUseCase)(date).getOrThrow()

            val result = SaveWorkRecordUseCase(workRecordRepository)(entry, WorkRecordInput(clockIn = "x"))

            assertIs<InvalidWorkRecordException>(result.exceptionOrNull())
            assertTrue(workRecordRepository.savedRecords.isEmpty())
        }

    @Test
    fun `record is deleted`() =
        runTest {
            val result = DeleteWorkRecordUseCase(workRecordRepository)(date)

            assertTrue(result.isSuccess)
            assertEquals(listOf(date), workRecordRepository.deletedDates)
        }
}
