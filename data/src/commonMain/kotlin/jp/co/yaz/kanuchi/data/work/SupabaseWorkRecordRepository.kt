package jp.co.yaz.kanuchi.data.work

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import jp.co.yaz.kanuchi.data.common.hoursOf
import jp.co.yaz.kanuchi.data.common.toDbValue
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.domain.work.WorkRecordRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * [WorkRecordRepository] のSupabase実装。`work_records` と、埋め込みで `allocations` を PostgREST で参照する。
 * 参照できるのは本人の行と、admin なら全員の行 (RLS)。参照できない行はエラーにならず、結果に含まれない。
 * 保存・削除できるのは本人の行だけ (RLS)。本人のユーザーIDの行だけを対象にするため、RLS で拒否されることは無い
 * (配分の upsert・delete は念のため変更した行数を確かめる)。
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

    override suspend fun saveWorkRecord(record: WorkRecord): Result<Unit> =
        runCatchingData {
            // 1. その日の記録を追加または上書きし (user_id + work_date が unique)、行のIDを受け取る
            val workRecordId =
                supabaseClient
                    .from(TABLE)
                    .upsert(record.toSaveDto(currentUserId())) {
                        onConflict = "user_id,work_date"
                        select(Columns.list("id"))
                    }.decodeSingle<WorkRecordIdDto>()
                    .id
            // 2. 保存済みの配分との差分だけを追加・変更・削除する。途中で失敗しても、同じ内容で保存し直せば揃う
            val current =
                supabaseClient
                    .from(ALLOCATIONS)
                    .select(Columns.list("project_id", "hours")) {
                        filter { eq("work_record_id", workRecordId) }
                    }.decodeList<AllocationDto>()
                    .associate { it.projectId to hoursOf(it.hours) }
            val changes = allocationChangesOf(current, record.allocations)
            if (changes.toDelete.isNotEmpty()) {
                val deleted =
                    supabaseClient
                        .from(ALLOCATIONS)
                        .delete {
                            select(Columns.list("project_id", "hours"))
                            filter {
                                eq("work_record_id", workRecordId)
                                isIn("project_id", changes.toDelete.toList())
                            }
                        }.decodeList<AllocationDto>()
                check(deleted.size == changes.toDelete.size) { "allocations were not deleted (not permitted?)" }
            }
            if (changes.toUpsert.isNotEmpty()) {
                val upserted =
                    supabaseClient
                        .from(ALLOCATIONS)
                        .upsert(
                            changes.toUpsert.map { (projectId, hours) ->
                                AllocationSaveDto(workRecordId = workRecordId, projectId = projectId, hours = hours.toDbValue())
                            },
                        ) {
                            onConflict = "work_record_id,project_id"
                            select(Columns.list("project_id", "hours"))
                        }.decodeList<AllocationDto>()
                check(upserted.size == changes.toUpsert.size) { "allocations were not saved (not permitted?)" }
            }
        }

    override suspend fun deleteWorkRecord(date: LocalDate): Result<Unit> =
        runCatchingData {
            // 配分は外部キーの on delete cascade で一緒に消える。記録が無い (0行の) 場合も、消えた状態なので成功とする
            supabaseClient
                .from(TABLE)
                .delete {
                    filter {
                        eq("user_id", currentUserId())
                        eq("work_date", date.toString())
                    }
                }
            Unit
        }

    private fun currentUserId(): String = checkNotNull(supabaseClient.auth.currentUserOrNull()?.id) { "not signed in" }

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
        const val ALLOCATIONS = "allocations"

        // supabase/config.toml の max_rows (Supabase の既定値) と同じ。これより小さい上限の環境では途中までしか取得できない
        const val PAGE_SIZE = 1000
    }
}
