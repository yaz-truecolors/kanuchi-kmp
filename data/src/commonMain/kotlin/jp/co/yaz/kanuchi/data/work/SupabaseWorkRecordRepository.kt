package jp.co.yaz.kanuchi.data.work

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.domain.work.WorkRecordRepository
import kotlinx.datetime.YearMonth

/**
 * [WorkRecordRepository] のSupabase実装。`work_records` と、埋め込みで `allocations` を PostgREST で参照する。
 * 参照できるのは本人の行と、admin なら全員の行 (RLS)。参照できない行はエラーにならず、結果に含まれない。
 */
internal class SupabaseWorkRecordRepository(
    private val supabaseClient: SupabaseClient,
) : WorkRecordRepository {
    override suspend fun getWorkRecords(
        userId: String,
        yearMonth: YearMonth,
    ): Result<List<WorkRecord>> =
        runCatchingData {
            supabaseClient
                .from(TABLE)
                .select(Columns.raw(WorkRecordDto.COLUMNS)) {
                    filter {
                        eq("user_id", userId)
                        gte("work_date", yearMonth.firstDay.toString())
                        lte("work_date", yearMonth.lastDay.toString())
                    }
                    order("work_date", Order.ASCENDING)
                }.decodeList<WorkRecordDto>()
                .map { it.toDomain() }
        }

    private companion object {
        const val TABLE = "work_records"
    }
}
