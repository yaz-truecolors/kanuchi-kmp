package jp.co.yaz.kanuchi.domain.project

import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 案件・割当の候補となるユーザー一覧・現在の担当メンバーをまとめて (並行して) 取得するユースケース。
 * いずれかの取得に失敗した場合は、その失敗を返す。
 */
class GetProjectAssignmentUseCase(
    private val projectRepository: ProjectRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(projectId: String): Result<ProjectAssignment> =
        coroutineScope {
            val projectResult = async { projectRepository.getProject(projectId) }
            val usersResult = async { profileRepository.getProfiles() }
            val assignedUserIdsResult = async { projectRepository.getAssignedUserIds(projectId) }
            val project = projectResult.await()
            val users = usersResult.await()
            val assignedUserIds = assignedUserIdsResult.await()

            val failure = listOf(project, users, assignedUserIds).firstNotNullOfOrNull { it.exceptionOrNull() }
            if (failure != null) {
                Result.failure(failure)
            } else {
                Result.success(ProjectAssignment(project.getOrThrow(), users.getOrThrow(), assignedUserIds.getOrThrow()))
            }
        }
}
