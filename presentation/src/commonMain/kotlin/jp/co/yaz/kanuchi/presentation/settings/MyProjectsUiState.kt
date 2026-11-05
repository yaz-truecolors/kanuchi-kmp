package jp.co.yaz.kanuchi.presentation.settings

import jp.co.yaz.kanuchi.domain.project.Project

/**
 * 個人の案件の追加に失敗した理由。Composable 側で strings.xml の文言に変換して表示する。
 */
enum class PersonalProjectAddError {
    /** 名前が空 (空白のみ)。 */
    BLANK_NAME,

    /** 通信エラー等、それ以外の理由。 */
    SAVE_FAILED,
}

/**
 * 個人の案件の削除に失敗した理由。Composable 側で strings.xml の文言に変換して表示する。
 */
enum class PersonalProjectDeleteError {
    /** 今月以降の日に工数が記録されている。 */
    IN_USE,

    /** 通信エラー等、それ以外の理由。 */
    DELETE_FAILED,
}

/**
 * 案件の編集画面 (個人設定から開く) のUI状態。
 */
data class MyProjectsUiState(
    /** 本人に割り当てられた有効な共有の案件 (案件名の順)。表示するだけで、変更・削除はできない。 */
    val assignedProjects: List<Project> = emptyList(),
    /** 本人の削除していない個人の案件 (案件名の順)。 */
    val personalProjects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    /** 追加する個人の案件名の入力内容。 */
    val newProjectName: String = "",
    val isAdding: Boolean = false,
    val addError: PersonalProjectAddError? = null,
    /** 削除の確認ダイアログを表示している個人の案件。 */
    val deleteTarget: Project? = null,
    /** 削除中の個人の案件のID。 */
    val deletingProjectId: String? = null,
    /** 削除に失敗した個人の案件のIDと理由。 */
    val deleteFailedProjectId: String? = null,
    val deleteError: PersonalProjectDeleteError? = null,
) {
    /** いずれかの保存 (追加・削除) の処理中か。処理中は他の保存を受け付けない。 */
    val isSaving: Boolean get() = isAdding || deletingProjectId != null

    /**
     * 個人の案件を追加・削除できるか。一覧の取得に成功するまでは操作させない
     * (取得中に追加すると、後から返った一覧で追加した案件が画面から消えるため)。
     */
    val canEdit: Boolean get() = !isLoading && !loadFailed && !isSaving
}
