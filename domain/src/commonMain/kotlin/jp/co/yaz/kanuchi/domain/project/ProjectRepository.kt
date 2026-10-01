package jp.co.yaz.kanuchi.domain.project

/**
 * 案件と担当メンバーの割当を扱うRepositoryインターフェース。
 * 実装はdata層 (SupabaseProjectRepository) が提供する。
 *
 * 失敗時は、特に記載が無い限り [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す
 * (presentation 層が文言に変換して表示する)。
 * 参照は全員ができるが、変更は admin だけができる (DB のアクセス制御 (RLS) で強制している)。
 * 権限が無く変更できなかった場合も失敗として返す。
 */
interface ProjectRepository {
    /** すべての案件 (無効な案件を含む) を取得する。並び順は保証しない。 */
    suspend fun getProjects(): Result<List<Project>>

    /** 案件を1件取得する。 */
    suspend fun getProject(projectId: String): Result<Project>

    /**
     * 有効な案件を追加し、追加した案件を返す。
     * 同じ名前の案件が既にある場合は [DuplicateProjectNameException] を返す。
     */
    suspend fun addProject(name: ProjectName): Result<Project>

    /**
     * 案件の名前を変更し、変更後の案件を返す。
     * 同じ名前の案件が既にある場合は [DuplicateProjectNameException] を返す。
     */
    suspend fun renameProject(
        projectId: String,
        name: ProjectName,
    ): Result<Project>

    /** 案件の有効／無効を切り替え、変更後の案件を返す。 */
    suspend fun setProjectActive(
        projectId: String,
        isActive: Boolean,
    ): Result<Project>

    /** 案件の担当メンバー (ユーザーID) を取得する。 */
    suspend fun getAssignedUserIds(projectId: String): Result<Set<String>>

    /**
     * 案件の担当メンバーを [userIds] のとおりに設定する (含まれないユーザーの割当は外す)。
     * 途中で失敗した場合、一部だけが反映されていることがある (同じ内容で再度呼べば、残りが反映される)。
     */
    suspend fun setAssignedUserIds(
        projectId: String,
        userIds: Set<String>,
    ): Result<Unit>
}
