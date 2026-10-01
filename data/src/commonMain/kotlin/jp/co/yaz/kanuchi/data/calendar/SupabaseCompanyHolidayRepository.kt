package jp.co.yaz.kanuchi.data.calendar

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayRepository
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import kotlinx.datetime.LocalDate

/**
 * [CompanyHolidayRepository] のSupabase実装。`company_holidays` テーブルを PostgREST で参照・変更する。
 *
 * 参照は全員、追加・削除は admin のみ (RLS)。権限の無い insert はエラーになるが、delete はエラーにならず
 * 0行の削除になるため、削除した行を受け取って件数を確かめる。
 */
internal class SupabaseCompanyHolidayRepository(
    private val supabaseClient: SupabaseClient,
) : CompanyHolidayRepository {
    override suspend fun getCompanyHolidays(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<CompanyHoliday>> =
        runCatchingData {
            supabaseClient
                .from(TABLE)
                .select(Columns.list(CompanyHolidayDto.COLUMNS)) {
                    filter {
                        gte("holiday_date", from.toString())
                        lte("holiday_date", to.toString())
                    }
                    order("holiday_date", Order.ASCENDING)
                }.decodeList<CompanyHolidayDto>()
                .map { it.toDomain() }
        }

    override suspend fun addCompanyHoliday(holiday: CompanyHoliday): Result<CompanyHoliday> {
        val result =
            runCatchingData {
                supabaseClient
                    .from(TABLE)
                    .insert(holiday.toDto()) {
                        select(Columns.list(CompanyHolidayDto.COLUMNS))
                    }.decodeSingle<CompanyHolidayDto>()
                    .toDomain()
            }
        val failure = result.exceptionOrNull() as? GenericDataFailureException ?: return result
        return Result.failure(companyHolidayAddFailure(failure, (failure.cause as? PostgrestRestException)?.code))
    }

    override suspend fun deleteCompanyHoliday(date: LocalDate): Result<Unit> =
        runCatchingData {
            val deleted =
                supabaseClient
                    .from(TABLE)
                    .delete {
                        select(Columns.list(CompanyHolidayDto.COLUMNS))
                        filter { eq("holiday_date", date.toString()) }
                    }.decodeList<CompanyHolidayDto>()
            check(deleted.size == 1) { "company holiday was not deleted (not found or not permitted?)" }
        }

    private companion object {
        const val TABLE = "company_holidays"
    }
}
