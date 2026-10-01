package jp.co.yaz.kanuchi.data.role

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.data.profile.toDbValue
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.role.UserRoleRepository
import kotlinx.serialization.Serializable

/**
 * [UserRoleRepository] のSupabase実装。`profiles.role` を PostgREST で update する。
 *
 * 更新できる行は RLS (`profiles_update_own_or_admin`) で制限され、権限の変更はトリガー
 * (`enforce_role_change_permission`: admin 以外の変更を拒否、`guard_active_admins`: 自分自身の降格・
 * 利用中の admin が0人になる降格を拒否) がさらに検証する。
 */
internal class SupabaseUserRoleRepository(
    private val supabaseClient: SupabaseClient,
) : UserRoleRepository {
    override suspend fun changeRole(
        userId: String,
        role: UserRole,
    ): Result<Unit> {
        val updated =
            runCatchingData {
                supabaseClient
                    .from(TABLE)
                    .update({ set("role", role.toDbValue()) }) {
                        // 更新後の行を返させて、0行 (RLS で更新できなかった) を検出する
                        select(Columns.list("id"))
                        filter { eq("id", userId) }
                    }.decodeList<UpdatedRowDto>()
            }
        return updated.fold(
            onSuccess = { rows -> roleChangeResult(rows.size) },
            onFailure = { failure ->
                val restException = failure.cause as? PostgrestRestException
                val known =
                    restException?.let { toRoleChangeFailure(code = it.code, message = it.error, cause = it) }
                Result.failure(known ?: failure)
            },
        )
    }

    @Serializable
    private data class UpdatedRowDto(
        val id: String,
    )

    private companion object {
        const val TABLE = "profiles"
    }
}
