package jp.co.yaz.kanuchi.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import jp.co.yaz.kanuchi.domain.auth.AuthRepository
import jp.co.yaz.kanuchi.domain.auth.EmailAddress
import jp.co.yaz.kanuchi.domain.auth.EmailNotInvitedException
import jp.co.yaz.kanuchi.domain.auth.GenericAuthFailureException
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
 * 招待リストに無いメールアドレスでのアカウント作成を拒否した際に、
 * Before User Created Hook (supabase/migrations の hook_before_user_created) が返す固定の識別子。
 * Supabase Auth はこの値を `msg` としてそのまま返し、supabase-kt では
 * [AuthRestException.errorDescription] に入る。SQL側の値と必ず一致させること。
 */
private const val EMAIL_NOT_INVITED_HOOK_MESSAGE = "email_not_invited"

/**
 * [jp.co.yaz.kanuchi.domain.auth.AuthRepository] のSupabase実装。
 * マジックリンク送信は Auth の OTP プロバイダ (メールリンク方式) を利用する。
 */
internal class SupabaseAuthRepository(
    private val supabaseClient: SupabaseClient,
) : AuthRepository {
    // AuthRepository の契約上、失敗は例外を送出せず必ず Result で返す。supabase-kt はネットワーク・
    // シリアライズ等で多様な例外を投げ得るため、境界で Exception を一括捕捉して GenericAuthFailureException に
    // 変換する意図的な実装であり、TooGenericExceptionCaught のみ抑制する。
    @Suppress("TooGenericExceptionCaught")
    override suspend fun sendMagicLink(email: EmailAddress): Result<Unit> =
        try {
            supabaseClient.auth.signInWith(OTP) {
                this.email = email.value
                // 招待済み(invitationsに登録済み)の未登録ユーザーは、初回のマジックリンク要求時に
                // アカウントが作成される必要があるため createUser = true とする。
                // 招待されていないメールアドレスでのアカウント作成は、サーバー側の
                // Before User Created Hook が拒否する (クライアント側の設定には依存しない)。
                createUser = true
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            // 構造化された並行処理のキャンセルはそのまま再送出し、握りつぶさない。
            throw e
        } catch (e: AuthRestException) {
            if (e.isEmailNotInvited()) {
                Result.failure(EmailNotInvitedException())
            } else {
                // errorDescriptionはSupabase Authが定義するユーザー向けの説明文なので安全に表示できる。
                // 一方 e.message / e.toString() には認証ヘッダーを含む生のHTTPレスポンス詳細が
                // 含まれるため、絶対にUIへそのまま渡さないこと。
                Result.failure(Exception(e.errorDescription))
            }
        } catch (e: Exception) {
            // data層はUI表示用の文言を持たない。汎用エラーメッセージへの変換はpresentation層に委ねる。
            // 元の例外は調査用に cause として保持する (message は持たないためUIには表示されない)。
            Result.failure(GenericAuthFailureException(e))
        }
}

/**
 * 「このメールアドレスではアカウントを作成できない」ことを示すエラーかどうか。
 *
 * - Hook による拒否: 招待リストに無い (通常運用時)
 * - [AuthErrorCode.SignupDisabled]: Supabase側で新規登録自体が無効 (Hook有効化前の移行期間など)
 * - [AuthErrorCode.OtpDisabled]: createUser = false で未登録のメールアドレスを指定した場合
 */
private fun AuthRestException.isEmailNotInvited(): Boolean =
    errorDescription == EMAIL_NOT_INVITED_HOOK_MESSAGE ||
        errorCode == AuthErrorCode.SignupDisabled ||
        errorCode == AuthErrorCode.OtpDisabled
