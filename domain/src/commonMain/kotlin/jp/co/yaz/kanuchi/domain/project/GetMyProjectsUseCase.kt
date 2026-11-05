package jp.co.yaz.kanuchi.domain.project

import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * ログイン中のユーザーが工数を記録する案件 ([MyProjects]) を取得するユースケース。
 * いずれかの取得に失敗した場合は、その失敗を返す。
 */
class GetMyProjectsUseCase(
    private val projectRepository: ProjectRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): Result<MyProjects> =
        coroutineScope {
            val profileResult = async { profileRepository.getCurrentUserProfile() }
            val projectsResult = async { projectRepository.getProjects() }
            val assignedResult = async { projectRepository.getAssignedProjectIdsOfCurrentUser() }
            val userId = profileResult.await().getOrElse { return@coroutineScope Result.failure(it) }.id
            val projects = projectsResult.await().getOrElse { return@coroutineScope Result.failure(it) }
            val assignedIds = assignedResult.await().getOrElse { return@coroutineScope Result.failure(it) }

            Result.success(
                MyProjects(
                    assignedProjects =
                        projects
                            .filter { !it.isPersonal && it.isActive && it.id in assignedIds }
                            .sortedBy { it.name },
                    // admin は他のユーザーの個人案件も参照できるため、作成者で絞り込む
                    personalProjects =
                        projects
                            .filter { it.ownerId == userId && !it.isDeleted }
                            .sortedBy { it.name },
                ),
            )
        }
}
