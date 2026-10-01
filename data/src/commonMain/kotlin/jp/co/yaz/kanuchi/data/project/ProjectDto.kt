package jp.co.yaz.kanuchi.data.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.DuplicateProjectNameException
import jp.co.yaz.kanuchi.domain.project.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `projects` テーブルの1行 (PostgREST のレスポンス)。
 */
@Serializable
internal data class ProjectDto(
    val id: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean,
) {
    companion object {
        /** PostgREST で取得する列。テーブルに列が増えても、必要な列だけを取得する。 */
        val COLUMNS = listOf("id", "name", "is_active")
    }
}

internal fun ProjectDto.toDomain(): Project = Project(id = id, name = name, isActive = isActive)

/** `projects` に追加する行 (id・is_active 等は DB の既定値を使う)。 */
@Serializable
internal data class NewProjectDto(
    val name: String,
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
