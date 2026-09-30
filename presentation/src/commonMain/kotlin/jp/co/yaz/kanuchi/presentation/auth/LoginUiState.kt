package jp.co.yaz.kanuchi.presentation.auth

import jp.co.yaz.kanuchi.domain.auth.MagicLinkCallbackError

/**
 * ログイン画面 (マジックリンク送信フロー) のUI状態。
 */
data class LoginUiState(
    val email: String = "",
    val isSending: Boolean = false,
    val sentSuccessfully: Boolean = false,
    val errorMessage: String? = null,
    /**
     * メール内のマジックリンクを開いたがログインできずに戻ってきた場合の理由 (期限切れ等)。
     * 画面を開いた時に1回だけ設定され、メールアドレスの入力・送信で消える。
     */
    val magicLinkCallbackError: MagicLinkCallbackError? = null,
)
