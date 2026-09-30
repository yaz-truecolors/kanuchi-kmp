package jp.co.yaz.kanuchi.domain.auth

/**
 * メール内のマジックリンクを開いてアプリに戻ってきた際に、ログインできなかった理由。
 *
 * Supabase Auth はリンクの検証に失敗すると、リダイレクト先URLの `#error_code=...` に理由を付けて返す。
 * URLはリンクを開いた人が自由に書き換えられるため、URL内の説明文 (`error_description`) は表示に使わず、
 * 種類だけをこの型で表す。表示する文言への変換は presentation 層の責務とする。
 */
enum class MagicLinkCallbackError {
    /** リンクの有効期限切れ、または使用済み (マジックリンクは1回しか使えない)。 */
    EXPIRED,

    /** それ以外の理由。 */
    UNKNOWN,
}
