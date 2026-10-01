package jp.co.yaz.kanuchi.data.auth

import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.status.SessionStatus
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.AuthenticatedUser
import jp.co.yaz.kanuchi.domain.auth.MagicLinkCallbackError

/**
 * supabase-kt のセッション状態を、domain のログイン状態に変換する。
 *
 * - [SessionStatus.RefreshFailure] は、期限切れのセッションの更新をネットワークエラー等で再試行している状態
 *   (supabase-kt が自動で再試行し、成功すれば [SessionStatus.Authenticated] に戻る)。ログアウト扱いにすると
 *   一時的な通信断でログイン画面に戻されてしまうため、確認中 ([AuthState.Unknown]) として扱う。
 * - [SessionStatus.Authenticated] でもユーザー情報が無い場合 (マジックリンクから戻った直後の
 *   ユーザー情報の取得に失敗した場合) は、ログイン後の画面で使うユーザーIDが得られないため
 *   未ログイン ([AuthState.SignedOut]) として扱う。ログインし直せば解消する。
 */
internal fun SessionStatus.toAuthState(): AuthState =
    when (this) {
        SessionStatus.Initializing, is SessionStatus.RefreshFailure -> AuthState.Unknown
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        is SessionStatus.Authenticated -> {
            val user = session.user
            if (user == null) {
                AuthState.SignedOut
            } else {
                AuthState.SignedIn(AuthenticatedUser(id = user.id, email = user.email))
            }
        }
    }

/**
 * マジックリンクを開いて戻ってきたURLに含まれていたエラーを、domain のエラー種別に変換する。
 * URL内の説明文 (`errorDescription`) はリンクを開いた人が自由に書き換えられるため使わない。
 *
 * 利用停止中のユーザーもマジックリンクのメール自体は受け取れる (Supabase Auth は送信を拒否しない) が、
 * リンクを開くと Supabase Auth が `error_code=user_banned` を付けてアプリに戻す。
 */
@OptIn(SupabaseExperimental::class)
internal fun AuthEvent.OtpError.toMagicLinkCallbackError(): MagicLinkCallbackError =
    when (errorCode) {
        AuthErrorCode.OtpExpired -> MagicLinkCallbackError.EXPIRED
        // 利用停止中のユーザー (auth.users.banned_until が未来の日時。supabase/migrations の set_user_suspended 参照)
        AuthErrorCode.UserBanned -> MagicLinkCallbackError.SUSPENDED
        else -> MagicLinkCallbackError.UNKNOWN
    }
