package jp.co.yaz.kanuchi.domain.profile

/**
 * ユーザーのプロフィール。DB の `profiles` テーブルの1行に対応する。
 *
 * @property id ユーザーID (Supabase Auth のユーザーIDと同じ)
 * @property displayName 表示名。初期値はメールアドレスの `@` より前の部分で、本人が変更できる
 * @property isSuspended 利用停止中か。利用停止中のユーザーはログインできない (admin がユーザー管理画面で停止・復帰させる)
 */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val isSuspended: Boolean = false,
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}
