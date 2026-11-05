package jp.co.yaz.kanuchi.data.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.DuplicateProjectNameException
import jp.co.yaz.kanuchi.domain.project.PersonalProjectInUseException
import jp.co.yaz.kanuchi.domain.project.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `projects` テーブルの1行 (PostgREST のレスポンス)。
 * [ownerId] は個人案件の作成者 (共有の案件なら null)、[deletedAt] は個人案件を削除した日時 (削除していなければ null)。
 */
@Serializable
internal data class ProjectDto(
    val id: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    companion object {
        /** PostgREST で取得する列。テーブルに列が増えても、必要な列だけを取得する。 */
        val COLUMNS = listOf("id", "name", "is_active", "owner_id", "deleted_at")
    }
}

internal fun ProjectDto.toDomain(): Project =
    Project(id = id, name = name, isActive = isActive, ownerId = ownerId, isDeleted = deletedAt != null)

/** `projects` に追加する共有の案件の行 (id・is_active 等は DB の既定値を使う。owner_id を送らないので null になる)。 */
@Serializable
internal data class NewProjectDto(
    val name: String,
)

/** `projects` に追加する個人案件の行 ([ownerId] はログイン中のユーザー自身。RLS で本人以外は拒否される)。 */
@Serializable
internal data class NewPersonalProjectDto(
    val name: String,
    @SerialName("owner_id") val ownerId: String,
)

/** `user_projects` テーブルの1行 (担当メンバーの割当)。 */
@Serializable
internal data class UserProjectDto(
    @SerialName("user_id") val userId: String,
    @SerialName("project_id") val projectId: String,
) {
    companion object {
        val COLUMNS = listOf("user_id", "project_id")
    }
}

/** Postgres の unique 制約違反のエラーコード (PostgREST はこれを `code` として返す)。 */
internal const val POSTGRES_UNIQUE_VIOLATION = "23505"

/**
 * 案件名を保存 (追加・名前変更) したときの失敗を変換する。`projects` の unique 制約は `name` だけなので、
 * unique 制約違反 ([postgresErrorCode] が [POSTGRES_UNIQUE_VIOLATION]) は「同じ名前の案件がある」とみなす。
 */
internal fun projectNameSaveFailure(
    failure: GenericDataFailureException,
    postgresErrorCode: String?,
): Exception =
    if (postgresErrorCode == POSTGRES_UNIQUE_VIOLATION) {
        DuplicateProjectNameException(failure.cause)
    } else {
        failure
    }

/** `raise exception` の既定の SQLSTATE (関数 delete_personal_project が使う) */
private const val POSTGRES_RAISE_EXCEPTION = "P0001"

/** 関数 delete_personal_project が、当月以降の日に工数がある個人案件の削除を拒否したときのメッセージ */
private const val PERSONAL_PROJECT_IN_USE = "personal_project_in_use"

/**
 * 個人案件を削除したときの失敗を変換する。当月以降の日に工数があるため削除できなかった場合
 * ([postgresErrorCode] が [POSTGRES_RAISE_EXCEPTION] で [message] が [PERSONAL_PROJECT_IN_USE]) は
 * [PersonalProjectInUseException] にし、それ以外は [failure] のままにする。
 */
internal fun personalProjectDeleteFailure(
    failure: GenericDataFailureException,
    postgresErrorCode: String?,
    message: String?,
): Exception =
    if (postgresErrorCode == POSTGRES_RAISE_EXCEPTION && message == PERSONAL_PROJECT_IN_USE) {
        PersonalProjectInUseException(failure.cause)
    } else {
        failure
    }
