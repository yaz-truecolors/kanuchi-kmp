package jp.co.yaz.kanuchi.presentation.project

import jp.co.yaz.kanuchi.domain.project.Project

/**
 * 案件名の保存 (追加・名前変更) に失敗した理由。Composable 側で strings.xml の文言に変換して表示する。
 */
enum class ProjectNameSaveError {
    /** 名前が空 (空白のみ)。 */
    BLANK_NAME,

    /** 同じ名前の案件がある。 */
    DUPLICATE_NAME,

    /** 通信エラー等、それ以外の理由。 */
    SAVE_FAILED,
}

/**
 * 案件管理画面のUI状態。
 */
data class ProjectsUiState(
    /** すべての案件 (無効な案件を含む)。一覧に表示する順 ([Project.DISPLAY_ORDER]) に並べる。 */
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    /** 追加する案件名の入力内容。 */
    val newProjectName: String = "",
    val isAdding: Boolean = false,
    val addError: ProjectNameSaveError? = null,
    /** 名前を変更中 (入力欄を表示中) の案件のID。 */
    val renamingProjectId: String? = null,
    /** 変更後の案件名の入力内容。 */
    val renamingName: String = "",
    val renameError: ProjectNameSaveError? = null,
    /** 名前の変更・有効／無効の切り替えを保存中の案件のID。 */
    val savingProjectId: String? = null,
    /** 有効／無効の切り替えに失敗した案件のID。 */
    val activeChangeFailedProjectId: String? = null,
) {
    /** いずれかの保存 (追加・名前変更・有効／無効の切り替え) の処理中か。処理中は他の保存を受け付けない。 */
    val isSaving: Boolean get() = isAdding || savingProjectId != null

    /**
     * 案件を追加できるか。一覧の取得に成功するまでは追加させない
     * (取得中に追加すると、後から返った一覧で追加した案件が画面から消えるため)。
     */
    val canAdd: Boolean get() = !isLoading && !loadFailed && !isSaving
}
