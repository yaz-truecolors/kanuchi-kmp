package jp.co.yaz.kanuchi.domain.project

/**
 * 案件と担当メンバーの割当を扱うRepositoryインターフェース。
 * 実装はdata層 (SupabaseProjectRepository) が提供する。
 *
 * 失敗時は、特に記載が無い限り [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す
 * (presentation 層が文言に変換して表示する)。
 * 共有の案件 ([Project.ownerId] が null) は全員が参照でき、変更は admin だけができる。
 * 個人案件は作成したユーザー本人と admin が参照でき、作成・削除は本人だけができる
 * (いずれも DB のアクセス制御 (RLS・関数) で強制している)。権限が無く変更できなかった場合も失敗として返す。
 */
interface ProjectRepository {
    /**
     * 参照できるすべての案件 (無効な案件・削除した個人案件を含む) を取得する。並び順は保証しない。
     * 個人案件は、ログイン中のユーザー自身の分 (admin なら全員の分) を含む。
     */
    suspend fun getProjects(): Result<List<Project>>

    /** 案件を1件取得する。 */
    suspend fun getProject(projectId: String): Result<Project>

    /**
     * 有効な共有の案件を追加し、追加した案件を返す。
     * 同じ名前の案件が既にある場合は [DuplicateProjectNameException] を返す。
     */
    suspend fun addProject(name: ProjectName): Result<Project>

    /**
     * 共有の案件の名前を変更し、変更後の案件を返す。
     * 同じ名前の案件が既にある場合は [DuplicateProjectNameException] を返す。
     */
    suspend fun renameProject(
        projectId: String,
        name: ProjectName,
    ): Result<Project>

    /** 共有の案件の有効／無効を切り替え、変更後の案件を返す。 */
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

    /**
     * ログイン中のユーザー自身に割り当てられている案件のIDを取得する (無効な案件の割当も含む)。
     * 日次入力で、工数を配分できる案件を決めるのに使う。
     */
    suspend fun getAssignedProjectIdsOfCurrentUser(): Result<Set<String>>

    /**
     * ログイン中のユーザー自身の個人案件を追加し、追加した案件を返す。名前は他の案件と重複してよい。
     */
    suspend fun addPersonalProject(name: ProjectName): Result<Project>

    /**
     * ログイン中のユーザー自身の個人案件を削除する (論理削除。先月までの工数は残り、集計で引き続き見られる)。
     * 当月以降 (日本時間) の日に工数が配分されている場合は [PersonalProjectInUseException] を返す。
     * 削除済みの個人案件を指定した場合は何もせず成功する。
     */
    suspend fun deletePersonalProject(projectId: String): Result<Unit>
}
