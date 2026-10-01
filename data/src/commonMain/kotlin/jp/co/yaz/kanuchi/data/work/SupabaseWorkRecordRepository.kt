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

    override suspend fun getWorkRecordsOfAllUsers(yearMonth: YearMonth): Result<Map<String, List<WorkRecord>>> =
        runCatchingData {
            // PostgREST は1回の応答の行数に上限 (max_rows) があるため、ページに分けて全件を取得する
            val rows = mutableListOf<UserWorkRecordDto>()
            do {
                val page =
                    supabaseClient
                        .from(TABLE)
                        .select(Columns.raw(UserWorkRecordDto.COLUMNS)) {
                            filter {
                                gte("work_date", yearMonth.firstDay.toString())
                                lte("work_date", yearMonth.lastDay.toString())
                            }
                            // ページの境目で行が重複・欠落しないよう、一意な並び順 (user_id + work_date は unique) にする
                            order("user_id", Order.ASCENDING)
                            order("work_date", Order.ASCENDING)
                            range(rows.size.toLong(), rows.size.toLong() + PAGE_SIZE - 1)
                        }.decodeList<UserWorkRecordDto>()
                rows += page
            } while (page.size == PAGE_SIZE)
            rows.toWorkRecordsByUserId()
        }

    private companion object {
        const val TABLE = "work_records"

        // supabase/config.toml の max_rows (Supabase の既定値) と同じ。これより小さい上限の環境では途中までしか取得できない
        const val PAGE_SIZE = 1000
    }
}
