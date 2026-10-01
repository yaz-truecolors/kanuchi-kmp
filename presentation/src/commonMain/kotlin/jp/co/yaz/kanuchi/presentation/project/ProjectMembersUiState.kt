package jp.co.yaz.kanuchi.presentation.project

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.project.Project

/**
 * 案件の担当メンバー画面のUI状態。
 */
data class ProjectMembersUiState(
    /** 対象の案件。読み込み前・読み込み失敗時は null。 */
    val project: Project? = null,
    /** 割当の候補となるユーザー (全ユーザー)。 */
    val users: List<UserProfile> = emptyList(),
    /** 画面で選択されている担当メンバーのユーザーID。 */
    val selectedUserIds: Set<String> = emptySet(),
    /** 保存済み (DB 上) の担当メンバーのユーザーID。 */
    val savedUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** 保存に成功したことを表示するか (選択を変えると消える)。 */
    val showsSavedMessage: Boolean = false,
) {
    /** 選択が保存済みの内容から変わっているか。変わっている場合のみ保存できる。 */
    val hasChanges: Boolean get() = selectedUserIds != savedUserIds
}
