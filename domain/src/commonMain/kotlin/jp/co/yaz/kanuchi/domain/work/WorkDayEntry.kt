package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings

/**
 * 日次入力画面で1日分を入力するのに必要な情報 ([GetWorkDayEntryUseCase] で取得する)。
 *
 * @property day その日の行 (日の種類・保存済みの記録・今日以前の日か等)
 * @property shiftSettings 本人の勤務時間設定 (空欄の定時の値)
 * @property projects 工数を配分できる案件 ([AllocatableProject.DISPLAY_ORDER] の順)
 */
data class WorkDayEntry(
    val day: WorkDay,
    val shiftSettings: ShiftSettings,
    val projects: List<AllocatableProject>,
)

/**
 * 日次入力で工数を配分できる案件。本人に割り当てられた有効な共有の案件、本人の削除していない個人案件と、
 * その日に既に配分がある案件 (無効にされた・割当を外された・削除した案件でも、配分を直したり 0 にしたりできるよう含める)。
 *
 * @property canAddAllocation 新しく工数を配分できる案件か (本人に割り当てられた有効な共有の案件か、本人の削除していない個人案件)。
 *   false なら、既に配分があるため表示している案件
 */
data class AllocatableProject(
    val project: Project,
    val canAddAllocation: Boolean,
) {
    companion object {
        /** 表示する順。新しく配分できる案件を先に、それぞれ案件名の順に並べる。 */
        val DISPLAY_ORDER: Comparator<AllocatableProject> =
            compareByDescending<AllocatableProject> { it.canAddAllocation }.thenBy { it.project.name }
    }
}
