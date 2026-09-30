package jp.co.yaz.kanuchi.domain.auth

/**
 * アプリから見たログイン状態。
 * 画面の出し分け (ログイン画面 / ログイン後の画面) はこの状態だけを見て行う。
 */
sealed interface AuthState {
    /**
     * ログイン状態を確認中。起動直後 (保存済みセッションの読み込み・マジックリンクの処理中) と、
     * 期限切れのセッションの更新をネットワークエラー等で再試行している間がこれにあたる。
     */
    data object Unknown : AuthState

    /** ログイン済み。 */
    data class SignedIn(
        val user: AuthenticatedUser,
    ) : AuthState

    /** 未ログイン (ログアウト済みを含む)。 */
    data object SignedOut : AuthState
}

/**
 * ログイン中のユーザー。
 *
 * @property id Supabase Auth のユーザーID (`auth.users.id`、`profiles.id` と同じ値)
 * @property email ログインに使ったメールアドレス。マジックリンク認証では常に存在するが、
 *   Supabase Auth の仕様上は省略され得るため nullable にしている。
 */
data class AuthenticatedUser(
    val id: String,
    val email: String?,
)
