package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.calendar.WorkCalendar
import jp.co.yaz.kanuchi.domain.calendar.todayInJapan
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.YearMonth
import kotlin.time.Clock

/**
 * ユーザーの月次シート ([MonthlyWorkSheet]) を取得するユースケース。日次入力・案件別集計・管理者ダッシュボードで使う。
 *
 * 稼働記録・本人の勤務時間設定 (未保存なら [ShiftSettings.DEFAULT])・その月の会社の休業日・日本時間の今日 ([clock]) を
 * 集めて組み立てる。どれかの取得に失敗した場合は、その失敗を返す。
 * 他のユーザーの分は admin だけが取得できる (DB のアクセス制御 (RLS) で強制している。member が他人の分を取得すると、
 * 記録が空・勤務時間設定が初期値のシートになる)。
 */
class GetMonthlyWorkSheetUseCase(
    private val workRecordRepository: WorkRecordRepository,
    private val shiftSettingsRepository: ShiftSettingsRepository,
    private val companyHolidayRepository: CompanyHolidayRepository,
    private val clock: Clock,
) {
    /**
     * @param userId 対象のユーザーID (ログイン中のユーザー自身の分は、そのユーザーID を渡す)
     * @param yearMonth 対象の月
     */
    suspend operator fun invoke(
        userId: String,
        yearMonth: YearMonth,
    ): Result<MonthlyWorkSheet> =
        coroutineScope {
            val records = async { workRecordRepository.getWorkRecords(userId, yearMonth) }
            val shiftSettings = async { shiftSettingsRepository.getShiftSettingsOf(userId) }
            val companyHolidays = async { companyHolidayRepository.getCompanyHolidays(yearMonth.firstDay, yearMonth.lastDay) }
            Result.success(
                MonthlyWorkSheet.create(
                    yearMonth = yearMonth,
                    records = records.await().getOrElse { return@coroutineScope Result.failure(it) },
                    shiftSettings = shiftSettings.await().getOrElse { return@coroutineScope Result.failure(it) } ?: ShiftSettings.DEFAULT,
                    workCalendar = WorkCalendar(companyHolidays.await().getOrElse { return@coroutineScope Result.failure(it) }),
                    today = clock.todayInJapan(),
                ),
            )
        }
}
