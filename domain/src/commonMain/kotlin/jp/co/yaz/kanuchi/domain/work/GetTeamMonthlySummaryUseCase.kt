package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.WorkCalendar
import jp.co.yaz.kanuchi.domain.calendar.todayInJapan
import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import jp.co.yaz.kanuchi.domain.project.ProjectRepository
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.YearMonth
import kotlin.time.Clock

/**
 * チームの月の集計 ([TeamMonthlySummary]) を取得するユースケース。管理者ダッシュボードで使う。
 *
 * 全ユーザーのプロフィール・稼働記録・勤務時間設定・その月の会社の休業日・案件を、それぞれ1回ずつまとめて取得して組み立てる
 * (メンバーごとに [GetMonthlyWorkSheetUseCase] を呼ぶと、メンバー数に比例して問い合わせが増えるため)。
 * どれかの取得に失敗した場合は、その失敗を返す。
 * 全員分を取得できるのは admin だけ (DB のアクセス制御 (RLS) で強制している。member が呼ぶと本人の分だけになる)。
 */
class GetTeamMonthlySummaryUseCase(
    private val profileRepository: ProfileRepository,
    private val workRecordRepository: WorkRecordRepository,
    private val shiftSettingsRepository: ShiftSettingsRepository,
    private val companyHolidayRepository: CompanyHolidayRepository,
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(yearMonth: YearMonth): Result<TeamMonthlySummary> =
        coroutineScope {
            val profiles = async { profileRepository.getProfiles() }
            val records = async { workRecordRepository.getWorkRecordsOfAllUsers(yearMonth) }
            val shiftSettings = async { shiftSettingsRepository.getShiftSettingsOfAllUsers() }
            val companyHolidays = async { companyHolidayRepository.getCompanyHolidays(yearMonth.firstDay, yearMonth.lastDay) }
            val projects = async { projectRepository.getProjects() }
            Result.success(
                TeamMonthlySummary.create(
                    yearMonth = yearMonth,
                    profiles = profiles.await().getOrElse { return@coroutineScope Result.failure(it) },
                    recordsByUserId = records.await().getOrElse { return@coroutineScope Result.failure(it) },
                    shiftSettingsByUserId = shiftSettings.await().getOrElse { return@coroutineScope Result.failure(it) },
                    workCalendar = WorkCalendar(companyHolidays.await().getOrElse { return@coroutineScope Result.failure(it) }),
                    today = clock.todayInJapan(),
                    projects = projects.await().getOrElse { return@coroutineScope Result.failure(it) },
                ),
            )
        }
}
