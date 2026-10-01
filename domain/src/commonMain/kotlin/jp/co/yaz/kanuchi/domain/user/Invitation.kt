package jp.co.yaz.kanuchi.domain.user

/**
 * 招待リスト (`invitations`) に登録されたメールアドレス。
 *
 * @property email 招待したメールアドレス (DB 側で小文字・前後空白なしに正規化されている)
 * @property hasAccount そのメールアドレスのアカウントが既に作成されている (本人が1回以上ログインした) か
 */
data class Invitation(
    val email: String,
    val hasAccount: Boolean,
)
