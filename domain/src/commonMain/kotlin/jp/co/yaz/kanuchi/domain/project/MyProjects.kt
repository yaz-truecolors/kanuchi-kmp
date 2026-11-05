package jp.co.yaz.kanuchi.domain.project

/**
 * ログイン中のユーザーが工数を記録する案件。個人設定の案件の編集画面に表示する内容。
 *
 * @property assignedProjects 本人に割り当てられた有効な共有の案件 (案件名の順)。本人は変更・削除できない
 * @property personalProjects 本人の削除していない個人案件 (案件名の順)。本人が作成・削除できる
 */
data class MyProjects(
    val assignedProjects: List<Project>,
    val personalProjects: List<Project>,
)
