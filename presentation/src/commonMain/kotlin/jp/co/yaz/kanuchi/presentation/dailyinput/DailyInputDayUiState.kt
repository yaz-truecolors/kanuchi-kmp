package jp.co.yaz.kanuchi.presentation.dailyinput

import jp.co.yaz.kanuchi.domain.work.AllocationHoursViolation
import jp.co.yaz.kanuchi.domain.work.WorkDayEntry
import jp.co.yaz.kanuchi.domain.work.WorkRecordField
import jp.co.yaz.kanuchi.domain.work.WorkRecordInput
import jp.co.yaz.kanuchi.domain.work.WorkRecordPreview
import jp.co.yaz.kanuchi.domain.work.WorkRecordViolation
import kotlinx.datetime.LocalDate

/**
 * 日次入力 (1日分の入力) 画面のUI状態。
 *
 * @property date 入力する日
 * @property entry 入力に必要な情報 (日の種類・保存済みの記録・勤務時間設定・配分できる案件)。読み込み前・失敗時は null
 * @property input 入力欄の内容
 * @property violations 保存しようとして見つかった、入力が満たしていない条件
 * @property allocationViolations 保存しようとして見つかった、案件ごとの工数の入力の誤り (案件ID → 誤り)
 * @property showsDeleteConfirmation 「この日の入力を消す」の確認ダイアログを表示しているか
 * @property isFinished 保存・削除が終わったか (true になったら一覧へ戻る)
 */
data class DailyInputDayUiState(
    val date: LocalDate,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val entry: WorkDayEntry? = null,
    val input: WorkRecordInput = WorkRecordInput(),
    val violations: Set<WorkRecordViolation> = emptySet(),
    val allocationViolations: Map<String, AllocationHoursViolation> = emptyMap(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val showsDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val isFinished: Boolean = false,
) {
    val showsForm: Boolean get() = entry != null

    /** 保存・削除の最中か (その間は入力・操作を受け付けない)。 */
    val isBusy: Boolean get() = isSaving || isDeleting || isFinished

    /** 保存済みの記録があるか (「この日の入力を消す」を表示する)。 */
    val hasSavedRecord: Boolean get() = entry?.day?.record != null

    /** 入力中の再計算 (稼働時間・配分の合計・過不足)。 */
    val preview: WorkRecordPreview?
        get() = entry?.let { input.preview(it.day.dayKind, it.shiftSettings) }

    fun violationOf(field: WorkRecordField): WorkRecordViolation? = violations.firstOrNull { it.field == field }
}
