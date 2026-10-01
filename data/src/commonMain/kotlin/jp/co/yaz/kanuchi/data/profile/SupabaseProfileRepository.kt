package jp.co.yaz.kanuchi.data.profile

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import kotlinx.coroutines.CancellationException

/**
 * [ProfileRepository] のSupabase実装。`profiles` テーブルを PostgREST で参照する。
 * 参照できる行は RLS (`profiles_select_own_or_admin`) で制限される。
 */
internal class SupabaseProfileRepository(
    private val supabaseClient: SupabaseClient,
) : ProfileRepository {
    override suspend fun getCurrentUserProfile(): Result<UserProfile> =
        runCatchingData {
            val userId = checkNotNull(supabaseClient.auth.currentUserOrNull()?.id) { "not signed in" }
            supabaseClient
                .from(TABLE)
                .select(Columns.list(ProfileDto.COLUMNS)) {
                    filter { eq("id", userId) }
                }.decodeSingle<ProfileDto>()
                .toDomain()
        }

    override suspend fun getProfiles(): Result<List<UserProfile>> =
        runCatchingData {
            supabaseClient
                .from(TABLE)
                .select(Columns.list(ProfileDto.COLUMNS)) {
                    order("display_name", Order.ASCENDING)
                }.decodeList<ProfileDto>()
                .map { it.toDomain() }
        }

    private companion object {
        const val TABLE = "profiles"
    }
}

/**
 * DB アクセスを実行し、失敗を [GenericDataFailureException] に変換して [Result] で返す。
 *
 * supabase-kt の例外 (`RestException` 等) の message には HTTP の詳細が含まれ得るため、そのまま UI に出さない
 * (data.instructions.md の「例外の扱い」)。キャンセルは握りつぶさずに再送出する。
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runCatchingData(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(GenericDataFailureException(e))
    }
