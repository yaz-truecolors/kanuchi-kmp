package jp.co.yaz.kanuchi.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import jp.co.yaz.kanuchi.domain.auth.AuthRepository
import jp.co.yaz.kanuchi.domain.auth.EmailAddress
import kotlinx.coroutines.CancellationException

/**
 * このアプリで使うSupabaseクライアントのシングルトン生成処理。
 * Koinから1度だけ生成され、Auth/Postgrestを利用するRepository実装に注入される。
 */
internal fun createKanuchiSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = SupabaseConfig.PROJECT_URL,
        supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }

/**
 * [jp.co.yaz.kanuchi.domain.auth.AuthRepository] のSupabase実装。
 * マジックリンク送信は Auth の OTP プロバイダ (メールリンク方式) を利用する。
 */
internal class SupabaseAuthRepository(
    private val supabaseClient: SupabaseClient,
) : AuthRepository {
    override suspend fun sendMagicLink(email: EmailAddress): Result<Unit> =
        try {
            supabaseClient.auth.signInWith(OTP) {
                this.email = email.value
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            // 構造化された並行処理のキャンセルはそのまま再送出し、握りつぶさない。
            throw e
        } catch (e: AuthRestException) {
            // errorDescriptionはSupabase Authが定義するユーザー向けの説明文なので安全に表示できる。
            // 一方 e.message / e.toString() には認証ヘッダーを含む生のHTTPレスポンス詳細が
            // 含まれるため、絶対にUIへそのまま渡さないこと。
            Result.failure(Exception(e.errorDescription))
        } catch (e: Exception) {
            Result.failure(Exception(GENERIC_ERROR_MESSAGE))
        }

    private companion object {
        const val GENERIC_ERROR_MESSAGE = "マジックリンクの送信に失敗しました。しばらくしてから再度お試しください。"
    }
}
