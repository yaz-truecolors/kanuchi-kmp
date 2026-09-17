package jp.co.yaz.kanuchi.presentation.auth

/**
 * ログイン画面 (マジックリンク送信フロー) のUI状態。
 *
 * v1スコープ: メールアドレス入力→送信→確認メッセージ表示まで。
 * セッション復元 (メール内リンクのクリック後の状態) は別タスクで扱う。
 */
data class LoginUiState(
    val email: String = "",
    val isSending: Boolean = false,
    val sentSuccessfully: Boolean = false,
    val errorMessage: String? = null,
)
