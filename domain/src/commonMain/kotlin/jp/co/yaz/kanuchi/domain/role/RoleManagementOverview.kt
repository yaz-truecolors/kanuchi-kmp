package jp.co.yaz.kanuchi.domain.role

import jp.co.yaz.kanuchi.domain.profile.UserProfile

/**
 * 権限管理画面に表示する内容。
 *
 * @property currentUserId ログイン中のユーザー (操作している admin) のID
 * @property users 全ユーザー (利用停止中を含む)
 */
data class RoleManagementOverview(
    val currentUserId: String,
    val users: List<UserProfile>,
) {
    /**
     * [user] の権限を画面から変更できるか。
     *
     * - 自分自身は変更できない (自分の降格は DB 側でも拒否される。admin が権限管理画面を使うため、自分の昇格も不要)
     * - 利用停止中のユーザーは変更できない (DB 側では許可されるが、利用停止中は admin としての権限を持たないため、
     *   変更しても意味が無く、紛らわしい。必要ならユーザー管理画面で復帰させてから変更する)
     */
    fun canChangeRole(user: UserProfile): Boolean = user.id != currentUserId && !user.isSuspended
}
