package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.calendar.FakeCompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.FixedClock
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.profile.FakeProfileRepository
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.project.FakeProjectRepository
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.shift.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GetTeamMonthlySummaryUseCaseTest {
    private val member = UserProfile(id = "user-1", email = "taro@example.com", displayName = "taro", role = UserRole.MEMBER)
    private val admin = UserProfile(id = "user-2", email = "hanako@example.com", displayName = "hanako", role = UserRole.ADMIN)
    private val project = Project(id = "p", name = "案件", isActive = true)

    private val profileRepository = FakeProfileRepository().apply { profilesResult = Result.success(listOf(admin, member)) }
    private val workRecordRepository = FakeWorkRecordRepository()
    private val shiftSettingsRepository = FakeShiftSettingsRepository()
    private val companyHolidayRepository = FakeCompanyHolidayRepository()
    private val projectRepository = FakeProjectRepository().apply { projectsResult = Result.success(listOf(project)) }

    // 日本時間 2026-10-02 0:30 (UTC では 10/1)
    private val useCase =
        GetTeamMonthlySummaryUseCase(
            profileRepository,
            workRecordRepository,
            shiftSettingsRepository,
            companyHolidayRepository,
            projectRepository,
            FixedClock("2026-10-01T15:30:00Z"),
        )

    private val october = YearMonth(2026, 10)

    @Test
    fun `builds the summary from the data of all users fetched at once`() =
        runTest {
            val shift = ShiftSettings.DEFAULT.copy(breakHours = Hours.ZERO)
            shiftSettingsRepository.getOfAllUsersResult = Result.success(mapOf(member.id to shift))
            workRecordRepository.records[member.id] =
                listOf(
                    WorkRecord(LocalDate(2026, 10, 1), allocations = mapOf("p" to Hours.ofHundredths(900))),
                    WorkRecord(LocalDate(2026, 9, 30), allocations = mapOf("p" to Hours.ofHundredths(100))),
                )
            companyHolidayRepository.holidays += CompanyHoliday(LocalDate(2026, 10, 30), "創立記念日")

            val summary = useCase(october).getOrThrow()

            assertEquals(listOf(october), workRecordRepository.requestedMonthsOfAllUsers)
            assertEquals(listOf(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31)), companyHolidayRepository.requestedRanges)
            assertEquals(listOf(admin, member), summary.members.map { it.profile })
            val memberSheet = summary.members.single { it.profile == member }.sheet
            assertEquals(shift, memberSheet.shiftSettings)
            assertEquals(DayKind.CompanyHoliday("創立記念日"), memberSheet.dayOf(LocalDate(2026, 10, 30))?.dayKind)
            // 今日は日本時間の 10/2 なので 10/1・10/2 が合計対象 (休憩 0 で 9 時間 × 2)
            assertEquals(Hours.ofHundredths(1_800), memberSheet.totalWorkingHours)
            // 設定を保存していない admin は初期値 (休憩 1 時間で 8 時間 × 2)
            assertEquals(
                Hours.ofHundredths(1_600),
                summary.members
                    .single { it.profile == admin }
                    .sheet.totalWorkingHours,
            )
            assertEquals(listOf(TeamProjectHours("p", project, Hours.ofHundredths(900))), summary.projectHours)
        }

    @Test
    fun `returns the failure of any repository`() =
        runTest {
            val profileError = IllegalStateException("profiles")
            profileRepository.profilesResult = Result.failure(profileError)
            assertSame(profileError, useCase(october).exceptionOrNull())
            profileRepository.profilesResult = Result.success(listOf(member))

            val recordError = IllegalStateException("records")
            workRecordRepository.getAllFailure = recordError
            assertSame(recordError, useCase(october).exceptionOrNull())
            workRecordRepository.getAllFailure = null

            val shiftError = IllegalStateException("shift")
            shiftSettingsRepository.getOfAllUsersResult = Result.failure(shiftError)
            assertSame(shiftError, useCase(october).exceptionOrNull())
            shiftSettingsRepository.getOfAllUsersResult = Result.success(emptyMap())

            val holidayError = IllegalStateException("holidays")
            companyHolidayRepository.getFailure = holidayError
            assertSame(holidayError, useCase(october).exceptionOrNull())
            companyHolidayRepository.getFailure = null

            val projectError = IllegalStateException("projects")
            projectRepository.projectsResult = Result.failure(projectError)
            assertSame(projectError, useCase(october).exceptionOrNull())
        }
}
