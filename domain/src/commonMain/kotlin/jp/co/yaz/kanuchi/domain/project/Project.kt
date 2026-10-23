package jp.co.yaz.kanuchi.domain.project

/**
 * 案件。DB の `projects` テーブルの1行に対応する。
 *
 * 案件には2種類ある。
 * - 共有の案件 ([ownerId] が null): admin が作成・管理し、担当メンバーを割り当てる。削除せず、使わなくなったら無効
 *   ([isActive] = false) にする (日々の工数配分 `allocations` から参照されている案件は削除できないため)。
 * - 個人案件 ([ownerId] がある): 各ユーザーが自分だけのために作成・削除する (他のユーザーには影響しない)。
 *   削除は論理削除 ([isDeleted]) で、先月までの工数は集計で引き続き見られる。名前は他の案件と重複してよい。
 *
 * @property id 案件ID
 * @property name 案件名 (共有の案件の中では一意。個人案件の名前は重複してよい)
 * @property isActive 有効か。無効な案件は新たな工数入力の対象にしない (個人案件は常に有効)
 * @property ownerId 個人案件を作成したユーザーのID。共有の案件なら null
 * @property isDeleted 削除した個人案件か。削除した案件は新たな工数入力の対象にしない
 */
data class Project(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val ownerId: String? = null,
    val isDeleted: Boolean = false,
) {
    /** 個人案件か (共有の案件なら false)。 */
    val isPersonal: Boolean get() = ownerId != null

    companion object {
        /** 一覧に表示する順。有効な案件を先に、それぞれ案件名の順に並べる。 */
        val DISPLAY_ORDER: Comparator<Project> =
            compareByDescending<Project> { it.isActive }.thenBy { it.name }
    }
}
