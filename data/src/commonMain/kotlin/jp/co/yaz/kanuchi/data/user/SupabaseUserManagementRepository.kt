package jp.co.yaz.kanuchi.data.user

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.domain.auth.EmailAddress
import jp.co.yaz.kanuchi.domain.user.UserManagementRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [UserManagementRepository] のSupabase実装。
 *
 * 招待リストは `invitations` テーブル (RLS で admin のみ参照・追加・削除可)、
 * 利用停止・復帰は RPC `set_user_suspended` (admin のみ実行可) を PostgREST で呼び出す。
 */
internal class SupabaseUserManagementRepository(
    private val supabaseClient: SupabaseClient,
) : UserManagementRepository {
    override suspend fun getInvitedEmails(): Result<List<String>> =
        runCatchingData {
            supabaseClient
                .from(INVITATIONS)
                .select(Columns.list("email")) {
                    order("created_at", Order.DESCENDING)
                }.decodeList<InvitationDto>()
                .map { it.email }
        }

    override suspend fun invite(email: EmailAddress): Result<Unit> =
        runCatchingUserManagement {
            // invited_by はトリガーが既定値 (auth.uid()) を入れ、メールアドレスは DB 側で小文字に正規化される
            supabaseClient.from(INVITATIONS).insert(InvitationDto(email = email.value))
        }

    override suspend fun revokeInvitation(email: String): Result<Unit> =
        runCatchingData {
            supabaseClient.from(INVITATIONS).delete {
                filter { eq("email", email) }
            }
        }

    override suspend fun setUserSuspended(
        userId: String,
        suspended: Boolean,
    ): Result<Unit> =
        runCatchingUserManagement {
            supabaseClient.postgrest.rpc(
                function = SET_USER_SUSPENDED,
                parameters =
                    buildJsonObject {
                        put("target_user_id", userId)
                        put("suspended", suspended)
                    },
            )
        }

    /**
     * [runCatchingData] と同様に実行し、DB が返した既知のエラー (一意制約違反・admin ガード) は
     * 対応する domain の例外に変換する。
     */
    private suspend fun runCatchingUserManagement(block: suspend () -> Unit): Result<Unit> =
        runCatchingData(block).recoverCatchingKnownFailure()

    private fun Result<Unit>.recoverCatchingKnownFailure(): Result<Unit> {
        val restException = exceptionOrNull()?.cause as? PostgrestRestException
        val known =
            restException?.let { toUserManagementFailure(code = it.code, message = it.error, cause = it) }
        return if (known != null) Result.failure(known) else this
    }

    @Serializable
    private data class InvitationDto(
        val email: String,
    )

    private companion object {
        const val INVITATIONS = "invitations"
        const val SET_USER_SUSPENDED = "set_user_suspended"
    }
}
