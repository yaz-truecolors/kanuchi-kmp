package jp.co.yaz.kanuchi.presentation.home

/**
 * ホーム画面 (ログイン後の画面) のUI状態。
 *
 * 現時点ではログイン中のユーザーの表示とログアウトのみ。日次入力・集計等は今後追加する。
 */
data class HomeUiState(
    /** ログイン中のユーザーのメールアドレス。取得できない場合は null。 */
    val email: String? = null,
    val isSigningOut: Boolean = false,
    val signOutFailed: Boolean = false,
)
