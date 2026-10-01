package jp.co.yaz.kanuchi.domain.project

import jp.co.yaz.kanuchi.domain.profile.UserProfile

/**
 * 案件の担当メンバーの割当。担当メンバーの設定画面に表示する内容。
 *
 * @property project 対象の案件
 * @property users 割当の候補となるユーザー (参照できるすべてのユーザー)
 * @property assignedUserIds 担当メンバーとして割り当てられているユーザーのID
 */
data class ProjectAssignment(
    val project: Project,
    val users: List<UserProfile>,
    val assignedUserIds: Set<String>,
)
