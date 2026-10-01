package jp.co.yaz.kanuchi.domain.user

import jp.co.yaz.kanuchi.domain.profile.UserProfile

/**
 * ユーザー管理画面に表示する内容 (全ユーザーと招待リスト)。
 *
 * @property currentUserId ログイン中のユーザー (操作している admin) のID。自分自身は利用停止できないため、区別に使う
 * @property users 全ユーザー (利用停止中を含む)
 * @property invitations 招待リスト
 */
data class UserManagementOverview(
    val currentUserId: String,
    val users: List<UserProfile>,
    val invitations: List<Invitation>,
)
