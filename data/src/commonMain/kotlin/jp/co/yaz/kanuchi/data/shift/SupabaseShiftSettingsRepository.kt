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
 * 保存できるのは本人の行だけ (RLS の `shift_settings_*_own`)、参照できるのは本人の行と、admin なら全員の行
 * (RLS の `shift_settings_admin_select`)。
 */
internal class SupabaseShiftSettingsRepository(
    private val supabaseClient: SupabaseClient,
) : ShiftSettingsRepository {
    override suspend fun getShiftSettings(): Result<ShiftSettings?> = runCatchingData { fetchShiftSettingsOf(currentUserId()) }

    override suspend fun getShiftSettingsOf(userId: String): Result<ShiftSettings?> = runCatchingData { fetchShiftSettingsOf(userId) }

    private suspend fun fetchShiftSettingsOf(userId: String): ShiftSettings? =
        supabaseClient
            .from(TABLE)
            .select(Columns.list(ShiftSettingsDto.COLUMNS)) {
                filter { eq("user_id", userId) }
            }.decodeSingleOrNull<ShiftSettingsDto>()
            ?.toDomain()

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
