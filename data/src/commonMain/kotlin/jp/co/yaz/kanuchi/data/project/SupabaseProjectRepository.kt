package jp.co.yaz.kanuchi.data.project

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import jp.co.yaz.kanuchi.data.profile.runCatchingData
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.DuplicateProjectNameException
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.project.ProjectName
import jp.co.yaz.kanuchi.domain.project.ProjectRepository

/**
 * [ProjectRepository] のSupabase実装。`projects`・`user_projects` テーブルを PostgREST で参照・変更する。
 *
 * 変更は RLS で admin のみに制限されている。権限の無い insert はエラーになるが、update・delete はエラーにならず
 * 0行の変更になる (data.instructions.md の「DB アクセス (PostgREST) の実装パターン」) ため、
 * 変更後の行を受け取って件数を確かめ、足りなければ失敗にする。
 */
internal class SupabaseProjectRepository(
    private val supabaseClient: SupabaseClient,
) : ProjectRepository {
    override suspend fun getProjects(): Result<List<Project>> =
        runCatchingData {
            supabaseClient
                .from(PROJECTS)
                .select(Columns.list(ProjectDto.COLUMNS)) {
                    order("name", Order.ASCENDING)
                }.decodeList<ProjectDto>()
                .map { it.toDomain() }
        }

    override suspend fun getProject(projectId: String): Result<Project> =
        runCatchingData {
            supabaseClient
                .from(PROJECTS)
                .select(Columns.list(ProjectDto.COLUMNS)) {
                    filter { eq("id", projectId) }
                }.decodeSingle<ProjectDto>()
                .toDomain()
        }

    override suspend fun addProject(name: ProjectName): Result<Project> =
        runCatchingProjectNameSave {
            supabaseClient
                .from(PROJECTS)
                .insert(NewProjectDto(name = name.value)) {
                    select(Columns.list(ProjectDto.COLUMNS))
                }.decodeSingle<ProjectDto>()
                .toDomain()
        }

    override suspend fun renameProject(
        projectId: String,
        name: ProjectName,
    ): Result<Project> =
        runCatchingProjectNameSave {
            updateProject(projectId) { set("name", name.value) }
        }

    override suspend fun setProjectActive(
        projectId: String,
        isActive: Boolean,
    ): Result<Project> =
        runCatchingData {
            updateProject(projectId) { set("is_active", isActive) }
        }

    override suspend fun getAssignedUserIds(projectId: String): Result<Set<String>> = runCatchingData { selectAssignedUserIds(projectId) }

    override suspend fun setAssignedUserIds(
        projectId: String,
        userIds: Set<String>,
    ): Result<Unit> =
        runCatchingData {
            // 現在の割当との差分だけを追加・削除する (変更の無い割当の created_at を保つため)
            val current = selectAssignedUserIds(projectId)
            val toAdd = userIds - current
            val toRemove = current - userIds
            if (toAdd.isNotEmpty()) {
                supabaseClient
                    .from(USER_PROJECTS)
                    .insert(toAdd.map { UserProjectDto(userId = it, projectId = projectId) })
            }
            if (toRemove.isNotEmpty()) {
                val deleted =
                    supabaseClient
                        .from(USER_PROJECTS)
                        .delete {
                            select(Columns.list(UserProjectDto.COLUMNS))
                            filter {
                                eq("project_id", projectId)
                                isIn("user_id", toRemove.toList())
                            }
                        }.decodeList<UserProjectDto>()
                check(deleted.size == toRemove.size) { "assignments were not deleted (not permitted?)" }
            }
        }

    /** 案件を1件更新し、更新後の行を返す。更新できなかった (0行の) 場合は decodeSingle が例外を投げる。 */
    private suspend fun updateProject(
        projectId: String,
        values: PostgrestUpdate.() -> Unit,
    ): Project =
        supabaseClient
            .from(PROJECTS)
            .update(values) {
                select(Columns.list(ProjectDto.COLUMNS))
                filter { eq("id", projectId) }
            }.decodeSingle<ProjectDto>()
            .toDomain()

    private suspend fun selectAssignedUserIds(projectId: String): Set<String> =
        supabaseClient
            .from(USER_PROJECTS)
            .select(Columns.list(UserProjectDto.COLUMNS)) {
                filter { eq("project_id", projectId) }
            }.decodeList<UserProjectDto>()
            .map { it.userId }
            .toSet()

    /** 案件名の保存 (追加・名前変更)。[runCatchingData] に加え、名前の重複を [DuplicateProjectNameException] に変換する。 */
    private suspend fun <T> runCatchingProjectNameSave(block: suspend () -> T): Result<T> {
        val result = runCatchingData(block)
        val failure = result.exceptionOrNull() as? GenericDataFailureException ?: return result
        return Result.failure(projectNameSaveFailure(failure, (failure.cause as? PostgrestRestException)?.code))
    }

    private companion object {
        const val PROJECTS = "projects"
        const val USER_PROJECTS = "user_projects"
    }
}
