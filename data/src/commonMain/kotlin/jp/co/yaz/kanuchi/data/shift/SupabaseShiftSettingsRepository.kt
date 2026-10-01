package jp.co.yaz.kanuchi.data.shift

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsRepository

/**
 * [ShiftSettingsRepository] のSupabase実装。`shift_settings` テーブルを PostgREST で参照・保存する。
 * 参照・保存できるのは本人の行だけ (RLS の `shift_settings_*_own`)。
 */
internal class SupabaseShiftSettingsRepository(
    private val supabaseClient: SupabaseClient,
) : ShiftSettingsRepository {
    override suspend fun getShiftSettings(): Result<ShiftSettings?> =
        runCatchingData {
            supabaseClient
                .from(TABLE)
                .select(Columns.list(ShiftSettingsDto.COLUMNS)) {
                    filter { eq("user_id", currentUserId()) }
                }.decodeSingleOrNull<ShiftSettingsDto>()
                ?.toDomain()
        }

    override suspend fun saveShiftSettings(settings: ShiftSettings): Result<ShiftSettings> =
        runCatchingData {
            // 行がまだ無ければ追加、あれば上書きする (user_id は unique)。保存後の行を受け取って返す
            supabaseClient
                .from(TABLE)
                .upsert(settings.toDto(currentUserId())) {
                    onConflict = "user_id"
                    select(Columns.list(ShiftSettingsDto.COLUMNS))
                }.decodeSingle<ShiftSettingsDto>()
                .toDomain()
        }

    private fun currentUserId(): String = checkNotNull(supabaseClient.auth.currentUserOrNull()?.id) { "not signed in" }

    private companion object {
        const val TABLE = "shift_settings"
    }
}
