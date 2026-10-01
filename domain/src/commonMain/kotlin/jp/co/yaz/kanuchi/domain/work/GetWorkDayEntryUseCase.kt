package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import jp.co.yaz.kanuchi.domain.project.ProjectRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * ログイン中のユーザー自身の、日次入力画面で1日分を入力するのに必要な情報 ([WorkDayEntry]) を取得するユースケース。
 * いずれかの取得に失敗した場合は、その失敗を返す。
 */
class GetWorkDayEntryUseCase(
    private val profileRepository: ProfileRepository,
    private val projectRepository: ProjectRepository,
    private val getMonthlyWorkSheetUseCase: GetMonthlyWorkSheetUseCase,
) {
    suspend operator fun invoke(date: LocalDate): Result<WorkDayEntry> =
        coroutineScope {
            val projectsResult = async { projectRepository.getProjects() }
            val assignedResult = async { projectRepository.getAssignedProjectIdsOfCurrentUser() }
            val sheetResult =
                async {
                    profileRepository.getCurrentUserProfile().fold(
                        onSuccess = { getMonthlyWorkSheetUseCase(it.id, YearMonth(date.year, date.month)) },
                        onFailure = { Result.failure(it) },
                    )
                }
            val sheet = sheetResult.await().getOrElse { return@coroutineScope Result.failure(it) }
            val projects = projectsResult.await().getOrElse { return@coroutineScope Result.failure(it) }
            val assignedIds = assignedResult.await().getOrElse { return@coroutineScope Result.failure(it) }

            val day = checkNotNull(sheet.dayOf(date)) { "the sheet must contain $date" }
            val allocatedIds =
                day.record
                    ?.allocations
                    ?.keys
                    .orEmpty()
            val allocatable =
                projects
                    .mapNotNull { project ->
                        val isAssigned = project.isActive && project.id in assignedIds
                        if (isAssigned || project.id in allocatedIds) AllocatableProject(project, isAssigned) else null
                    }.sortedWith(AllocatableProject.DISPLAY_ORDER)
            Result.success(WorkDayEntry(day = day, shiftSettings = sheet.shiftSettings, projects = allocatable))
        }
}
