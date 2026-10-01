package jp.co.yaz.kanuchi.domain.profile

/**
 * ユーザーのプロフィール。DB の `profiles` テーブルの1行に対応する。
 *
 * @property id ユーザーID (Supabase Auth のユーザーIDと同じ)
 * @property displayName 表示名。初期値はメールアドレスの `@` より前の部分で、本人が変更できる
 */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}
