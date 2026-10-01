package jp.co.yaz.kanuchi.presentation.role

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole

/**
 * 権限管理画面のUI状態。
 */
data class RolesUiState(
    /** 一覧に表示するユーザー。読み込み前・読み込み失敗時は空。 */
    val users: List<RoleUserItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    /** 確認ダイアログで確認中の変更。null ならダイアログを表示しない。 */
    val pendingChange: RoleChange? = null,
    /** 保存中の変更。保存中は他の変更を受け付けない。 */
    val savingChange: RoleChange? = null,
    /** 直前に成功した変更 (完了の文言を表示する)。 */
    val completedChange: RoleChange? = null,
    val saveError: RoleChangeError? = null,
) {
    val isSaving: Boolean get() = savingChange != null
}

/**
 * 一覧の1行。
 *
 * @property isCurrentUser ログイン中のユーザー (自分) か
 * @property canChangeRole 権限を変更できるか (自分自身・利用停止中のユーザーは変更できない)
 */
data class RoleUserItem(
    val profile: UserProfile,
    val isCurrentUser: Boolean,
    val canChangeRole: Boolean,
)

/**
 * 権限の変更 (対象のユーザーと変更後の権限)。
 */
data class RoleChange(
    val user: UserProfile,
    val newRole: UserRole,
)

/** 権限の変更に失敗した理由 (Composable 側で文言に変換する)。 */
enum class RoleChangeError {
    /** 利用中の admin が1人もいなくなるため拒否された */
    LAST_ACTIVE_ADMIN_REQUIRED,

    /** 通信エラー等、具体的な理由を表示できない失敗 */
    GENERIC,
}
