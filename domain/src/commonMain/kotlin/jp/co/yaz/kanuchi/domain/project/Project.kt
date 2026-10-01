package jp.co.yaz.kanuchi.domain.project

/**
 * 案件。DB の `projects` テーブルの1行に対応する。
 *
 * 案件は削除せず、使わなくなったら無効 ([isActive] = false) にする
 * (日々の工数配分 `allocations` から参照されている案件は削除できないため)。
 *
 * @property id 案件ID
 * @property name 案件名 (チーム内で一意)
 * @property isActive 有効か。無効な案件は新たな工数入力の対象にしない
 */
data class Project(
    val id: String,
    val name: String,
    val isActive: Boolean,
) {
    companion object {
        /** 一覧に表示する順。有効な案件を先に、それぞれ案件名の順に並べる。 */
        val DISPLAY_ORDER: Comparator<Project> =
            compareByDescending<Project> { it.isActive }.thenBy { it.name }
    }
}
